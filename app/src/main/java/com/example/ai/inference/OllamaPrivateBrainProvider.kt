package com.example.ai.inference

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Real native Ollama Private Brain provider.
 * Interacts directly with Ollama's /api/tags and /api/chat endpoints.
 */
class OllamaPrivateBrainProvider(
    private val config: PrivateBrainConfig,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(config.readTimeoutSeconds + config.connectTimeoutSeconds, TimeUnit.SECONDS)
        .build()
) : PrivateBrainProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun resolveUrl(path: String): String {
        val base = config.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) {
            throw PrivateBrainNotConfiguredException("Private Brain base URL is not configured.")
        }
        val cleanPath = path.trim().trimStart('/')
        return "$base/$cleanPath"
    }

    private fun applyHeaders(builder: Request.Builder): Request.Builder {
        builder.header("Content-Type", "application/json")
        val key = config.apiKey?.trim()
        if (!key.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $key")
        }
        return builder
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trim()
        if (base.isEmpty()) return@withContext false

        try {
            val url = resolveUrl("api/tags")
            val request = applyHeaders(Request.Builder().url(url).get()).build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 401
            }
        } catch (e: Exception) {
            safeLog("Ollama availability probe failed: ${e.javaClass.simpleName} - ${e.message}")
            false
        }
    }

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val url = try {
            resolveUrl("api/tags")
        } catch (e: Exception) {
            throw PrivateBrainException("Invalid Private Brain endpoint configuration: ${e.message}", e)
        }

        val request = applyHeaders(Request.Builder().url(url).get()).build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    handleHttpError(response.code, errorBody, "tags")
                }

                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    throw PrivateBrainResponseException("Ollama returned an empty response for tags list.")
                }

                try {
                    val root = JSONObject(bodyStr)
                    val modelsArray = root.optJSONArray("models")
                        ?: throw PrivateBrainResponseException("Expected 'models' array in Ollama /api/tags response.")

                    val result = mutableListOf<String>()
                    for (i in 0 until modelsArray.length()) {
                        val item = modelsArray.optJSONObject(i)
                        val name = item?.optString("name")?.ifEmpty { item.optString("model") }?.trim()
                        if (!name.isNullOrEmpty()) {
                            result.add(name)
                        }
                    }
                    result
                } catch (e: JSONException) {
                    throw PrivateBrainResponseException("Malformed JSON in Ollama /api/tags response: ${e.message}", e)
                }
            }
        } catch (e: SocketTimeoutException) {
            throw PrivateBrainTimeoutException("Connection timed out querying models from Ollama.", e)
        } catch (e: IOException) {
            throw PrivateBrainUnavailableException("Unable to reach Ollama server at ${sanitizeUrl(config.baseUrl)}: ${e.message}", e)
        }
    }

    override suspend fun generate(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        if (model.isBlank()) {
            throw PrivateBrainException("Model name must not be blank.")
        }
        val url = resolveUrl("api/chat")
        val payload = buildOllamaChatPayload(messages, model, temperature, maxTokens, stream = false)

        val request = applyHeaders(
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
        ).build()

        safeLog("Sending completion request to Ollama model: $model at ${sanitizeUrl(url)}")

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    handleHttpError(response.code, bodyStr, model)
                }

                if (bodyStr.isBlank()) {
                    throw PrivateBrainResponseException("Ollama returned an empty response.")
                }

                try {
                    val root = JSONObject(bodyStr)
                    val messageObj = root.optJSONObject("message")
                    val content = messageObj?.optString("content")

                    if (content.isNullOrBlank()) {
                        throw PrivateBrainResponseException("Ollama returned empty message content.")
                    }
                    content
                } catch (e: JSONException) {
                    throw PrivateBrainResponseException("Malformed JSON response from Ollama: ${e.message}", e)
                }
            }
        } catch (e: SocketTimeoutException) {
            throw PrivateBrainTimeoutException("Request timed out waiting for Ollama response.", e)
        } catch (e: IOException) {
            throw PrivateBrainUnavailableException("Communication error with Ollama at ${sanitizeUrl(config.baseUrl)}: ${e.message}", e)
        }
    }

    override fun generateStream(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        if (model.isBlank()) {
            throw PrivateBrainException("Model name must not be blank.")
        }
        val url = resolveUrl("api/chat")
        val payload = buildOllamaChatPayload(messages, model, temperature, maxTokens, stream = true)

        val request = applyHeaders(
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
        ).build()

        safeLog("Streaming completion from Ollama model: $model at ${sanitizeUrl(url)}")

        var emittedAnyToken = false

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                response.close()
                handleHttpError(response.code, errorBody, model)
            }

            val body = response.body ?: throw PrivateBrainResponseException("Empty response body from Ollama stream.")
            val source = body.source()

            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue

                    try {
                        val json = JSONObject(line)
                        val messageObj = json.optJSONObject("message")
                        val textChunk = messageObj?.optString("content")
                        if (!textChunk.isNullOrEmpty()) {
                            emit(textChunk)
                            emittedAnyToken = true
                        }
                        if (json.optBoolean("done", false)) {
                            break
                        }
                    } catch (je: JSONException) {
                        // Non-fatal line parse error, continue reading
                    }
                }
            } finally {
                response.close()
            }

            if (!emittedAnyToken) {
                throw PrivateBrainResponseException("Ollama stream completed without emitting any tokens.")
            }
        } catch (e: SocketTimeoutException) {
            throw PrivateBrainTimeoutException("Stream connection timed out on Ollama.", e)
        } catch (e: IOException) {
            throw PrivateBrainUnavailableException("Network error during streaming from Ollama: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildOllamaChatPayload(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        maxTokens: Int,
        stream: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("stream", stream)

            val options = JSONObject().apply {
                put("temperature", temperature.toDouble())
                put("num_predict", maxTokens)
            }
            put("options", options)

            val messagesArray = JSONArray()
            for (msg in messages) {
                val role = when (msg.sender.lowercase()) {
                    "user" -> "user"
                    "system" -> "system"
                    "assistant", "diana", "bot" -> "assistant"
                    else -> "user"
                }
                messagesArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.text)
                })
            }
            put("messages", messagesArray)
        }
    }

    private fun handleHttpError(code: Int, errorBody: String, target: String): Nothing {
        safeLog("HTTP $code from Ollama endpoint: ${sanitizeErrorBody(errorBody)}")
        when (code) {
            401, 403 -> throw PrivateBrainAuthException("Authentication failed on Ollama (HTTP $code): Check API key or proxy auth.")
            404 -> throw PrivateBrainModelNotFoundException("Model '$target' not found in Ollama tags (HTTP 404): $errorBody")
            in 400..499 -> throw PrivateBrainException("Ollama request rejected (HTTP $code): $errorBody")
            in 500..599 -> throw PrivateBrainUnavailableException("Ollama server error (HTTP $code): $errorBody")
            else -> throw PrivateBrainException("Unexpected HTTP $code response from Ollama: $errorBody")
        }
    }

    private fun sanitizeUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}:${if (uri.port != -1) uri.port else ""}${uri.path}"
        } catch (e: Exception) {
            "[Configured Ollama Endpoint]"
        }
    }

    private fun sanitizeErrorBody(errorBody: String): String {
        return if (errorBody.length > 200) errorBody.take(200) + "..." else errorBody
    }

    private fun safeLog(msg: String) {
        Log.d("PrivateBrainOllama", msg)
    }
}
