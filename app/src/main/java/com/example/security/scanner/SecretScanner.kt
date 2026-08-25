package com.example.security.scanner

import com.example.diagnostics.redactor.SensitiveDataRedactor
import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import java.io.File
import java.util.regex.Pattern

data class SecretFinding(
    val file: String,
    val lineNumber: Int,
    val secretType: String,
    val severity: SecuritySeverity,
    val redactedSnippet: String,
    val rawEvidence: String
)

class SecretScanner {

    private val secretPatterns = listOf(
        // Google API Keys
        Pair(
            Pattern.compile("(?i)AIza[0-9A-Za-z\\-_]{25,45}"),
            "Google Cloud / Firebase API Key"
        ),
        // GitHub Personal Access Tokens / Fine-Grained
        Pair(
            Pattern.compile("gh[pousr]_[A-Za-z0-9_]{20,255}"),
            "GitHub Access Token"
        ),
        // AWS Access Key ID
        Pair(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            "AWS Access Key ID"
        ),
        // Generic API Key assignment
        Pair(
            Pattern.compile("(?i)(api[_-]?key|access[_-]?token|auth[_-]?token|secret[_-]?key|client[_-]?secret)\\s*[:=]\\s*['\"]([a-zA-Z0-9_\\-.~]{12,})['\"]"),
            "Generic Hardcoded API Secret"
        ),
        // Keystore & Signing Passwords
        Pair(
            Pattern.compile("(?i)(storepassword|keypassword|keystorepass|keypass)\\s*[:=]\\s*['\"]([^'\"\\s]{4,})['\"]"),
            "Hardcoded Keystore / Key Password"
        ),
        // Private Key Blocks
        Pair(
            Pattern.compile("-----BEGIN (RSA|EC|OPENSSH|DSA|PGP)? ?PRIVATE KEY-----"),
            "Unencrypted Private Key Block"
        ),
        // JSON Web Token
        Pair(
            Pattern.compile("ey[A-Za-z0-9_\\-]{15,}\\.ey[A-Za-z0-9_\\-]{15,}\\.[A-Za-z0-9_\\-]{10,}"),
            "Hardcoded JSON Web Token (JWT)"
        )
    )

    // Suspicious filenames that should not be tracked or packaged directly
    private val sensitiveFileNames = listOf(
        ".env",
        ".env.local",
        ".env.production",
        "release.keystore",
        "debug.keystore",
        "release.jks",
        "service-account.json",
        "google-services.json.secret",
        "id_rsa",
        "id_ecdsa",
        "id_ed25519"
    )

    /**
     * Scans arbitrary text content (e.g. manifest, config, dex class names, logs) for secrets.
     */
    fun scanContent(
        content: String,
        sourceName: String = "in-memory"
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()
        val lines = content.lines()

        for ((idx, line) in lines.withIndex()) {
            val lineNum = idx + 1
            // Skip common safe placeholder examples
            if (line.contains("your-api-key", ignoreCase = true) ||
                line.contains("example_key", ignoreCase = true) ||
                line.contains("TODO", ignoreCase = true) && line.contains("REPLACE", ignoreCase = true)
            ) {
                continue
            }

            for ((pattern, label) in secretPatterns) {
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    val matchedGroup = matcher.group(0) ?: ""
                    val safeRedacted = redactSecret(matchedGroup)
                    val redactedLine = line.replace(matchedGroup, safeRedacted)

                    val isHighImpact = label.contains("Private Key") || label.contains("Keystore") || label.contains("Access Token")
                    val severity = if (isHighImpact) SecuritySeverity.CRITICAL else SecuritySeverity.HIGH

                    findings.add(
                        SecurityFinding(
                            category = FindingCategory.SECRETS,
                            severity = severity,
                            title = "Hardcoded Secret: $label",
                            description = "Detected potential secret credential ($label) hardcoded in $sourceName at line $lineNum.",
                            evidence = "Line $lineNum: ${redactedLine.trim()}",
                            recommendation = "Move secret to secure environment configuration, Android Secrets Panel, or GitHub Actions Secrets. Never hardcode credentials into source or packaged assets.",
                            module = "SecretsEngine",
                            affectedFile = sourceName,
                            status = FindingStatus.OPEN
                        )
                    )
                }
            }
        }
        return findings
    }

    /**
     * Scans project files and directory structure for secret exposure and tracked credential files.
     */
    fun scanDirectory(
        projectDir: File,
        maxDepth: Int = 4
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()
        if (!projectDir.exists()) return findings

        // Check for sensitive files
        projectDir.walkTopDown()
            .maxDepth(maxDepth)
            .filter { it.isFile }
            .forEach { file ->
                val fileName = file.name
                if (sensitiveFileNames.any { it.equals(fileName, ignoreCase = true) || fileName.endsWith(".keystore") || fileName.endsWith(".jks") }) {
                    findings.add(
                        SecurityFinding(
                            category = FindingCategory.SECRETS,
                            severity = SecuritySeverity.HIGH,
                            title = "Tracked Credential / Secret File: $fileName",
                            description = "Found sensitive credential or keystore file '$fileName' in project directory.",
                            evidence = "File path: ${file.relativeToOrSelf(projectDir).path}",
                            recommendation = "Ensure $fileName is added to .gitignore and stored via Android Secrets panel or CI encrypted secrets.",
                            module = "GitSecurity",
                            affectedFile = file.name,
                            status = FindingStatus.OPEN
                        )
                    )
                }

                // Scan text-based files for hardcoded secrets
                if (isScannableTextFile(file) && file.length() < 1024 * 1024L) { // max 1MB per text file
                    try {
                        val content = file.readText()
                        findings.addAll(scanContent(content, file.name))
                    } catch (e: Exception) {
                        // Skip unreadable files
                    }
                }
            }

        return findings
    }

    private fun isScannableTextFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in setOf("kt", "java", "xml", "json", "gradle", "kts", "yml", "yaml", "properties", "md", "txt", "env")
    }

    /**
     * Redacts secret values keeping prefix and suffix if long, or fully redacting.
     * Example: AIzaSyD1234567890 -> AIza...REDACTED
     */
    fun redactSecret(secret: String): String {
        if (secret.length <= 8) return "[REDACTED_SECRET]"
        val prefix = secret.take(4)
        return "$prefix...[REDACTED]"
    }
}
