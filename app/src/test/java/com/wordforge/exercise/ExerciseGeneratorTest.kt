package com.wordforge.exercise

import com.wordforge.data.LearningItemType
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
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ExerciseGeneratorTest {
    @Test
    fun openAiUsesResponsesStructuredOutputAndBearerHeader() = runBlocking {
        val providerJson = JSONObject()
            .put(
                "output",
                JSONArray().put(
                    JSONObject().put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "output_text")
                                .put("text", validPackJson()),
                        ),
                    )
                ),
            )
            .toString()
        val connection = FakeConnection(URL("https://unused.test"), 200, providerJson)
        val generator = OpenAiExerciseGenerator(
            apiKey = "openai-secret",
            connectionOpener = { connection },
        )

        val pack = generator.generate(request())
        val body = JSONObject(connection.requestBody())

        assertEquals(3, pack.exercises.size)
        assertEquals("Bearer openai-secret", connection.headers["Authorization"])
        assertFalse(body.getBoolean("store"))
        val format = body.getJSONObject("text").getJSONObject("format")
        assertEquals("json_schema", format.getString("type"))
        assertTrue(format.getBoolean("strict"))
        assertEquals(3, format.getJSONObject("schema")
            .getJSONObject("properties")
            .getJSONObject("exercises")
            .getInt("minItems"))
    }

    @Test
    fun geminiUsesGenerateContentJsonSchemaAndKeyHeader() = runBlocking {
        val providerJson = JSONObject()
            .put(
                "candidates",
                JSONArray().put(
                    JSONObject().put(
                        "content",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", validPackJson())),
                        ),
                    )
                ),
            )
            .toString()
        var openedUrl: URL? = null
        val connection = FakeConnection(URL("https://unused.test"), 200, providerJson)
        val generator = GeminiExerciseGenerator(
            apiKey = "gemini-secret",
            model = "gemini-2.5-flash",
            connectionOpener = { url ->
                openedUrl = url
                connection
            },
        )

        generator.generate(request())
        val body = JSONObject(connection.requestBody())
        val generationConfig = body.getJSONObject("generationConfig")

        assertTrue(openedUrl.toString().endsWith(
            "/v1beta/models/gemini-2.5-flash:generateContent"
        ))
        assertEquals("gemini-secret", connection.headers["x-goog-api-key"])
        assertEquals("application/json", generationConfig.getString("responseMimeType"))
        assertTrue(generationConfig.has("responseJsonSchema"))
    }

    @Test
    fun invalidModelIsRejectedBeforeOpeningAConnection() {
        var opened = false
        assertThrows(ExerciseGenerationException.InvalidConfiguration::class.java) {
            GeminiExerciseGenerator(
                apiKey = "secret",
                model = "../../unsafe/model",
                connectionOpener = {
                    opened = true
                    FakeConnection(it, 200, "{}")
                },
            )
        }
        assertFalse(opened)
    }

    @Test
    fun authenticationErrorNeverReadsOrEchoesProviderBody() {
        val connection = FakeConnection(
            URL("https://unused.test"),
            401,
            "provider echoed openai-secret and vocabulary",
        )
        val generator = OpenAiExerciseGenerator(
            apiKey = "openai-secret",
            connectionOpener = { connection },
        )

        val error = assertThrows(ExerciseGenerationException.Authentication::class.java) {
            runBlocking { generator.generate(request()) }
        }

        assertFalse(connection.inputOpened)
        assertFalse(error.message.orEmpty().contains("openai-secret"))
        assertFalse(error.message.orEmpty().contains("vocabulary"))
    }

    @Test
    fun announcedOversizedResponseIsRejectedBeforeReading() {
        val connection = FakeConnection(
            URL("https://unused.test"),
            200,
            "{}",
            announcedLength = 1024L * 1024L + 1,
        )
        val generator = OpenAiExerciseGenerator(
            apiKey = "secret",
            connectionOpener = { connection },
        )

        assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            runBlocking { generator.generate(request()) }
        }
        assertFalse(connection.inputOpened)
    }

    private fun request(): ExerciseSessionRequest {
        val config = ExerciseConfig(
            exerciseCount = 3,
            selectedTypes = setOf(ExerciseType.TRANSLATION),
            coveragePercent = 100,
        )
        return ExerciseSessionRequest(
            config = config,
            selectedWords = listOf(
                SelectedExerciseWord(
                    ref = "w1",
                    sourceWordId = "private-id",
                    itemType = LearningItemType.SIMPLE_WORD,
                    term = "casa",
                    meaning = "house",
                    tense = "",
                    conjugations = emptyList(),
                )
            ),
        )
    }

    private fun validPackJson(): String = JSONObject()
        .put("title", "Translations")
        .put(
            "exercises",
            JSONArray().apply {
                repeat(3) { index ->
                    put(
                        JSONObject()
                            .put("id", "e${index + 1}")
                            .put("type", ExerciseType.TRANSLATION.name)
                            .put("instructions", "Translate the word.")
                            .put("prompt", "Translate casa.")
                            .put("options", JSONArray())
                            .put("correctOptionIndex", -1)
                            .put("acceptedAnswers", JSONArray(listOf("house")))
                            .put("pairs", JSONArray())
                            .put("wordRefs", JSONArray(listOf("w1")))
                            .put("explanation", "Casa means house.")
                    )
                }
            },
        )
        .toString()

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val responseBody: String,
        private val announcedLength: Long = responseBody
            .toByteArray(StandardCharsets.UTF_8)
            .size
            .toLong(),
    ) : HttpURLConnection(url) {
        private val request = ByteArrayOutputStream()
        val headers = linkedMapOf<String, String>()
        var inputOpened = false
            private set

        fun requestBody(): String = request.toString(StandardCharsets.UTF_8.name())

        override fun setRequestProperty(key: String, value: String) {
            headers[key] = value
        }

        override fun getOutputStream(): OutputStream = request

        override fun getResponseCode(): Int = status

        override fun getContentLengthLong(): Long = announcedLength

        override fun getInputStream(): InputStream {
            inputOpened = true
            return ByteArrayInputStream(responseBody.toByteArray(StandardCharsets.UTF_8))
        }

        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }
}
