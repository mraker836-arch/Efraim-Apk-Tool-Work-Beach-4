package com.example.security.integrity

import com.example.core.CryptoUtils
import com.example.security.model.ArtifactIntegrityRecord
import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class ChecksumMatchResult {
    MATCH,
    MISMATCH,
    UNKNOWN
}

data class TamperCheckResult(
    val result: ChecksumMatchResult,
    val expectedHash: String?,
    val actualHash: String,
    val finding: SecurityFinding? = null
)

class ArtifactHasher {

    // In-memory repository of registered artifact hashes indexed by artifactName / version
    private val recordedHashes = ConcurrentHashMap<String, ArtifactIntegrityRecord>()

    fun computeAndRecordHash(
        artifactFile: File,
        version: String = "1.0"
    ): ArtifactIntegrityRecord {
        val sha256 = if (artifactFile.exists()) {
            CryptoUtils.calculateSha256(artifactFile)
        } else {
            "0000000000000000000000000000000000000000000000000000000000000000"
        }

        val record = ArtifactIntegrityRecord(
            artifactName = artifactFile.name,
            sha256 = sha256,
            sizeBytes = if (artifactFile.exists()) artifactFile.length() else 0L,
            version = version,
            registeredAt = System.currentTimeMillis()
        )

        recordedHashes[artifactFile.name] = record
        return record
    }

    fun registerExpectedHash(
        artifactName: String,
        sha256: String,
        version: String = "1.0",
        sizeBytes: Long = 0L
    ) {
        recordedHashes[artifactName] = ArtifactIntegrityRecord(
            artifactName = artifactName,
            sha256 = sha256.lowercase().trim(),
            sizeBytes = sizeBytes,
            version = version,
            registeredAt = System.currentTimeMillis()
        )
    }

    fun verifyArtifactIntegrity(artifactFile: File): TamperCheckResult {
        if (!artifactFile.exists()) {
            return TamperCheckResult(
                result = ChecksumMatchResult.UNKNOWN,
                expectedHash = null,
                actualHash = "",
                finding = SecurityFinding(
                    category = FindingCategory.ARTIFACT_TAMPER,
                    severity = SecuritySeverity.HIGH,
                    title = "Artifact File Not Found for Integrity Check",
                    description = "Cannot verify checksum for missing artifact: ${artifactFile.name}",
                    evidence = "File does not exist at ${artifactFile.absolutePath}",
                    recommendation = "Rebuild target APK before executing integrity check.",
                    module = "ArtifactHasher",
                    affectedFile = artifactFile.name,
                    status = FindingStatus.OPEN
                )
            )
        }

        val actualHash = CryptoUtils.calculateSha256(artifactFile).lowercase()
        val record = recordedHashes[artifactFile.name]

        if (record == null) {
            // First time seeing this artifact -> record it as baseline
            recordedHashes[artifactFile.name] = ArtifactIntegrityRecord(
                artifactName = artifactFile.name,
                sha256 = actualHash,
                sizeBytes = artifactFile.length(),
                version = "1.0",
                registeredAt = System.currentTimeMillis()
            )
            return TamperCheckResult(
                result = ChecksumMatchResult.MATCH,
                expectedHash = actualHash,
                actualHash = actualHash
            )
        }

        val expected = record.sha256.lowercase().trim()
        if (expected == actualHash) {
            return TamperCheckResult(
                result = ChecksumMatchResult.MATCH,
                expectedHash = expected,
                actualHash = actualHash
            )
        } else {
            val finding = SecurityFinding(
                category = FindingCategory.ARTIFACT_TAMPER,
                severity = SecuritySeverity.CRITICAL,
                title = "CRITICAL: Artifact Tamper / Checksum Mismatch",
                description = "Calculated SHA-256 ($actualHash) does not match recorded baseline ($expected). Artifact may have been modified or corrupted post-build.",
                evidence = "Expected: $expected, Actual: $actualHash",
                recommendation = "Do not deploy or release this artifact. Invalidate artifact and rebuild from trusted clean source.",
                module = "ArtifactHasher",
                affectedFile = artifactFile.name,
                status = FindingStatus.OPEN
            )
            return TamperCheckResult(
                result = ChecksumMatchResult.MISMATCH,
                expectedHash = expected,
                actualHash = actualHash,
                finding = finding
            )
        }
    }

    fun getRecordedRecord(artifactName: String): ArtifactIntegrityRecord? {
        return recordedHashes[artifactName]
    }
}
