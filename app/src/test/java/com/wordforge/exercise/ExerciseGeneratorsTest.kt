package com.wordforge.exercise

import com.wordforge.data.LearningItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExerciseGeneratorsTest {

    @Test
    fun openAiSendsStatelessStructuredRequestAndExtractsOutputText() = runBlocking {
        val connection = RecordingHttpURLConnection(
            url = URL("https://unused.test"),
            responseBody = openAiResponse(validExercisePackJson()),
        )
        var openedUrl: URL? = null
        val generator = OpenAiExerciseGenerator(
            apiKey = FAKE_SECRET,
            model = "gpt-test",
            connectionOpener = { url ->
                openedUrl = url
                connection
            },
        )

        val pack = generator.generate(minimalRequest())

        assertEquals("https://api.openai.com/v1/responses", openedUrl.toString())
        assertEquals("POST", connection.requestMethod)
        assertEquals("Bearer $FAKE_SECRET", connection.requestHeaders["Authorization"])
        assertEquals("application/json; charset=utf-8", connection.requestHeaders["Content-Type"])
        assertEquals("application/json", connection.requestHeaders["Accept"])

        val body = JSONObject(connection.writtenBody())
        assertEquals("gpt-test", body.getString("model"))
        assertFalse(body.getBoolean("store"))
        assertTrue(body.getString("instructions").isNotBlank())
        assertTrue(body.getString("input").isNotBlank())

        val format = body.getJSONObject("text").getJSONObject("format")
        assertEquals("json_schema", format.getString("type"))
        assertEquals("wordforge_exercise_pack", format.getString("name"))
        assertTrue(format.getBoolean("strict"))
        assertExerciseSchema(format.getJSONObject("schema"))

        assertEquals("Generator contract", pack.title)
        assertEquals(3, pack.exercises.size)
        assertTrue(connection.wasDisconnected)
    }

    @Test
    fun geminiSendsJsonSchemaRequestAndExtractsCandidateParts() = runBlocking {
        val exerciseJson = validExercisePackJson()
        val connection = RecordingHttpURLConnection(
            url = URL("https://unused.test"),
            responseBody = geminiResponse(exerciseJson),
        )
        var openedUrl: URL? = null
        val generator = GeminiExerciseGenerator(
            apiKey = FAKE_SECRET,
            model = "gemini-test",
            connectionOpener = { url ->
                openedUrl = url
                connection
            },
        )

        val pack = generator.generate(minimalRequest())

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
            openedUrl.toString(),
        )
        assertEquals("POST", connection.requestMethod)
        assertEquals(FAKE_SECRET, connection.requestHeaders["x-goog-api-key"])
        assertFalse(connection.requestHeaders.containsKey("Authorization"))

        val body = JSONObject(connection.writtenBody())
        assertTrue(
            body.getJSONObject("systemInstruction")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .isNotBlank()
        )
        assertEquals(
            "user",
            body.getJSONArray("contents").getJSONObject(0).getString("role"),
        )
        val generationConfig = body.getJSONObject("generationConfig")
        assertEquals("application/json", generationConfig.getString("responseMimeType"))
        assertExerciseSchema(generationConfig.getJSONObject("responseJsonSchema"))

        assertEquals("Generator contract", pack.title)
        assertEquals(listOf("exercise-1", "exercise-2", "exercise-3"), pack.exercises.map { it.id })
        assertTrue(connection.wasDisconnected)
    }

    @Test
    fun unauthorizedResponseMapsToAuthenticationWithoutLeakingSecret() {
        val generator = openAiGenerator(
            RecordingHttpURLConnection(
                url = URL("https://unused.test"),
                statusCode = HttpURLConnection.HTTP_UNAUTHORIZED,
                responseBody = "provider echoed $FAKE_SECRET",
            )
        )

        val error = assertThrows(ExerciseGenerationException.Authentication::class.java) {
            runBlocking { generator.generate(minimalRequest()) }
        }

        assertSecretSafe(error)
    }

    @Test
    fun tooManyRequestsMapsToRateLimitedWithoutLeakingSecret() {
        val generator = openAiGenerator(
            RecordingHttpURLConnection(
                url = URL("https://unused.test"),
                statusCode = 429,
                responseBody = "provider echoed $FAKE_SECRET",
            )
        )

        val error = assertThrows(ExerciseGenerationException.RateLimited::class.java) {
            runBlocking { generator.generate(minimalRequest()) }
        }

        assertSecretSafe(error)
    }

    @Test
    fun oversizedStreamingResponseIsRejectedWithoutLeakingSecret() {
        val oversizedBody = ByteArray(1024 * 1024 + 1) { 'x'.code.toByte() }
        val generator = openAiGenerator(
            RecordingHttpURLConnection(
                url = URL("https://unused.test"),
                responseBytes = oversizedBody,
                announcedLength = -1L,
            )
        )

        val error = assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            runBlocking { generator.generate(minimalRequest()) }
        }

        assertEquals("The provider response was too large", error.message)
        assertSecretSafe(error)
    }

    @Test
    fun transportExceptionMessageCannotLeakSecret() {
        val generator = openAiGenerator(
            RecordingHttpURLConnection(
                url = URL("https://unused.test"),
                responseFailure = IOException("connection failed with $FAKE_SECRET"),
            )
        )

        val error = assertThrows(ExerciseGenerationException.Network::class.java) {
            runBlocking { generator.generate(minimalRequest()) }
        }

        assertSecretSafe(error)
    }

    @Test
    fun cancellationDisconnectsTheActiveProviderRequest() = runBlocking {
        val connection = CancellableHttpURLConnection(URL("https://unused.test"))
        val generator = OpenAiExerciseGenerator(
            apiKey = FAKE_SECRET,
            model = "gpt-test",
            connectionOpener = { connection },
        )

        val job = launch(Dispatchers.Default) { generator.generate(minimalRequest()) }
        assertTrue(connection.readStarted.await(2, TimeUnit.SECONDS))

        job.cancelAndJoin()

        assertTrue(connection.wasDisconnected)
    }

    private fun openAiGenerator(
        connection: RecordingHttpURLConnection,
    ): OpenAiExerciseGenerator = OpenAiExerciseGenerator(
        apiKey = FAKE_SECRET,
        model = "gpt-test",
        connectionOpener = { connection },
    )

    private fun assertExerciseSchema(schema: JSONObject) {
        assertEquals("object", schema.getString("type"))
        assertFalse(schema.getBoolean("additionalProperties"))
        assertEquals(
            setOf("title", "exercises"),
            schema.getJSONArray("required").toStringSet(),
        )

        val exercises = schema
            .getJSONObject("properties")
            .getJSONObject("exercises")
        assertEquals("array", exercises.getString("type"))
        assertEquals(3, exercises.getInt("minItems"))
        assertEquals(3, exercises.getInt("maxItems"))
        assertFalse(exercises.getJSONObject("items").getBoolean("additionalProperties"))
    }

    private fun assertSecretSafe(error: Throwable) {
        assertFalse(error.message.orEmpty().contains(FAKE_SECRET))
        assertFalse(error.cause?.message.orEmpty().contains(FAKE_SECRET))
    }

    private fun minimalRequest(): ExerciseSessionRequest = ExerciseSessionRequest(
        config = ExerciseConfig(
            exerciseCount = 3,
            selectedTypes = setOf(ExerciseType.TRANSLATION),
            coveragePercent = 100,
        ),
        selectedWords = listOf(
            SelectedExerciseWord(
                ref = "word-1",
                sourceWordId = "local-database-id",
                itemType = LearningItemType.SIMPLE_WORD,
                term = "hola",
                meaning = "hello",
                tense = "",
                conjugations = emptyList(),
            )
        ),
    )

    private fun validExercisePackJson(): String = JSONObject()
        .put("title", "Generator contract")
        .put(
            "exercises",
            JSONArray().apply {
                repeat(3) { index ->
                    put(
                        JSONObject()
                            .put("id", "exercise-${index + 1}")
                            .put("type", ExerciseType.TRANSLATION.name)
                            .put("instructions", "Translate into English")
                            .put("prompt", "Translate hola into English")
                            .put("options", JSONArray())
                            .put("correctOptionIndex", -1)
                            .put("acceptedAnswers", JSONArray().put("hello"))
                            .put("pairs", JSONArray())
                            .put("wordRefs", JSONArray().put("word-1"))
                            .put("explanation", "hola means hello")
                    )
                }
            },
        )
        .toString()

    private fun openAiResponse(exerciseJson: String): String = JSONObject()
        .put("output_text", exerciseJson)
        .toString()

    private fun geminiResponse(exerciseJson: String): String {
        val midpoint = exerciseJson.length / 2
        return JSONObject()
            .put(
                "candidates",
                JSONArray().put(
                    JSONObject().put(
                        "content",
                        JSONObject().put(
                            "parts",
                            JSONArray()
                                .put(JSONObject().put("text", exerciseJson.substring(0, midpoint)))
                                .put(JSONObject().put("text", exerciseJson.substring(midpoint))),
                        ),
                    )
                ),
            )
            .toString()
    }

    private fun JSONArray.toStringSet(): Set<String> = buildSet {
        for (index in 0 until length()) add(getString(index))
    }

    private class RecordingHttpURLConnection(
        url: URL,
        private val statusCode: Int = HttpURLConnection.HTTP_OK,
        responseBody: String = "",
        responseBytes: ByteArray = responseBody.toByteArray(StandardCharsets.UTF_8),
        private val announcedLength: Long = responseBytes.size.toLong(),
        private val responseFailure: IOException? = null,
    ) : HttpURLConnection(url) {
        val requestHeaders = linkedMapOf<String, String>()
        var wasDisconnected = false
            private set

        private val requestBytes = ByteArrayOutputStream()
        private val responseBytes = responseBytes.copyOf()

        override fun connect() = Unit

        override fun disconnect() {
            wasDisconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }

        override fun getRequestProperty(key: String): String? = requestHeaders[key]

        override fun getOutputStream(): OutputStream = requestBytes

        override fun getResponseCode(): Int {
            responseFailure?.let { throw it }
            return statusCode
        }

        override fun getContentLengthLong(): Long = announcedLength

        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBytes)

        fun writtenBody(): String = requestBytes.toString(StandardCharsets.UTF_8.name())
    }

    private class CancellableHttpURLConnection(url: URL) : HttpURLConnection(url) {
        val readStarted = CountDownLatch(1)
        private val disconnected = CountDownLatch(1)
        private val requestBytes = ByteArrayOutputStream()

        @Volatile
        var wasDisconnected = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            wasDisconnected = true
            disconnected.countDown()
        }

        override fun usingProxy(): Boolean = false
        override fun getOutputStream(): OutputStream = requestBytes
        override fun getResponseCode(): Int = HTTP_OK
        override fun getContentLengthLong(): Long = -1

        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int {
                readStarted.countDown()
                disconnected.await(2, TimeUnit.SECONDS)
                throw IOException("disconnected")
            }
        }
    }

    private companion object {
        const val FAKE_SECRET = "sk-test-never-leak-123456789"
    }
}
