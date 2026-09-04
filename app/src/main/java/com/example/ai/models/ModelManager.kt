package com.example.ai.models

import android.content.Context
import android.util.Log
import com.example.ai.inference.InferenceService
import com.example.ai.inference.PrivateBrainAuthException
import com.example.ai.inference.PrivateBrainModelNotFoundException
import com.example.ai.inference.PrivateBrainNotConfiguredException
import com.example.ai.inference.PrivateBrainUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Metadata for a model discovered from or configured for the real Private Brain backend.
 */
data class GgufModelInfo(
    val id: String,
    val name: String,
    val fileName: String,
    val format: String = "Private Server Model",
    val quantization: String = "Server Managed",
    val sizeBytes: Long = 0L,
    val sizeDisplay: String = "Server Managed",
    val contextLength: Int = 4096,
    val ramRequirementMb: Int = 0,
    val isDownloaded: Boolean = true, // Reflects availability on the private LLM server
    val isLoaded: Boolean = false,
    val isDefault: Boolean = false,
    val description: String = ""
)

/**
 * Real Private-Model Management Component.
 * Interacts with the real PrivateBrainProvider via InferenceService.
 * Contains no fake model downloads, placeholder file writers, or hard-coded lists.
 */
class ModelManager(
    private val context: Context,
    private val inferenceService: InferenceService
) {
    private val prefs = context.getSharedPreferences("efraim_model_manager_prefs", Context.MODE_PRIVATE)

    private val _models = MutableStateFlow<List<GgufModelInfo>>(emptyList())
    val models: StateFlow<List<GgufModelInfo>> = _models.asStateFlow()

    private val _serverState = MutableStateFlow<ServerConnectionState>(ServerConnectionState.UNCONFIGURED)
    val serverState: StateFlow<ServerConnectionState> = _serverState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        // Initial refresh based on current configuration and persistence
        refreshModelCatalog()
    }

    private fun getDefaultModelPreference(): String? {
        return prefs.getString("default_model_id", null)
    }

    private fun saveDefaultModelPreference(modelId: String) {
        prefs.edit().putString("default_model_id", modelId).apply()
    }

    /**
     * Synchronous catalog check using cached list or initial discovery state.
     */
    fun refreshModelCatalog() {
        val configured = inferenceService.isConfigured()
        if (!configured) {
            _serverState.value = ServerConnectionState.UNCONFIGURED
            _statusMessage.value = "Private Brain server is not configured. Configure Endpoint in Settings."
            val currentLoaded = inferenceService.getLoadedModelName()
            if (currentLoaded != null && _models.value.isNotEmpty()) {
                _models.value = _models.value.map {
                    it.copy(isLoaded = it.fileName.equals(currentLoaded, ignoreCase = true))
                }
            }
            return
        }

        val currentLoaded = inferenceService.getLoadedModelName()
        if (_models.value.isNotEmpty()) {
            _models.value = _models.value.map {
                it.copy(isLoaded = it.fileName.equals(currentLoaded, ignoreCase = true))
            }
        }
    }

    /**
     * Real server discovery: queries PrivateBrainProvider.listModels() and updates models list.
     */
    suspend fun refreshModels(): List<GgufModelInfo> = withContext(Dispatchers.IO) {
        if (!inferenceService.isConfigured()) {
            _serverState.value = ServerConnectionState.UNCONFIGURED
            _statusMessage.value = "Private Brain server is not configured. Configure Endpoint in Settings."
            _models.value = emptyList()
            return@withContext emptyList()
        }

        _serverState.value = ServerConnectionState.CHECKING
        _statusMessage.value = "Connecting to Private Brain server..."

        try {
            val provider = inferenceService.getActiveProvider()
            val available = provider.isAvailable()
            if (!available) {
                _serverState.value = ServerConnectionState.UNAVAILABLE
                _statusMessage.value = "Server is unavailable or unreachable."
                return@withContext _models.value
            }

            val remoteModels = provider.listModels()
            val defaultPref = getDefaultModelPreference()
            val currentlyLoaded = inferenceService.getLoadedModelName()

            val updatedList = remoteModels.mapIndexed { index, modelName ->
                val isDefault = if (defaultPref != null) {
                    modelName.equals(defaultPref, ignoreCase = true)
                } else {
                    index == 0
                }
                val isLoaded = currentlyLoaded?.equals(modelName, ignoreCase = true) == true

                GgufModelInfo(
                    id = modelName,
                    name = formatDisplayName(modelName),
                    fileName = modelName,
                    format = "Remote Private LLM",
                    quantization = extractQuantization(modelName),
                    sizeBytes = 0L,
                    sizeDisplay = "Server Hosted",
                    contextLength = 4096,
                    ramRequirementMb = 0,
                    isDownloaded = true,
                    isLoaded = isLoaded,
                    isDefault = isDefault,
                    description = "Verified model hosted on private inference server: $modelName"
                )
            }

            _models.value = updatedList
            _serverState.value = ServerConnectionState.AVAILABLE
            _statusMessage.value = "Connected. Discovered ${updatedList.size} models."
            updatedList
        } catch (e: PrivateBrainAuthException) {
            _serverState.value = ServerConnectionState.AUTHENTICATION_ERROR
            _statusMessage.value = "Authentication failed: Check your API key in Settings."
            Log.w("ModelManager", "Authentication error on Private Brain server", e)
            _models.value
        } catch (e: PrivateBrainUnavailableException) {
            _serverState.value = ServerConnectionState.UNAVAILABLE
            _statusMessage.value = "Server unavailable: ${e.message ?: "Could not reach endpoint."}"
            Log.w("ModelManager", "Server unavailable", e)
            _models.value
        } catch (e: Exception) {
            _serverState.value = ServerConnectionState.ERROR
            _statusMessage.value = "Discovery error: ${e.message ?: "Unknown error"}"
            Log.e("ModelManager", "Failed to refresh models from Private Brain", e)
            _models.value
        }
    }

    /**
     * Confirms whether a model exists on the real backend server.
     */
    suspend fun isModelAvailable(modelName: String): Boolean = withContext(Dispatchers.IO) {
        if (!inferenceService.isConfigured()) return@withContext false
        try {
            val provider = inferenceService.getActiveProvider()
            val list = provider.listModels()
            list.any { it.equals(modelName, ignoreCase = true) || it.contains(modelName, ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Tests server connectivity and authentication directly.
     */
    suspend fun testConnection(): ServerConnectionState = withContext(Dispatchers.IO) {
        if (!inferenceService.isConfigured()) {
            _serverState.value = ServerConnectionState.UNCONFIGURED
            _statusMessage.value = "Private Brain endpoint is not set."
            return@withContext ServerConnectionState.UNCONFIGURED
        }

        _serverState.value = ServerConnectionState.CHECKING
        _statusMessage.value = "Testing server connection..."

        try {
            val provider = inferenceService.getActiveProvider()
            val available = provider.isAvailable()
            if (available) {
                _serverState.value = ServerConnectionState.AVAILABLE
                _statusMessage.value = "Connection established successfully."
                ServerConnectionState.AVAILABLE
            } else {
                _serverState.value = ServerConnectionState.UNAVAILABLE
                _statusMessage.value = "Server returned offline status."
                ServerConnectionState.UNAVAILABLE
            }
        } catch (e: PrivateBrainAuthException) {
            _serverState.value = ServerConnectionState.AUTHENTICATION_ERROR
            _statusMessage.value = "Authentication failed."
            ServerConnectionState.AUTHENTICATION_ERROR
        } catch (e: Exception) {
            _serverState.value = ServerConnectionState.ERROR
            _statusMessage.value = "Connection failed: ${e.message}"
            ServerConnectionState.ERROR
        }
    }

    /**
     * Connects/selects a model for active DIANA inference on the real server.
     */
    suspend fun loadModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = _models.value.find { it.id == modelId } ?: GgufModelInfo(
            id = modelId,
            name = modelId,
            fileName = modelId,
            isDownloaded = true,
            isLoaded = false,
            isDefault = false
        )

        try {
            _statusMessage.value = "Connecting model '$modelId'..."
            inferenceService.loadModel(model.fileName, model.contextLength)
            val currentLoaded = inferenceService.getLoadedModelName()

            _models.value = _models.value.map {
                it.copy(isLoaded = it.fileName.equals(currentLoaded, ignoreCase = true))
            }
            _serverState.value = ServerConnectionState.AVAILABLE
            _statusMessage.value = "Model '$modelId' is active for Private Brain inference."
        } catch (e: PrivateBrainModelNotFoundException) {
            _serverState.value = ServerConnectionState.MODEL_NOT_FOUND
            _statusMessage.value = "Model '$modelId' not found on server."
            throw e
        } catch (e: Exception) {
            _serverState.value = ServerConnectionState.ERROR
            _statusMessage.value = "Failed to activate model: ${e.message}"
            throw e
        }
    }

    /**
     * Unloads/disconnects the active model session.
     */
    suspend fun unloadModel(modelId: String) = withContext(Dispatchers.IO) {
        inferenceService.unloadLocalModel()
        _models.value = _models.value.map {
            if (it.id == modelId || it.fileName == modelId) it.copy(isLoaded = false) else it
        }
        _statusMessage.value = "Model session unloaded."
    }

    /**
     * Set a default model preference.
     */
    suspend fun setDefaultModel(modelId: String) = withContext(Dispatchers.IO) {
        saveDefaultModelPreference(modelId)
        _models.value = _models.value.map {
            it.copy(isDefault = it.id == modelId)
        }
    }

    /**
     * Model installation is managed on the server side; no arbitrary local fake downloads.
     */
    suspend fun downloadModel(modelId: String) = withContext(Dispatchers.IO) {
        _statusMessage.value = "Model installation is managed by the configured private LLM server."
        Log.i("ModelManager", "Model installation is managed by the configured private LLM server.")
    }

    /**
     * Deletes selection or local cache entry.
     */
    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        if (inferenceService.getLoadedModelName() == modelId) {
            inferenceService.unloadLocalModel()
        }
        _models.value = _models.value.filterNot { it.id == modelId }
        _statusMessage.value = "Model '$modelId' removed from local list view."
    }

    private fun formatDisplayName(modelName: String): String {
        return modelName.replace(":", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun extractQuantization(modelName: String): String {
        val lower = modelName.lowercase()
        return when {
            lower.contains("q4_k_m") -> "Q4_K_M"
            lower.contains("q4_0") -> "Q4_0"
            lower.contains("q8_0") -> "Q8_0"
            lower.contains("q5_k_m") -> "Q5_K_M"
            lower.contains("fp16") -> "FP16"
            lower.contains("16b") || lower.contains("8b") || lower.contains("7b") || lower.contains("1.5b") || lower.contains("2b") -> "Standard"
            else -> "Server Managed"
        }
    }
}
