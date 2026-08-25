package com.example.security.model

data class BuildProvenance(
    val commitSha: String,
    val version: String,
    val flutterVersion: String = "N/A (Native Kotlin/Compose)",
    val javaVersion: String = System.getProperty("java.version") ?: "17",
    val gradleVersion: String = "8.5",
    val androidGradlePlugin: String = "8.3.0",
    val dependenciesHash: String,
    val artifactHash: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ArtifactIntegrityRecord(
    val artifactName: String,
    val sha256: String,
    val sizeBytes: Long,
    val version: String,
    val registeredAt: Long = System.currentTimeMillis()
)
