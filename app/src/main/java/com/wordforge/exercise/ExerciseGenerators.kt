package com.wordforge.exercise

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ExerciseGenerator {
    suspend fun generate(request: ExerciseSessionRequest): ExercisePack
}

object ExerciseGeneratorFactory {
    fun create(
        provider: LlmProvider,
        apiKey: String,
        model: String = provider.defaultModel,
    ): ExerciseGenerator = when (provider) {
        LlmProvider.OPENAI -> OpenAiExerciseGenerator(apiKey = apiKey, model = model)
        LlmProvider.GEMINI -> GeminiExerciseGenerator(apiKey = apiKey, model = model)
    }
}

class OpenAiExerciseGenerator internal constructor(
    apiKey: String,
    model: String = LlmProvider.OPENAI.defaultModel,
    connectionOpener: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : BaseHttpExerciseGenerator(apiKey, model, connectionOpener) {
    override val endpoint: URL = URL("https://api.openai.com/v1/responses")

    override fun headers(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
    )

    override fun requestBody(request: ExerciseSessionRequest): JSONObject {
        val prompt = ExercisePromptBuilder.build(request)
        return JSONObject()
            .put("model", model)
            .put("store", false)
            .put("instructions", prompt.instructions)
            .put("input", prompt.input)
            .put(
                "text",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", "wordforge_exercise_pack")
                        .put("strict", true)
                        .put("schema", ExerciseJsonSchema.create(request.config.exerciseCount)),
                ),
            )
    }

    override fun extractExerciseJson(responseBody: String): String {
        try {
            val response = JSONObject(responseBody)
            val direct = response.opt("output_text")
            if (direct is String && direct.isNotBlank()) return direct

            val output = response.optJSONArray("output") ?: invalidProviderResponse()
            for (outputIndex in 0 until output.length()) {
                val item = output.optJSONObject(outputIndex) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (contentIndex in 0 until content.length()) {
                    val part = content.optJSONObject(contentIndex) ?: continue
                    if (part.optString("type") == "output_text") {
                        val text = part.opt("text")
                        if (text is String && text.isNotBlank()) return text
                    }
                }
            }
            invalidProviderResponse()
        } catch (_: JSONException) {
            invalidProviderResponse()
        }
    }
}

class GeminiExerciseGenerator internal constructor(
    apiKey: String,
    model: String = LlmProvider.GEMINI.defaultModel,
    connectionOpener: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : BaseHttpExerciseGenerator(apiKey, model, connectionOpener) {
    override val endpoint: URL = URL(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    )

    override fun headers(): Map<String, String> = mapOf(
        "x-goog-api-key" to apiKey,
    )

    override fun requestBody(request: ExerciseSessionRequest): JSONObject {
        val prompt = ExercisePromptBuilder.build(request)
        return JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt.instructions)),
                ),
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt.input)),
                        ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put(
                        "responseJsonSchema",
                        ExerciseJsonSchema.create(request.config.exerciseCount),
                    ),
            )
    }

    override fun extractExerciseJson(responseBody: String): String {
        try {
            val candidates = JSONObject(responseBody).optJSONArray("candidates")
                ?: invalidProviderResponse()
            if (candidates.length() == 0) invalidProviderResponse()
            val parts = candidates
                .optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: invalidProviderResponse()
            val text = buildString {
                for (index in 0 until parts.length()) {
                    val value = parts.optJSONObject(index)?.opt("text")
                    if (value is String) append(value)
                }
            }
            if (text.isBlank()) invalidProviderResponse()
            return text
        } catch (_: JSONException) {
            invalidProviderResponse()
        }
    }
}

abstract class BaseHttpExerciseGenerator internal constructor(
    protected val apiKey: String,
    protected val model: String,
    private val connectionOpener: (URL) -> HttpURLConnection,
) : ExerciseGenerator {
    protected abstract val endpoint: URL
    protected abstract fun headers(): Map<String, String>
    protected abstract fun requestBody(request: ExerciseSessionRequest): JSONObject
    protected abstract fun extractExerciseJson(responseBody: String): String

    init {
        if (apiKey.isBlank() || apiKey.length > MAX_API_KEY_LENGTH || apiKey.any(Char::isISOControl)) {
            throw ExerciseGenerationException.InvalidConfiguration("A valid API key is required")
        }
        if (!MODEL_NAME.matches(model)) {
            throw ExerciseGenerationException.InvalidConfiguration("The model name is invalid")
        }
    }

    final override suspend fun generate(request: ExerciseSessionRequest): ExercisePack {
        val responseBody = execute(requestBody(request).toString())
        val exerciseJson = extractExerciseJson(responseBody)
        return ExercisePackParser.parseAndValidate(exerciseJson, request)
    }

    private suspend fun execute(body: String): String = withContext(Dispatchers.IO) {
        val requestBytes = body.toByteArray(StandardCharsets.UTF_8)
        if (requestBytes.size > MAX_REQUEST_BYTES) {
            throw ExerciseGenerationException.InvalidConfiguration(
                "The exercise request is too large"
            )
        }

        suspendCancellableCoroutine { continuation ->
            val activeConnection = AtomicReference<HttpURLConnection?>()
            continuation.invokeOnCancellation {
                activeConnection.getAndSet(null)?.disconnect()
            }
            if (!continuation.isActive) return@suspendCancellableCoroutine

            try {
                val connection = connectionOpener(endpoint).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    useCaches = false
                    instanceFollowRedirects = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    headers().forEach { (name, value) -> setRequestProperty(name, value) }
                    setFixedLengthStreamingMode(requestBytes.size)
                }
                activeConnection.set(connection)
                if (!continuation.isActive) return@suspendCancellableCoroutine

                connection.outputStream.use { output -> output.write(requestBytes) }

                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    // Deliberately do not read or expose provider error bodies: they
                    // may echo request content or credential details.
                    throw when (statusCode) {
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        HttpURLConnection.HTTP_FORBIDDEN,
                        -> ExerciseGenerationException.Authentication()

                        429 -> ExerciseGenerationException.RateLimited()
                        else -> ExerciseGenerationException.ProviderUnavailable(statusCode)
                    }
                }

                val announcedLength = connection.contentLengthLong
                if (announcedLength > MAX_RESPONSE_BYTES) {
                    throw ExerciseGenerationException.InvalidResponse(
                        "The provider response was too large"
                    )
                }
                val response = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_RESPONSE_BYTES) {
                            throw ExerciseGenerationException.InvalidResponse(
                                "The provider response was too large"
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toString(StandardCharsets.UTF_8.name())
                }
                if (continuation.isActive) continuation.resume(response)
            } catch (error: ExerciseGenerationException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } catch (_: SocketTimeoutException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(ExerciseGenerationException.Timeout())
                }
            } catch (_: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(ExerciseGenerationException.Network())
                }
            } catch (_: RuntimeException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(ExerciseGenerationException.Network())
                }
            } finally {
                activeConnection.getAndSet(null)?.disconnect()
            }
        }
    }

    protected fun invalidProviderResponse(): Nothing =
        throw ExerciseGenerationException.InvalidResponse(
            "The provider returned an invalid response"
        )

    private companion object {
        val MODEL_NAME = Regex("^[A-Za-z0-9._-]{1,100}$")
        const val MAX_API_KEY_LENGTH = 4_096
        const val MAX_REQUEST_BYTES = 512 * 1024
        const val MAX_RESPONSE_BYTES = 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
