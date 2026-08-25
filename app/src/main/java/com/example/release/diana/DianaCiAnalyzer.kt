package com.example.release.diana

import com.example.release.model.ChangePlan
import com.example.release.model.ChangePlanStatus
import com.example.release.model.CiErrorCategory
import com.example.release.model.CiFailureRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DianaCiAnalysisResult(
    val rootCause: String,
    val severity: String,
    val suggestedFix: String,
    val proposedChangePlan: ChangePlan?
)

class DianaCiAnalyzer {

    suspend fun analyzeCiFailure(failure: CiFailureRecord): DianaCiAnalysisResult = withContext(Dispatchers.Default) {
        val snippet = failure.logSnippet.lowercase()
        val summary = failure.summary.lowercase()

        when (failure.category) {
            CiErrorCategory.SIGNING_ERROR -> {
                val plan = ChangePlan(
                    title = "Configure Release Signing Secrets",
                    description = "Add KEYSTORE_BASE64 and STORE_PASSWORD in GitHub Repository Actions Secrets to enable production APK signing.",
                    affectedFiles = listOf(".github/workflows/android-release.yml", "app/build.gradle.kts"),
                    diffPreview = "+ KEYSTORE_BASE64: \${{ secrets.KEYSTORE_BASE64 }}\n+ STORE_PASSWORD: \${{ secrets.STORE_PASSWORD }}",
                    proposedAction = "Inject base64 encoded upload key into environment during CI build step."
                )
                DianaCiAnalysisResult(
                    rootCause = "Missing or unconfigured Android release keystore credentials in GitHub Actions secrets.",
                    severity = "CRITICAL",
                    suggestedFix = "Generate base64 upload key and set KEYSTORE_BASE64, STORE_PASSWORD in GitHub Actions secrets repository settings.",
                    proposedChangePlan = plan
                )
            }
            CiErrorCategory.TEST_ERROR -> {
                val plan = ChangePlan(
                    title = "Update Outdated Test Assertions",
                    description = "Synchronize unit and Robolectric test assertions with current app strings and verified state.",
                    affectedFiles = listOf("app/src/test/java/com/example/ExampleRobolectricTest.kt"),
                    diffPreview = "- assertEquals(\"Old Name\", appName)\n+ assertEquals(\"EFRAIM APK WORKBENCH TOOL M\", appName)",
                    proposedAction = "Fix test expectations to match official EFRAIM APK WORKBENCH product identity."
                )
                DianaCiAnalysisResult(
                    rootCause = "Unit test or Roborazzi screenshot verification failed due to expected value discrepancy.",
                    severity = "HIGH",
                    suggestedFix = "Update test assertions to match current application configuration or update screenshot reference baseline.",
                    proposedChangePlan = plan
                )
            }
            CiErrorCategory.BUILD_ERROR -> {
                val plan = ChangePlan(
                    title = "Align Gradle & AGP Toolchain Versions",
                    description = "Ensure JDK 21 and Gradle 8.11+ compatibility in CI environment.",
                    affectedFiles = listOf("gradle/libs.versions.toml", ".github/workflows/android-release.yml"),
                    diffPreview = "+ distribution: 'temurin'\n+ java-version: '21'",
                    proposedAction = "Pin Java 21 LTS in GitHub Actions setup-java step."
                )
                DianaCiAnalysisResult(
                    rootCause = "Gradle compilation or Android Gradle Plugin resource processing error during native packaging.",
                    severity = "CRITICAL",
                    suggestedFix = "Verify AGP 9.1.1 toolchain requirements and ensure Temurin JDK 21 is selected.",
                    proposedChangePlan = plan
                )
            }
            CiErrorCategory.CONFIGURATION_ERROR -> {
                val plan = ChangePlan(
                    title = "Synchronize Package Application ID",
                    description = "Keep application ID com.aistudio.apkworkbench.wb7x consistent across build.gradle.kts and CI validator.",
                    affectedFiles = listOf("app/build.gradle.kts"),
                    diffPreview = "  applicationId = \"com.aistudio.apkworkbench.wb7x\"",
                    proposedAction = "Verify app/build.gradle.kts defaultConfig matches CI workflow expectations."
                )
                DianaCiAnalysisResult(
                    rootCause = "Discrepancy detected between project application ID and CI expected release identity.",
                    severity = "MEDIUM",
                    suggestedFix = "Review and sync applicationId in app/build.gradle.kts.",
                    proposedChangePlan = plan
                )
            }
            CiErrorCategory.ARTIFACT_ERROR -> {
                val plan = ChangePlan(
                    title = "Correct Release Output Path in CI Workflow",
                    description = "Locate output APK at app/build/outputs/apk/release/*.apk.",
                    affectedFiles = listOf(".github/workflows/android-release.yml"),
                    diffPreview = "+ find app/build/outputs/apk -name \"*.apk\"",
                    proposedAction = "Ensure build output search path captures the compiled APK artifact."
                )
                DianaCiAnalysisResult(
                    rootCause = "The compiled APK artifact was not located in the standard build output directory.",
                    severity = "HIGH",
                    suggestedFix = "Check Gradle build outputs directory structure and verify artifact packaging step.",
                    proposedChangePlan = plan
                )
            }
            CiErrorCategory.RELEASE_ERROR -> {
                val plan = ChangePlan(
                    title = "Grant Contents: Write Permission in CI Workflow",
                    description = "Ensure GITHUB_TOKEN has write permission to publish releases on tag push.",
                    affectedFiles = listOf(".github/workflows/android-release.yml"),
                    diffPreview = "permissions:\n  contents: write\n  actions: read",
                    proposedAction = "Set permissions: contents: write in GitHub Actions workflow definition."
                )
                DianaCiAnalysisResult(
                    rootCause = "GitHub Release publication failed due to insufficient GITHUB_TOKEN permissions or tag collision.",
                    severity = "CRITICAL",
                    suggestedFix = "Grant contents: write permission in repository workflow settings.",
                    proposedChangePlan = plan
                )
            }
        }
    }
}
