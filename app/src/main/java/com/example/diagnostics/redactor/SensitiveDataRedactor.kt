package com.example.diagnostics.redactor

import java.util.regex.Pattern

object SensitiveDataRedactor {

    private val REDACTED_PLACEHOLDER = "[REDACTED]"

    // Patterns for known secret formats
    private val PATTERNS = listOf(
        // API Keys & Tokens
        Pattern.compile("(?i)(api[_-]?key|apikey|access[_-]?token|auth[_-]?token|secret[_-]?key|client[_-]?secret)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_\\-.~]{6,})['\"]?"),
        Pattern.compile("AIza[0-9A-Za-z\\-_]{35}"), // Google API keys
        Pattern.compile("gh[pousr]_[A-Za-z0-9_]{20,255}"), // GitHub Tokens
        Pattern.compile("Bearer\\s+[a-zA-Z0-9\\-._~+/]+=*"), // Bearer Tokens
        Pattern.compile("ey[A-Za-z0-9_\\-]{10,}\\.ey[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}"), // JWT Tokens
        Pattern.compile("(?i)(password|passwd|pwd|keystorepass|keystorepassword|keypass|keypassword|storepass|storepassword|secret)\\s*[:=]\\s*['\"]?([^'\"\\s,;]{3,})['\"]?"),
        Pattern.compile("-----BEGIN [A-Z\\s]+PRIVATE KEY-----[\\s\\S]*?-----END [A-Z\\s]+PRIVATE KEY-----"),
        Pattern.compile("(?i)REAL_SECRET_VALUE")
    )

    /**
     * Redacts sensitive secrets from a single string.
     */
    fun redact(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var result = input

        for (pattern in PATTERNS) {
            val matcher = pattern.matcher(result)
            val sb = StringBuffer()
            while (matcher.find()) {
                val matched = matcher.group()
                val replacement = when {
                    matcher.groupCount() >= 2 -> {
                        // Key-value pair like apiKey = "xyz" -> apiKey = "[REDACTED]"
                        val key = matcher.group(1)
                        "$key=$REDACTED_PLACEHOLDER"
                    }
                    matched.startsWith("Bearer ", ignoreCase = true) -> {
                        "Bearer $REDACTED_PLACEHOLDER"
                    }
                    else -> REDACTED_PLACEHOLDER
                }
                matcher.appendReplacement(sb, MatcherQuoteReplacement(replacement))
            }
            matcher.appendTail(sb)
            result = sb.toString()
        }

        return result
    }

    private fun MatcherQuoteReplacement(s: String): String {
        return java.util.regex.Matcher.quoteReplacement(s)
    }

    /**
     * Sanitizes an exception message and stack trace.
     */
    fun redactStackTrace(throwable: Throwable?): String {
        if (throwable == null) return ""
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        throwable.printStackTrace(pw)
        return redact(sw.toString())
    }
}
