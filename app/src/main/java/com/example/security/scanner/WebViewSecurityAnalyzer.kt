package com.example.security.scanner

import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity

class WebViewSecurityAnalyzer {

    fun analyzeWebViewUsage(
        codebaseContent: String,
        sourceFile: String = "WebViewComponent.kt"
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        if (codebaseContent.contains("setJavaScriptEnabled(true)") || codebaseContent.contains("javaScriptEnabled = true")) {
            val hasBridge = codebaseContent.contains("addJavascriptInterface")
            val severity = if (hasBridge) SecuritySeverity.HIGH else SecuritySeverity.MEDIUM

            findings.add(
                SecurityFinding(
                    category = FindingCategory.WEBVIEW_SECURITY,
                    severity = severity,
                    title = if (hasBridge) "WebView JavaScript Bridge Enabled" else "WebView JavaScript Execution Enabled",
                    description = "JavaScript is enabled on WebView instance. ${if (hasBridge) "Exposed native JavaScriptInterface detected without origin check." else "Untrusted URL loading may trigger Cross-Site Scripting (XSS)."}",
                    evidence = "JavaScript enabled in $sourceFile",
                    recommendation = "Ensure URLs loaded into WebView are restricted to trusted domains and validate all @JavascriptInterface inputs strictly.",
                    module = "WebViewSecurity",
                    affectedFile = sourceFile,
                    status = FindingStatus.OPEN
                )
            )
        }

        if (codebaseContent.contains("setAllowFileAccess(true)") || codebaseContent.contains("allowFileAccess = true")) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.WEBVIEW_SECURITY,
                    severity = SecuritySeverity.HIGH,
                    title = "WebView Unrestricted File Access",
                    description = "WebView allows access to local filesystem (file:// scheme), potentially exposing internal app sandbox files to web content.",
                    evidence = "setAllowFileAccess(true) configured in $sourceFile",
                    recommendation = "Disable allowFileAccess unless strictly required; use androidx.webkit.WebViewAssetLoader instead.",
                    module = "WebViewSecurity",
                    affectedFile = sourceFile,
                    status = FindingStatus.OPEN
                )
            )
        }

        if (codebaseContent.contains("onReceivedSslError") && codebaseContent.contains("handler.proceed()")) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.WEBVIEW_SECURITY,
                    severity = SecuritySeverity.CRITICAL,
                    title = "WebView Ignores SSL/TLS Certificate Errors",
                    description = "WebViewClient overrides onReceivedSslError and calls handler.proceed(), completely bypassing SSL validation and enabling silent MitM attacks.",
                    evidence = "handler.proceed() in onReceivedSslError found in $sourceFile",
                    recommendation = "Never bypass SSL errors in production. Call handler.cancel() and show a secure error dialog to the user.",
                    module = "WebViewSecurity",
                    affectedFile = sourceFile,
                    status = FindingStatus.OPEN
                )
            )
        }

        return findings
    }
}
