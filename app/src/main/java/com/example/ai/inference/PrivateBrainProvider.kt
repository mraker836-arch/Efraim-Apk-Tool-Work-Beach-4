package com.example.ai.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Common data model for chat messages across the Private Brain and DIANA subsystems.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user", "diana", "assistant", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metrics: InferenceMetrics? = null
)

/**
 * Server compatibility mode for Private Brain endpoints.
 */
enum class PrivateBrainServerType {
    AUTO,
    OPENAI_COMPATIBLE,
    OLLAMA
}

/**
 * Runtime configuration for the Private Brain LLM backend.
 * By default, cloud fallback is strictly disabled and requires explicit opt-in.
 */
data class PrivateBrainConfig(
    val baseUrl: String = "",
    val apiKey: String? = null,
    val selectedModel: String = "",
    val serverType: PrivateBrainServerType = PrivateBrainServerType.AUTO,
    val enableCloudFallback: Boolean = false,
    val connectTimeoutSeconds: Long = 15L,
    val readTimeoutSeconds: Long = 60L
)

// --- Typed Exceptions for Real Error States ---

open class PrivateBrainException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PrivateBrainUnavailableException(message: String, cause: Throwable? = null) :
    PrivateBrainException(message, cause)

class PrivateBrainNotConfiguredException(message: String = "Private Brain is not configured.") :
    PrivateBrainException(message)

class PrivateBrainAuthException(message: String, cause: Throwable? = null) :
    PrivateBrainException(message, cause)

class PrivateBrainModelNotFoundException(message: String, cause: Throwable? = null) :
    PrivateBrainException(message, cause)

class PrivateBrainTimeoutException(message: String, cause: Throwable? = null) :
    PrivateBrainException(message, cause)

class PrivateBrainResponseException(message: String, cause: Throwable? = null) :
    PrivateBrainException(message, cause)

/**
 * Provider interface for Private Brain backends (e.g. self-hosted Ollama, vLLM,
 * llama.cpp server, LocalAI, or any OpenAI-compatible API).
 */
interface PrivateBrainProvider {
    /**
     * Checks if the private server is currently reachable and operational.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Queries the configured server for models actually installed and available.
     * Never returns hardcoded or fabricated model lists.
     */
    suspend fun listModels(): List<String>

    /**
     * Generates a complete textual response from the model.
     */
    suspend fun generate(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        maxTokens: Int
    ): String

    /**
     * Real-time streaming generation emitting delta tokens as they arrive over HTTP.
     * No fake delays or artificial token slicing.
     */
    fun generateStream(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        emit(generate(messages, model, temperature, maxTokens))
    }
}
