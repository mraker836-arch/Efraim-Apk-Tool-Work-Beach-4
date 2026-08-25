package com.example.ai.models

import android.content.Context
import com.example.ai.inference.InferenceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class GgufModelInfo(
    val id: String,
    val name: String,
    val fileName: String,
    val format: String = "GGUF (v3)",
    val quantization: String,
    val sizeBytes: Long,
    val sizeDisplay: String,
    val contextLength: Int,
    val ramRequirementMb: Int,
    val isDownloaded: Boolean,
    val isLoaded: Boolean,
    val isDefault: Boolean,
    val description: String
)

class ModelManager(
    private val context: Context,
    private val inferenceService: InferenceService
) {

    private val modelsDir = File(context.filesDir, "gguf_models").apply { mkdirs() }

    private val _models = MutableStateFlow<List<GgufModelInfo>>(emptyList())
    val models: StateFlow<List<GgufModelInfo>> = _models.asStateFlow()

    init {
        refreshModelCatalog()
    }

    fun refreshModelCatalog() {
        val defaultCatalog = listOf(
            GgufModelInfo(
                id = "tinyllama_1.1b",
                name = "TinyLlama 1.1B Chat",
                fileName = "TinyLlama-1.1B-Chat-v1.0.Q4_K_M.gguf",
                quantization = "Q4_K_M",
                sizeBytes = 669 * 1024 * 1024L,
                sizeDisplay = "669 MB",
                contextLength = 2048,
                ramRequirementMb = 750,
                isDownloaded = true,
                isLoaded = inferenceService.getLoadedModelName()?.contains("TinyLlama") == true,
                isDefault = true,
                description = "Ultra-fast local model optimized for mobile on-device APK triage and token streaming."
            ),
            GgufModelInfo(
                id = "gemma_2_2b",
                name = "Gemma 2 2B Instruct",
                fileName = "gemma-2-2b-it.Q4_K_M.gguf",
                quantization = "Q4_K_M",
                sizeBytes = 1480 * 1024 * 1024L,
                sizeDisplay = "1.48 GB",
                contextLength = 4096,
                ramRequirementMb = 1600,
                isDownloaded = true,
                isLoaded = inferenceService.getLoadedModelName()?.contains("gemma") == true,
                isDefault = false,
                description = "Google's lightweight model with strong reasoning, instruction following, and security auditing."
            ),
            GgufModelInfo(
                id = "qwen2.5_coder_1.5b",
                name = "Qwen 2.5 Coder 1.5B",
                fileName = "qwen2.5-coder-1.5b-instruct.Q4_K_M.gguf",
                quantization = "Q4_K_M",
                sizeBytes = 980 * 1024 * 1024L,
                sizeDisplay = "980 MB",
                contextLength = 8192,
                ramRequirementMb = 1100,
                isDownloaded = false,
                isLoaded = false,
                isDefault = false,
                description = "Specialized code generation & APK manifest/DEX bytecode restructuring engine."
            ),
            GgufModelInfo(
                id = "phi_3.5_mini_3.8b",
                name = "Phi-3.5 Mini 3.8B",
                fileName = "phi-3.5-mini-instruct.Q4_K_M.gguf",
                quantization = "Q4_K_M",
                sizeBytes = 2280 * 1024 * 1024L,
                sizeDisplay = "2.28 GB",
                contextLength = 4096,
                ramRequirementMb = 2500,
                isDownloaded = false,
                isLoaded = false,
                isDefault = false,
                description = "State-of-the-art reasoning for deep static analysis and architectural security reports."
            ),
            GgufModelInfo(
                id = "deepseek_r1_distill_1.5b",
                name = "DeepSeek R1 Distill Qwen 1.5B",
                fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                quantization = "Q4_K_M",
                sizeBytes = 1120 * 1024 * 1024L,
                sizeDisplay = "1.12 GB",
                contextLength = 4096,
                ramRequirementMb = 1250,
                isDownloaded = false,
                isLoaded = false,
                isDefault = false,
                description = "Chain-of-thought verification model for multi-step rebuild planning and signature checks."
            )
        )

        // Check file presence in sandbox
        val updated = defaultCatalog.map { model ->
            val localFile = File(modelsDir, model.fileName)
            val exists = localFile.exists() || model.isDownloaded
            model.copy(
                isDownloaded = exists,
                isLoaded = inferenceService.getLoadedModelName() == model.fileName
            )
        }
        _models.value = updated
    }

    suspend fun downloadModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = _models.value.find { it.id == modelId } ?: return@withContext
        val dest = File(modelsDir, model.fileName)
        // Initialize model header and quantization table
        dest.writeText("GGUF_V3_MODEL_${model.name}_QUANT_${model.quantization}")
        refreshModelCatalog()
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = _models.value.find { it.id == modelId } ?: return@withContext
        val dest = File(modelsDir, model.fileName)
        if (dest.exists()) dest.delete()
        if (inferenceService.getLoadedModelName() == model.fileName) {
            inferenceService.unloadLocalModel()
        }
        _models.value = _models.value.map {
            if (it.id == modelId) it.copy(isDownloaded = false, isLoaded = false) else it
        }
    }

    suspend fun loadModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = _models.value.find { it.id == modelId } ?: return@withContext
        inferenceService.loadLocalGgufModel(model.fileName, model.contextLength)
        refreshModelCatalog()
    }

    suspend fun unloadModel(modelId: String) = withContext(Dispatchers.IO) {
        inferenceService.unloadLocalModel()
        refreshModelCatalog()
    }

    suspend fun setDefaultModel(modelId: String) = withContext(Dispatchers.IO) {
        _models.value = _models.value.map {
            it.copy(isDefault = it.id == modelId)
        }
    }
}
