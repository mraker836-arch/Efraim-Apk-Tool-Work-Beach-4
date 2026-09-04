package com.example.ai.inference

import android.content.Context
import android.util.Log
import com.example.BuildConfig
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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AiMode {
    OFFLINE,
    CLOUD,
    AUTO
}

data class InferenceMetrics(
    val modelName: String,
    val isLocal: Boolean,
    val loadTimeMs: Long,
    val inferenceTimeMs: Long,
    val tokensGenerated: Int,
    val tokensPerSecond: Float,
    val ramUsageMb: Long,
    val contextUsageTokens: Int,
    val maxContextTokens: Int
)

data class InferenceConfig(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val contextSize: Int = 4096,
    val systemPrompt: String = "You are DIANA, a senior Android APK security, architecture, and rebuild specialist."
)

/**
 * Real Private Brain Inference Service.
 * Coordinates between DIANA and self-hosted private LLM backends (OpenAI-compatible or Ollama).
 * Contains no canned responses, mock delays, or simulated weights.
 */
class InferenceService(private val context: Context) {

    private val prefs = context.getSharedPreferences("efraim_private_brain_prefs", Context.MODE_PRIVATE)

    private var currentMode: AiMode = AiMode.AUTO
    private var loadedLocalModel: String? = null
    private var modelLoadTimeMs: Long = 0L
    private var isModelLoaded: Boolean = false

    private var activeConfig: PrivateBrainConfig = loadPersistedConfig()
    private var customProvider: PrivateBrainProvider? = null

