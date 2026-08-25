package com.example.security.scanner

import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity

enum class PermissionRiskCategory(val label: String) {
    NORMAL("Normal / Low Risk"),
    SENSITIVE("Sensitive / Dangerous (Runtime)"),
    HIGH_RISK("High Risk / System Level"),
    SPECIAL("Special Access / Overlay / Admin")
}

data class PermissionAnalysisResult(
    val permissionName: String,
    val shortName: String,
    val category: PermissionRiskCategory,
    val capability: String,
    val purposeExplanation: String,
    val isPotentiallyUnnecessary: Boolean,
    val recommendation: String
)

class APKPermissionAnalyzer {

    private val permissionDatabase = mapOf(
        // High Risk / System / Admin
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to PermissionDetail(
            PermissionRiskCategory.HIGH_RISK,
            "Accessibility Service Control",
            "Allows inspecting UI interactions, intercepting user touch events, and automating screen actions.",
            true,
            "Only legitimate assistive technology apps should request this. Verify requirement strictly."
        ),
        "android.permission.BIND_DEVICE_ADMIN" to PermissionDetail(
            PermissionRiskCategory.HIGH_RISK,
            "Device Administration",
            "Allows setting device security policies, locking the screen, or wiping storage.",
            true,
            "Enterprise device management only. Ensure robust user authorization is enforced."
        ),
        "android.permission.REQUEST_INSTALL_PACKAGES" to PermissionDetail(
            PermissionRiskCategory.HIGH_RISK,
            "Install Unknown Packages",
            "Allows the app to trigger APK package installations outside Google Play.",
            false,
            "Workbench / installer tool legitimately requires this; verify user consent flow is active."
        ),
        "android.permission.SYSTEM_ALERT_WINDOW" to PermissionDetail(
            PermissionRiskCategory.SPECIAL,
            "Draw Over Other Apps / Overlay",
            "Allows displaying windows on top of all other running applications.",
            true,
            "Can be abused for clickjacking / tapjacking if not guarded by Android 12+ overlay protections."
        ),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to PermissionDetail(
            PermissionRiskCategory.SPECIAL,
            "All Files Access (Scoped Storage Bypass)",
            "Grants broad access to shared external storage across all public directories.",
            false,
            "Required for APK file management tools, but Google Play enforces strict policy justifications."
        ),
        // Sensitive / Dangerous (Runtime)
        "android.permission.READ_EXTERNAL_STORAGE" to PermissionDetail(
            PermissionRiskCategory.SENSITIVE,
            "Read External Shared Storage",
            "Allows reading files stored in shared device storage.",
            false,
            "Use Scoped Storage (Storage Access Framework) on Android 10+ where possible."
        ),
        "android.permission.WRITE_EXTERNAL_STORAGE" to PermissionDetail(
            PermissionRiskCategory.SENSITIVE,
            "Write External Shared Storage",
            "Allows modifying or creating files in shared device storage on Android 9 and lower.",
            false,
            "Legacy permission. Scoped storage preferred on modern Android versions."
        ),
        "android.permission.CAMERA" to PermissionDetail(
            PermissionRiskCategory.SENSITIVE,
            "Camera Hardware Access",
            "Allows taking photos and capturing live video stream.",
            true,
            "Ensure runtime permission check is executed immediately before camera activation."
        ),
        "android.permission.RECORD_AUDIO" to PermissionDetail(
            PermissionRiskCategory.SENSITIVE,
            "Microphone Access",
            "Allows capturing ambient audio stream and user voice.",
            true,
            "Provide clear in-app indicators when recording audio."
        ),
        "android.permission.ACCESS_FINE_LOCATION" to PermissionDetail(
            PermissionRiskCategory.SENSITIVE,
            "Precise GPS Location",
            "Provides exact physical latitude and longitude of device.",
            true,
            "Evaluate if ACCESS_COARSE_LOCATION is sufficient for application needs."
        ),
        "android.permission.ACCESS_COARSE_LOCATION" to PermissionDetail(
            PermissionRiskCategory.SENSITIVE,
            "Approximate Location",
            "Provides cell/Wi-Fi derived city/neighborhood level location.",
            true,
            "Use only when location-dependent feature is active."
        ),
        "android.permission.READ_CONTACTS" to PermissionDetail(
            PermissionRiskCategory.SENSITIVE,
            "Read User Contacts",
            "Allows reading user address book and stored contact entries.",
            true,
            "Consider using system Contact Picker to avoid broad read access."
        ),
        "android.permission.READ_PHONE_STATE" to PermissionDetail(
            PermissionRiskCategory.SENSITIVE,
            "Phone State & Hardware Identifiers",
            "Allows reading phone state, cellular network status, and carrier info.",
            true,
            "Avoid accessing hardware identifiers; use anonymous session UUIDs instead."
        ),
        // Normal Permissions
        "android.permission.INTERNET" to PermissionDetail(
            PermissionRiskCategory.NORMAL,
            "Full Internet Access",
            "Allows opening network sockets to communicate with cloud servers and APIs.",
            false,
            "Standard permission for cloud sync, telemetry, and networking."
        ),
        "android.permission.ACCESS_NETWORK_STATE" to PermissionDetail(
            PermissionRiskCategory.NORMAL,
            "Network Connection State",
            "Allows checking whether device is on Wi-Fi, Cellular, or Offline.",
            false,
            "Standard for offline resilience and network monitoring."
        ),
        "android.permission.WAKE_LOCK" to PermissionDetail(
            PermissionRiskCategory.NORMAL,
            "Keep Processor Awake",
            "Prevents CPU from going to sleep during long-running background tasks.",
            false,
            "Ensure wake lock is released promptly to prevent battery drain."
        ),
        "android.permission.POST_NOTIFICATIONS" to PermissionDetail(
            PermissionRiskCategory.NORMAL,
            "Post Notifications (Android 13+)",
            "Allows displaying notifications in status bar.",
            false,
            "Standard notification permission."
        ),
        "android.permission.VIBRATE" to PermissionDetail(
            PermissionRiskCategory.NORMAL,
            "Haptic Feedback Control",
            "Allows controlling the device vibration motor.",
            false,
            "Safe haptic utility."
        )
    )

