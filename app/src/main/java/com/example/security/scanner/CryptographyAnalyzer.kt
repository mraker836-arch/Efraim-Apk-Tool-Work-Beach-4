package com.example.security.scanner

import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity

class CryptographyAnalyzer {

    fun analyzeCryptography(
        codebaseContent: String,
        sourceFile: String = "CryptoModule.kt"
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        // Weak hashing (MD5 or SHA-1 for cryptographic security)
        if (codebaseContent.contains("MessageDigest.getInstance(\"MD5\")") || codebaseContent.contains("MessageDigest.getInstance(\"SHA-1\")")) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.CRYPTOGRAPHY,
                    severity = SecuritySeverity.MEDIUM,
                    title = "Weak Cryptographic Hash Algorithm (MD5/SHA-1)",
                    description = "MD5 and SHA-1 are vulnerable to collision attacks and must not be used for cryptographic signatures, passwords, or security verification.",
                    evidence = "MessageDigest configured with MD5 or SHA-1 in $sourceFile",
                    recommendation = "Upgrade hashing algorithms to SHA-256 or SHA-512.",
                    module = "CryptographyEngine",
                    affectedFile = sourceFile,
                    status = FindingStatus.OPEN
                )
            )
        }

        // Insecure cipher mode (AES/ECB)
        if (codebaseContent.contains("Cipher.getInstance(\"AES/ECB") || codebaseContent.contains("Cipher.getInstance(\"AES\")")) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.CRYPTOGRAPHY,
                    severity = SecuritySeverity.HIGH,
                    title = "Insecure Cipher Mode (AES/ECB)",
                    description = "Electronic Codebook (ECB) mode produces identical ciphertext for identical plaintext blocks, leaking pattern structures.",
                    evidence = "Cipher initialization uses AES/ECB in $sourceFile",
                    recommendation = "Use AES/GCM/NoPadding (Galois/Counter Mode) with a unique initialization vector (IV) per encryption.",
                    module = "CryptographyEngine",
                    affectedFile = sourceFile,
                    status = FindingStatus.OPEN
                )
            )
        }

        // Insecure Random Number Generator
        if (codebaseContent.contains("java.util.Random") && (codebaseContent.contains("key") || codebaseContent.contains("token") || codebaseContent.contains("nonce") || codebaseContent.contains("iv"))) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.CRYPTOGRAPHY,
                    severity = SecuritySeverity.MEDIUM,
                    title = "Predictable Pseudo-Random Number Generator",
                    description = "java.util.Random is not cryptographically secure and produces predictable sequences.",
                    evidence = "java.util.Random used in security-sensitive context in $sourceFile",
                    recommendation = "Use java.security.SecureRandom for all security nonces, tokens, IVs, and keys.",
                    module = "CryptographyEngine",
                    affectedFile = sourceFile,
                    status = FindingStatus.OPEN
                )
            )
        }

        return findings
    }
}