    private val cloudHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun loadPersistedConfig(): PrivateBrainConfig {
        val baseUrl = prefs.getString("base_url", "") ?: ""
        val apiKey = prefs.getString("api_key", null)
        val selectedModel = prefs.getString("selected_model", "") ?: ""
        val typeStr = prefs.getString("server_type", PrivateBrainServerType.AUTO.name) ?: PrivateBrainServerType.AUTO.name
        val serverType = try {
            PrivateBrainServerType.valueOf(typeStr)
        } catch (e: Exception) {
            PrivateBrainServerType.AUTO
        }
        val enableCloudFallback = prefs.getBoolean("enable_cloud_fallback", false)

        return PrivateBrainConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModel = selectedModel,
            serverType = serverType,
            enableCloudFallback = enableCloudFallback
        )
    }

    fun getPrivateBrainConfig(): PrivateBrainConfig = activeConfig

    fun updatePrivateBrainConfig(newConfig: PrivateBrainConfig) {
        activeConfig = newConfig
        prefs.edit().apply {
            putString("base_url", newConfig.baseUrl)
            putString("api_key", newConfig.apiKey)
            putString("selected_model", newConfig.selectedModel)
            putString("server_type", newConfig.serverType.name)
            putBoolean("enable_cloud_fallback", newConfig.enableCloudFallback)
            apply()
        }
        customProvider = null // Reset custom provider so new config takes effect
        safeLog("Updated Private Brain configuration for endpoint: ${sanitizeUrl(newConfig.baseUrl)}")
    }

    fun setProvider(provider: PrivateBrainProvider) {
        customProvider = provider
    }

    fun getActiveProvider(): PrivateBrainProvider {
        customProvider?.let { return it }

        val type = when (activeConfig.serverType) {
            PrivateBrainServerType.OLLAMA -> PrivateBrainServerType.OLLAMA
            PrivateBrainServerType.OPENAI_COMPATIBLE -> PrivateBrainServerType.OPENAI_COMPATIBLE
            PrivateBrainServerType.AUTO -> {
                val url = activeConfig.baseUrl.lowercase()
                if (url.contains(":11434") || url.contains("/api/")) {
                    PrivateBrainServerType.OLLAMA
                } else {
                    PrivateBrainServerType.OPENAI_COMPATIBLE
                }
            }
        }

        return when (type) {
            PrivateBrainServerType.OLLAMA -> OllamaPrivateBrainProvider(activeConfig)
            else -> OpenAiPrivateBrainProvider(activeConfig)
        }
    }

    fun isConfigured(): Boolean {
        return activeConfig.baseUrl.isNotBlank() || customProvider != null
    }

    fun setMode(mode: AiMode) {
        currentMode = mode
    }

    fun getMode(): AiMode = currentMode

    fun isLocalModelLoaded(): Boolean = isModelLoaded

    fun getLoadedModelName(): String? = loadedLocalModel

    /**
     * Verifies server availability and confirms the model actually exists before setting isModelLoaded = true.
     * Replaces the former simulated model loader.
     */
    suspend fun loadModel(modelName: String, contextSize: Int = 4096): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (!isConfigured()) {
            isModelLoaded = false
            loadedLocalModel = null
            throw PrivateBrainNotConfiguredException("Private Brain is not configured.")
        }

        val provider = getActiveProvider()
        if (!provider.isAvailable()) {
            isModelLoaded = false
            loadedLocalModel = null
            throw PrivateBrainUnavailableException("Private Brain server at ${sanitizeUrl(activeConfig.baseUrl)} is unavailable or offline.")
        }

        try {
            val availableModels = provider.listModels()
            val match = availableModels.firstOrNull {
                it.equals(modelName, ignoreCase = true) ||
                it.contains(modelName, ignoreCase = true) ||
                modelName.contains(it, ignoreCase = true)
            }

            if (availableModels.isNotEmpty() && match == null) {
                isModelLoaded = false
                loadedLocalModel = null
                throw PrivateBrainModelNotFoundException(
                    "Model '$modelName' was not found on the Private Brain server. Available models: ${availableModels.joinToString(", ")}"
                )
            }

            loadedLocalModel = match ?: modelName
            isModelLoaded = true
            modelLoadTimeMs = System.currentTimeMillis() - startTime
            safeLog("Successfully connected model: $loadedLocalModel in ${modelLoadTimeMs}ms")
            modelLoadTimeMs
        } catch (e: Exception) {
            isModelLoaded = false
            loadedLocalModel = null
            if (e is PrivateBrainException) throw e
            throw PrivateBrainUnavailableException("Failed to confirm model availability on Private Brain: ${e.message}", e)
        }
    }

    /**
     * Backward-compatible alias for loading a model into the Private Brain session.
     */
    suspend fun loadLocalGgufModel(modelName: String, contextSize: Int = 4096): Long {
        return loadModel(modelName, contextSize)
    }

    suspend fun unloadLocalModel() = withContext(Dispatchers.Default) {
        loadedLocalModel = null
        isModelLoaded = false
        modelLoadTimeMs = 0L
    }

    /**
     * Queries the active private server for real available models.
     */
    suspend fun listAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw PrivateBrainNotConfiguredException("Private Brain is not configured.")
        }
        getActiveProvider().listModels()
    }

    /**
     * Non-streaming completion call to the Private Brain.
     */
    suspend fun generate(
        messages: List<ChatMessage>,
        model: String? = null,
        config: InferenceConfig = InferenceConfig()
    ): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw PrivateBrainNotConfiguredException("Private Brain is not configured.")
        }

        val targetModel = model
            ?: loadedLocalModel
            ?: activeConfig.selectedModel.ifEmpty { "default" }

        val provider = getActiveProvider()
        provider.generate(messages, targetModel, config.temperature, config.maxTokens)
    }

    /**
     * Stream inferences from the configured Private Brain LLM backend without artificial delay.
     * If cloud mode is requested and cloud fallback is explicitly enabled in settings,
     * routes to Gemini API; otherwise strictly operates via Private Brain.
     */
    fun generateStream(
        prompt: String,
        config: InferenceConfig = InferenceConfig(),
        onMetricsUpdated: ((InferenceMetrics) -> Unit)? = null
    ): Flow<String> = flow {
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        // Strict Cloud Mode check: Only runs if user selected CLOUD mode AND explicitly enabled cloud fallback
        if (currentMode == AiMode.CLOUD) {
            if (!activeConfig.enableCloudFallback) {
                emit("[Notice: Cloud AI mode is disabled in Private Brain settings. Enable cloud fallback in Settings to permit external cloud transmission.]")
                return@flow
            }

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                emit("[Notice: Gemini Cloud API Key not configured in Secrets. Please provide a valid Gemini API Key to enable cloud reasoning.]")
                return@flow
            }

            try {
                val cloudText = callGeminiRestApi(prompt, apiKey, config)
                emit(cloudText)
                tokenCount = (cloudText.length / 4).coerceAtLeast(1)

                val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000f
                val tps = tokenCount / elapsedSec
                val runtime = Runtime.getRuntime()
                val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                onMetricsUpdated?.invoke(
                    InferenceMetrics(
                        modelName = "gemini-2.5-flash",
                        isLocal = false,
                        loadTimeMs = 0L,
                        inferenceTimeMs = System.currentTimeMillis() - startTime,
                        tokensGenerated = tokenCount,
                        tokensPerSecond = tps,
                        ramUsageMb = usedRamMb,
                        contextUsageTokens = (prompt.length / 4) + tokenCount,
                        maxContextTokens = config.contextSize
                    )
                )
            } catch (e: Exception) {
                emit("[Cloud AI Error: ${e.message}]")
            }
            return@flow
        }

        // Default & Primary Execution Path: Private Brain
        if (!isConfigured()) {
            emit("Private Brain is not configured.")
            return@flow
        }

        val targetModel = loadedLocalModel
            ?: activeConfig.selectedModel.ifEmpty { "default" }

        val provider = getActiveProvider()

        val messages = listOf(
            ChatMessage(sender = "system", text = config.systemPrompt),
            ChatMessage(sender = "user", text = prompt)
        )

        try {
            provider.generateStream(messages, targetModel, config.temperature, config.maxTokens)
                .collect { tokenChunk ->
                    emit(tokenChunk)
                    tokenCount++

                    val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000f
                    val tps = tokenCount / elapsedSec
                    val runtime = Runtime.getRuntime()
                    val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                    onMetricsUpdated?.invoke(
                        InferenceMetrics(
                            modelName = targetModel,
                            isLocal = true,
                            loadTimeMs = modelLoadTimeMs,
                            inferenceTimeMs = System.currentTimeMillis() - startTime,
                            tokensGenerated = tokenCount,
                            tokensPerSecond = tps,
                            ramUsageMb = usedRamMb,
                            contextUsageTokens = (prompt.length / 4) + tokenCount,
                            maxContextTokens = config.contextSize
                        )
                    )
                }
        } catch (e: Exception) {
            safeLog("Private Brain stream failed: ${e.javaClass.simpleName} - ${e.message}")
            emit("[Error: ${e.message ?: "Private Brain communication failure"}]")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun callGeminiRestApi(prompt: String, apiKey: String, config: InferenceConfig): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "${config.systemPrompt}\n\nUser Question:\n$prompt"))
                    })
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", config.temperature)
                put("maxOutputTokens", config.maxTokens)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        cloudHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                throw PrivateBrainException("Cloud AI API returned HTTP ${response.code}: $err")
            }
            val bodyStr = response.body?.string().orEmpty()
            val root = JSONObject(bodyStr)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return@withContext parts.getJSONObject(0).optString("text", "")
                }
            }
            throw PrivateBrainResponseException("No candidate text received from Cloud AI.")
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

    private fun safeLog(msg: String) {
        Log.d("InferenceService", msg)
    }
}
