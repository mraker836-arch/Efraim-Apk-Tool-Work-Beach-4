package com.example.security.scanner

import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity

enum class ComponentType {
    ACTIVITY,
    SERVICE,
    RECEIVER,
    PROVIDER
}

data class ExportedComponentInfo(
    val componentName: String,
    val type: ComponentType,
    val isExported: Boolean,
    val permissionProtection: String?,
    val hasIntentFilter: Boolean,
    val riskLevel: SecuritySeverity,
    val recommendation: String
)

class ExportedComponentAnalyzer {

    fun analyzeComponents(
        activities: List<String>,
        services: List<String>,
        receivers: List<String>,
        providers: List<String>,
        manifestRaw: String = ""
    ): Pair<List<ExportedComponentInfo>, List<SecurityFinding>> {
        val components = mutableListOf<ExportedComponentInfo>()
        val findings = mutableListOf<SecurityFinding>()

        // Analyze Activities
        for (activity in activities) {
            val isMain = activity.contains("MainActivity", ignoreCase = true) || activity.contains("Splash", ignoreCase = true)
            val isExplicitlyExported = manifestRaw.contains("activity.*$activity.*android:exported=\"true\"".toRegex()) ||
                    (isMain && !manifestRaw.contains("android:exported=\"false\""))
            val permission = if (manifestRaw.contains("<activity[^>]*$activity[^>]*android:permission=\"([^\"]+)\"".toRegex())) {
                "<activity[^>]*$activity[^>]*android:permission=\"([^\"]+)\"".toRegex().find(manifestRaw)?.groupValues?.get(1)
            } else null

            val risk = when {
                isMain -> SecuritySeverity.INFO
                isExplicitlyExported && permission == null -> SecuritySeverity.MEDIUM
                isExplicitlyExported && permission != null -> SecuritySeverity.LOW
                else -> SecuritySeverity.INFO
            }

            val recommendation = if (risk == SecuritySeverity.MEDIUM) {
                "Set android:exported=\"false\" if not intended for external apps, or guard with custom android:permission."
            } else {
                "Standard entry point component."
            }

            val info = ExportedComponentInfo(
                componentName = activity,
                type = ComponentType.ACTIVITY,
                isExported = isExplicitlyExported,
                permissionProtection = permission,
                hasIntentFilter = isMain || isExplicitlyExported,
                riskLevel = risk,
                recommendation = recommendation
            )
            components.add(info)

            if (risk == SecuritySeverity.MEDIUM) {
                findings.add(
                    SecurityFinding(
                        category = FindingCategory.EXPORTED_COMPONENTS,
                        severity = risk,
                        title = "Exported Activity without Permission: ${activity.substringAfterLast(".")}",
                        description = "Activity '$activity' is exported and accessible by any third-party app without permission protection.",
                        evidence = "Exported: true, Permission: none",
                        recommendation = recommendation,
                        module = "ComponentSecurity",
                        affectedFile = "AndroidManifest.xml",
                        status = FindingStatus.OPEN
                    )
                )
            }
        }

        // Analyze Services
        for (service in services) {
            val isExported = manifestRaw.contains("service.*$service.*android:exported=\"true\"".toRegex())
            val permission = "<service[^>]*$service[^>]*android:permission=\"([^\"]+)\"".toRegex().find(manifestRaw)?.groupValues?.get(1)

            val risk = if (isExported && permission == null) SecuritySeverity.HIGH else SecuritySeverity.INFO
            val recommendation = if (risk == SecuritySeverity.HIGH) {
                "Exported services can be invoked by arbitrary applications. Enforce signature-level permissions or set android:exported=\"false\"."
            } else {
                "Internal service component."
            }

            components.add(
                ExportedComponentInfo(
                    componentName = service,
                    type = ComponentType.SERVICE,
                    isExported = isExported,
                    permissionProtection = permission,
                    hasIntentFilter = isExported,
                    riskLevel = risk,
                    recommendation = recommendation
                )
            )

            if (risk == SecuritySeverity.HIGH) {
                findings.add(
                    SecurityFinding(
                        category = FindingCategory.EXPORTED_COMPONENTS,
                        severity = risk,
                        title = "Unprotected Exported Service: ${service.substringAfterLast(".")}",
                        description = "Service '$service' can be bound or started by malicious third-party apps.",
                        evidence = "Exported: true, Permission: null",
                        recommendation = recommendation,
                        module = "ComponentSecurity",
                        affectedFile = "AndroidManifest.xml",
                        status = FindingStatus.OPEN
                    )
                )
            }
        }

        // Analyze Broadcast Receivers
        for (receiver in receivers) {
            val isExported = manifestRaw.contains("receiver.*$receiver.*android:exported=\"true\"".toRegex())
            val risk = if (isExported) SecuritySeverity.MEDIUM else SecuritySeverity.INFO
            val recommendation = if (isExported) {
                "Verify intent filter actions and validate caller identity before processing incoming broadcasts."
            } else {
                "Internal broadcast receiver."
            }

            components.add(
                ExportedComponentInfo(
                    componentName = receiver,
                    type = ComponentType.RECEIVER,
                    isExported = isExported,
                    permissionProtection = null,
                    hasIntentFilter = isExported,
                    riskLevel = risk,
                    recommendation = recommendation
                )
            )

            if (risk == SecuritySeverity.MEDIUM) {
                findings.add(
                    SecurityFinding(
                        category = FindingCategory.EXPORTED_COMPONENTS,
                        severity = risk,
                        title = "Exported Broadcast Receiver: ${receiver.substringAfterLast(".")}",
                        description = "Receiver '$receiver' can receive broadcast intents from external apps.",
                        evidence = "Exported: true",
                        recommendation = recommendation,
                        module = "ComponentSecurity",
                        affectedFile = "AndroidManifest.xml",
                        status = FindingStatus.OPEN
                    )
                )
            }
        }

        // Analyze Content Providers
        for (provider in providers) {
            val isExported = manifestRaw.contains("provider.*$provider.*android:exported=\"true\"".toRegex())
            val risk = if (isExported) SecuritySeverity.HIGH else SecuritySeverity.INFO
            val recommendation = if (isExported) {
                "Unprotected ContentProviders expose internal databases or files. Enforce readPermission/writePermission or set android:exported=\"false\"."
            } else {
                "Protected content provider."
            }

            components.add(
                ExportedComponentInfo(
                    componentName = provider,
                    type = ComponentType.PROVIDER,
                    isExported = isExported,
                    permissionProtection = null,
                    hasIntentFilter = false,
                    riskLevel = risk,
                    recommendation = recommendation
                )
            )

            if (risk == SecuritySeverity.HIGH) {
                findings.add(
                    SecurityFinding(
                        category = FindingCategory.EXPORTED_COMPONENTS,
                        severity = risk,
                        title = "Exported Content Provider: ${provider.substringAfterLast(".")}",
                        description = "Provider '$provider' may allow unauthenticated query or insert operations.",
                        evidence = "Exported: true",
                        recommendation = recommendation,
                        module = "ComponentSecurity",
                        affectedFile = "AndroidManifest.xml",
                        status = FindingStatus.OPEN
                    )
                )
            }
        }

        return Pair(components, findings)
    }
}
