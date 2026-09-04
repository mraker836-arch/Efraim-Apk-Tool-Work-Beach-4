package com.example.ai.models

/**
 * Real Server State for Private Brain Model Management.
 * Replaces simulated/placeholder states with genuine backend connectivity tracking.
 */
enum class ServerConnectionState {
    UNCONFIGURED,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    AUTHENTICATION_ERROR,
    MODEL_NOT_FOUND,
    ERROR
}
