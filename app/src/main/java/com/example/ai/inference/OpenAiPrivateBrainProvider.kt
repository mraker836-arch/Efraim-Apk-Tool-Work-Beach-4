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
 * Real OpenAI-compatible Private Brain provider.
 * Supports private self-hosted servers such as vLLM, llama.cpp server, LocalAI,
 * LiteLLM, Ollama (/v1), or LM Studio.
 */
class OpenAiPrivateBrainProvider(
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
        return if (base.endsWith("/v1") && cleanPath.startsWith("v1/")) {
            base + "/" + cleanPath.removePrefix("v1/").trimStart('/')
        } else if (!base.endsWith("/v1") && !cleanPath.startsWith("v1/")) {
            // If base has no /v1 and path has no /v1, add /v1 for OpenAI compliance
            "$base/v1/$cleanPath"
        } else {
            "$base/$cleanPath"
        }
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
            val url = resolveUrl("models")
            val request = applyHeaders(Request.Builder().url(url).get()).build()
            httpClient.newCall(request).execute().use { response ->
                // HTTP 2xx or 401 (server exists, needs auth) implies the host is active
                response.isSuccessful || response.code == 401
            }
        } catch (e: Exception) {
            safeLog("Availability check failed: ${e.javaClass.simpleName} - ${e.message}")
            false
        }
    }

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val url = try {
            resolveUrl("models")
        } catch (e: Exception) {
            throw PrivateBrainException("Invalid Private Brain endpoint configuration: ${e.message}", e)
        }

        val request = applyHeaders(Request.Builder().url(url).get()).build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    handleHttpError(response.code, errorBody, "models")
                }

                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    throw PrivateBrainResponseException("Private Brain returned an empty response for models list.")
                }

                try {
                    val root = JSONObject(bodyStr)
                    val dataArray = root.optJSONArray("data")
                        ?: throw PrivateBrainResponseException("Expected 'data' array in /v1/models response.")

                    val result = mutableListOf<String>()
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.optJSONObject(i)
                        val id = item?.optString("id")?.trim()
                        if (!id.isNullOrEmpty()) {
                            result.add(id)
                        }
                    }
                    result
                } catch (e: JSONException) {
                    throw PrivateBrainResponseException("Malformed JSON in /v1/models response: ${e.message}", e)
                }
            }
        } catch (e: SocketTimeoutException) {
            throw PrivateBrainTimeoutException("Connection timed out while querying models from Private Brain.", e)
        } catch (e: IOException) {
            throw PrivateBrainUnavailableException("Unable to reach Private Brain server at ${sanitizeUrl(config.baseUrl)}: ${e.message}", e)
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
        val url = resolveUrl("chat/completions")
        val payload = buildChatCompletionPayload(messages, model, temperature, maxTokens, stream = false)

        val request = applyHeaders(
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
        ).build()

        safeLog("Sending completion request to model: $model at ${sanitizeUrl(url)}")

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    handleHttpError(response.code, bodyStr, model)
                }

                if (bodyStr.isBlank()) {
                    throw PrivateBrainResponseException("Private Brain returned an empty response.")
                }

                try {
                    val root = JSONObject(bodyStr)
                    val choices = root.optJSONArray("choices")
                    if (choices == null || choices.length() == 0) {
                        throw PrivateBrainResponseException("No 'choices' returned in model response.")
                    }

                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.optJSONObject("message")
                    val content = messageObj?.optString("content")

                    if (content.isNullOrBlank()) {
                        throw PrivateBrainResponseException("Model returned empty message content.")
                    }
                    content
                } catch (e: JSONException) {
                    throw PrivateBrainResponseException("Malformed JSON response from Private Brain: ${e.message}", e)
                }
            }
        } catch (e: SocketTimeoutException) {
            throw PrivateBrainTimeoutException("Request timed out waiting for Private Brain model response.", e)
        } catch (e: IOException) {
            throw PrivateBrainUnavailableException("Communication error with Private Brain at ${sanitizeUrl(config.baseUrl)}: ${e.message}", e)
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
        val url = resolveUrl("chat/completions")
        val payload = buildChatCompletionPayload(messages, model, temperature, maxTokens, stream = true)

        val request = applyHeaders(
            Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
        ).build()

        safeLog("Streaming completion from model: $model at ${sanitizeUrl(url)}")

        var emittedAnyToken = false

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                response.close()
                handleHttpError(response.code, errorBody, model)
            }

            val body = response.body ?: throw PrivateBrainResponseException("Empty response body from Private Brain stream.")
            val source = body.source()

            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || line.startsWith(":") || line.startsWith("event:")) continue

                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") {
                            break
                        }

                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val textChunk = delta?.optString("content")
                                if (!textChunk.isNullOrEmpty()) {
                                    emit(textChunk)
                                    emittedAnyToken = true
                                }
                            }
                        } catch (je: JSONException) {
                            // Non-fatal chunk parsing error, continue reading next SSE event
                        }
                    }
                }
            } finally {
                response.close()
            }

            if (!emittedAnyToken) {
                throw PrivateBrainResponseException("Private Brain stream completed without emitting any tokens.")
            }
        } catch (e: SocketTimeoutException) {
            throw PrivateBrainTimeoutException("Stream connection timed out on Private Brain.", e)
        } catch (e: IOException) {
            throw PrivateBrainUnavailableException("Network error during streaming from Private Brain: ${e.message}", e)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildChatCompletionPayload(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        maxTokens: Int,
        stream: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("temperature", temperature.toDouble())
            put("max_tokens", maxTokens)
            put("stream", stream)

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

    private fun handleHttpError(code: Int, errorBody: String, modelTarget: String): Nothing {
        safeLog("HTTP $code from Private Brain endpoint: ${sanitizeErrorBody(errorBody)}")
        when (code) {
            401, 403 -> throw PrivateBrainAuthException("Authentication failed (HTTP $code): Invalid or missing API key.")
            404 -> throw PrivateBrainModelNotFoundException("Model '$modelTarget' not found on server (HTTP 404): $errorBody")
            in 400..499 -> throw PrivateBrainException("Private Brain request rejected (HTTP $code): $errorBody")
            in 500..599 -> throw PrivateBrainUnavailableException("Private Brain internal server error (HTTP $code): $errorBody")
            else -> throw PrivateBrainException("Unexpected HTTP $code response from Private Brain: $errorBody")
        }
    }

    private fun sanitizeUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}:${if (uri.port != -1) uri.port else ""}${uri.path}"
        } catch (e: Exception) {
            "[Configured Private Endpoint]"
        }
    }

    private fun sanitizeErrorBody(errorBody: String): String {
        return if (errorBody.length > 200) errorBody.take(200) + "..." else errorBody
    }

    private fun safeLog(msg: String) {
        Log.d("PrivateBrainOpenAI", msg)
    }
}