    fun analyzePermissions(permissions: List<String>): Pair<List<PermissionAnalysisResult>, List<SecurityFinding>> {
        val analysisResults = mutableListOf<PermissionAnalysisResult>()
        val findings = mutableListOf<SecurityFinding>()

        for (rawPerm in permissions) {
            val shortName = rawPerm.substringAfterLast(".")
            val detail = permissionDatabase[rawPerm] ?: PermissionDetail(
                PermissionRiskCategory.NORMAL,
                "Custom / Standard Android Permission",
                "Application-specific capability requested in AndroidManifest.",
                false,
                "Standard Android capability request."
            )

            val result = PermissionAnalysisResult(
                permissionName = rawPerm,
                shortName = shortName,
                category = detail.category,
                capability = detail.capability,
                purposeExplanation = detail.purposeExplanation,
                isPotentiallyUnnecessary = detail.isPotentiallyUnnecessary,
                recommendation = detail.recommendation
            )
            analysisResults.add(result)

            // Generate findings for high-risk / special or sensitive permissions
            when (detail.category) {
                PermissionRiskCategory.HIGH_RISK -> {
                    findings.add(
                        SecurityFinding(
                            category = FindingCategory.APK_PERMISSIONS,
                            severity = SecuritySeverity.HIGH,
                            title = "High Risk Permission: $shortName",
                            description = "Application declares $shortName (${detail.capability}). ${detail.purposeExplanation}",
                            evidence = "Manifest permission: $rawPerm",
                            recommendation = detail.recommendation,
                            module = "PermissionAnalyzer",
                            affectedFile = "AndroidManifest.xml",
                            status = FindingStatus.OPEN
                        )
                    )
                }
                PermissionRiskCategory.SPECIAL -> {
                    findings.add(
                        SecurityFinding(
                            category = FindingCategory.APK_PERMISSIONS,
                            severity = SecuritySeverity.MEDIUM,
                            title = "Special Access Permission: $shortName",
                            description = "Application requests special capability: ${detail.capability}.",
                            evidence = "Manifest permission: $rawPerm",
                            recommendation = detail.recommendation,
                            module = "PermissionAnalyzer",
                            affectedFile = "AndroidManifest.xml",
                            status = FindingStatus.OPEN
                        )
                    )
                }
                PermissionRiskCategory.SENSITIVE -> {
                    findings.add(
                        SecurityFinding(
                            category = FindingCategory.APK_PERMISSIONS,
                            severity = SecuritySeverity.LOW,
                            title = "Sensitive Runtime Permission: $shortName",
                            description = "Requires runtime consent on Android 6.0+. Capability: ${detail.capability}.",
                            evidence = "Manifest permission: $rawPerm",
                            recommendation = detail.recommendation,
                            module = "PermissionAnalyzer",
                            affectedFile = "AndroidManifest.xml",
                            status = FindingStatus.OPEN
                        )
                    )
                }
                PermissionRiskCategory.NORMAL -> {
                    // Normal permissions are informational only
                }
            }
        }

        return Pair(analysisResults, findings)
    }

    private data class PermissionDetail(
        val category: PermissionRiskCategory,
        val capability: String,
        val purposeExplanation: String,
        val isPotentiallyUnnecessary: Boolean,
        val recommendation: String
    )
}
