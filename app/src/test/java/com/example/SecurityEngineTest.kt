package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.inference.InferenceService
import com.example.apk.signing.SigningManager
import com.example.core.CryptoUtils
import com.example.diagnostics.engine.AuditLogService
import com.example.security.dependencies.DependencyFreshness
import com.example.security.dependencies.DependencySecurityAnalyzer
import com.example.security.diana.DianaSecurityAnalysisType
import com.example.security.diana.DianaSecurityAnalyzer
import com.example.security.engine.SecurityEngine
import com.example.security.integrity.ArtifactHasher
import com.example.security.integrity.ChecksumMatchResult
import com.example.security.integrity.SupplyChainIntegrityEngine
import com.example.security.model.*
import com.example.security.scanner.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SecurityEngineTest {

    private lateinit var context: Context
    private lateinit var signingManager: SigningManager
    private lateinit var inferenceService: InferenceService
    private lateinit var auditLogService: AuditLogService
    private lateinit var securityEngine: SecurityEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        signingManager = SigningManager(context)
        inferenceService = InferenceService(context)
        auditLogService = AuditLogService()
        securityEngine = SecurityEngine(context, signingManager, inferenceService, auditLogService)
    }

    @Test
    fun testSecretScanner_DetectsHardcodedSecrets() {
        val scanner = SecretScanner()
        val textWithSecrets = """
            val awsKey = "AKIAIOSFODNN7EXAMPLE"
            val googleKey = "AIzaSyD-1234567890abcdefghijklmno"
            val ghToken = "ghp_1234567890abcdefghijklmnopqrstuvwxyz"
            val safeText = "regular string without secrets"
        """.trimIndent()

        val findings = scanner.scanContent(textWithSecrets, "SampleCode.kt")
        assertTrue("Should detect at least 3 secrets", findings.size >= 3)
        assertTrue("Should detect AWS Access Key", findings.any { it.title.contains("AWS", ignoreCase = true) })
        assertTrue("Should detect Google API Key", findings.any { it.title.contains("Google", ignoreCase = true) })
        assertTrue("Should detect GitHub Token", findings.any { it.title.contains("GitHub", ignoreCase = true) })
    }

    @Test
    fun testAPKPermissionAnalyzer_IdentifiesDangerousAndNormalPermissions() {
        val analyzer = APKPermissionAnalyzer()
        val requestedPerms = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.BIND_ACCESSIBILITY_SERVICE"
        )

        val (results, findings) = analyzer.analyzePermissions(requestedPerms)
        assertTrue("Results must contain analyzed permissions", results.isNotEmpty())
        assertTrue("Dangerous permissions should generate findings", findings.isNotEmpty())
        assertTrue("High risk permission like BIND_ACCESSIBILITY_SERVICE should generate HIGH severity finding",
            findings.any { it.severity == SecuritySeverity.HIGH })
    }

    @Test
    fun testExportedComponentAnalyzer_DetectsVulnerableExportedComponents() {
        val analyzer = ExportedComponentAnalyzer()
        val manifestXml = """
            <manifest package="com.example.test">
                <application>
                    <activity android:name="com.example.test.MainActivity" android:exported="true" />
                    <service android:name="com.example.test.VulnerableService" android:exported="true" />
                    <receiver android:name="com.example.test.ProtectedReceiver" android:exported="true" android:permission="com.example.PERM" />
                    <provider android:name="com.example.test.VulnerableProvider" android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()

        val (components, findings) = analyzer.analyzeComponents(
            activities = listOf("com.example.test.MainActivity"),
            services = listOf("com.example.test.VulnerableService"),
            receivers = listOf("com.example.test.ProtectedReceiver"),
            providers = listOf("com.example.test.VulnerableProvider"),
            manifestRaw = manifestXml
        )

        assertTrue("Should evaluate components", components.isNotEmpty())
        assertTrue("Should generate findings for unprotected exported service or provider", findings.isNotEmpty())
        assertTrue("Should flag VulnerableService or VulnerableProvider",
            findings.any { it.title.contains("Service", ignoreCase = true) || it.title.contains("Provider", ignoreCase = true) })
    }

    @Test
    fun testNetworkSecurityAnalyzer_DetectsCleartextTraffic() {
        val analyzer = NetworkSecurityAnalyzer()
        val badManifest = "<manifest><application android:usesCleartextTraffic=\"true\" /></manifest>"
        val badNetworkConfig = """
            <network-security-config>
                <base-config>
                    <trust-anchors>
                        <certificates src="user" />
                    </trust-anchors>
                </base-config>
            </network-security-config>
        """.trimIndent()

        val (configInfo, findings) = analyzer.analyzeNetworkSecurity(badManifest, badNetworkConfig)
        assertTrue("ConfigInfo should reflect cleartext settings", configInfo.allowsCleartext)
        assertTrue("ConfigInfo should detect custom certificates", configInfo.customCertificatesAllowed)
        assertTrue("Should detect cleartext traffic permitted", findings.any { it.title.contains("Cleartext", ignoreCase = true) })
        assertTrue("Should detect user certificates trusted", findings.any { it.title.contains("User", ignoreCase = true) })
    }

    @Test
    fun testWebViewSecurityAnalyzer_DetectsInsecureWebViewSettings() {
        val analyzer = WebViewSecurityAnalyzer()
        val codeSnippet = """
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.allowFileAccess = true
            webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")
        """.trimIndent()

        val findings = analyzer.analyzeWebViewUsage(codeSnippet, "CustomWebViewActivity.kt")
        assertTrue("Should detect multiple webview risks", findings.size >= 2)
        assertTrue("Should detect JavaScript execution or Bridge",
            findings.any { it.title.contains("JavaScript", ignoreCase = true) })
        assertTrue("Should detect Unrestricted File Access",
            findings.any { it.title.contains("File Access", ignoreCase = true) })
    }

    @Test
    fun testCryptographyAnalyzer_DetectsWeakAlgorithms() {
        val analyzer = CryptographyAnalyzer()
        val cryptoCode = """
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val rand = java.util.Random()
            val token = rand.nextInt().toString()
        """.trimIndent()

        val findings = analyzer.analyzeCryptography(cryptoCode, "CryptoUtils.kt")
        assertTrue("Should detect weak crypto findings", findings.size >= 3)
        assertTrue("Should detect ECB mode", findings.any { it.title.contains("ECB", ignoreCase = true) })
        assertTrue("Should detect MD5/SHA-1", findings.any { it.title.contains("MD5", ignoreCase = true) || it.title.contains("SHA-1", ignoreCase = true) })
    }

    @Test
    fun testArtifactHasher_ComputesHashesAndMatches() {
        val hasher = ArtifactHasher()
        val tempFile = File(context.cacheDir, "test_artifact.bin")
        tempFile.writeBytes("EFRAIM-SECURE-PAYLOAD-2026".toByteArray())

        val record = hasher.computeAndRecordHash(tempFile, "1.0")
        assertNotNull(record.sha256)
        assertTrue("SHA256 must be 64 hex chars", record.sha256.length == 64)

        val tamperResult = hasher.verifyArtifactIntegrity(tempFile)
        assertEquals("Checksum verification should pass", ChecksumMatchResult.MATCH, tamperResult.result)
        tempFile.delete()
    }

    @Test
    fun testDependencySecurityAnalyzer_IdentifiesVulnerabilities() = runBlocking {
        val analyzer = DependencySecurityAnalyzer()
        val buildGradleContent = """
            dependencies {
                implementation("com.squareup.okhttp3:okhttp:3.12.0")
                implementation("androidx.core:core-ktx:1.9.0")
            }
        """.trimIndent()

        val (deps, findings) = analyzer.analyzeGradleDependencies(buildGradleContent)
        assertTrue("Should parse dependencies", deps.isNotEmpty())
        assertTrue("Should find outdated dependencies", deps.any { it.freshness == DependencyFreshness.OUTDATED })
        assertTrue("Should generate findings for outdated dependencies", findings.isNotEmpty())
    }

    @Test
    fun testSupplyChainIntegrityEngine_BuildProvenanceGeneration() {
        val engine = SupplyChainIntegrityEngine()
        val provenance = engine.generateProvenance(
            commitSha = "abcdef1234567890",
            version = "1.0.0"
        )

        assertNotNull(provenance)
        assertEquals("abcdef1234567890", provenance.commitSha)
        assertEquals("1.0.0", provenance.version)
        assertTrue("Dependencies hash must be populated", provenance.dependenciesHash.isNotBlank())
    }

    @Test
    fun testSecurityEngine_FullScanAndScoreCalculation() = runBlocking {
        val report = securityEngine.runFullSecurityScan(
            targetApkFile = null,
            apkInfo = null,
            projectDir = null
        )

        assertNotNull(report)
        assertNotNull(securityEngine.currentReport.value)
        val score = securityEngine.securityScore.value.numericScore
        assertNotNull("Numeric score should be computed", score)
        assertTrue("Numeric score should be in 0..100 range", score!! in 0..100)
    }

    @Test
    fun testDianaSecurityAnalyzer_AIThreatModelAndPlanGeneration() = runBlocking {
        val diana = securityEngine.dianaSecurityAnalyzer
        val finding = SecurityFinding(
            id = "FIND-001",
            category = FindingCategory.SECRETS,
            severity = SecuritySeverity.CRITICAL,
            title = "Exposed Hardcoded GitHub Token",
            description = "Token found in MainActivity.kt",
            evidence = "ghp_1234567890abcdefghijklmnopqrstuvwxyz",
            recommendation = "Move token to secrets manager",
            affectedFile = "MainActivity.kt"
        )

        val report = diana.analyzeSecurityFindings(
            type = DianaSecurityAnalysisType.SECRET_ANALYSIS,
            findings = listOf(finding)
        )

        assertNotNull(report)
        assertTrue("Summary must be provided", report.summary.isNotBlank())
        assertTrue("Remediation steps must be generated", report.remediationPlan.isNotBlank())
        assertNotNull("Suggested change plan should be generated for critical finding", report.proposedChangePlan)
        assertEquals("ChangePlan must start in PENDING state",
            com.example.release.model.ChangePlanStatus.PENDING,
            report.proposedChangePlan?.status)
    }

    @Test
    fun testSecurityEngine_ExportSecurityReport() = runBlocking {
        securityEngine.runFullSecurityScan(null, null, null)
        val report = securityEngine.currentReport.value
        assertNotNull(report)

        val jsonExport = report!!.toJson()
        assertTrue("JSON export must be non-empty", jsonExport.isNotBlank())
        assertTrue("JSON export must contain project name", jsonExport.contains("EFRAIM"))

        val textExport = report.toFormattedText()
        assertTrue("Text export must be non-empty", textExport.isNotBlank())
        assertTrue("Text export should format header", textExport.contains("SECURITY") && textExport.contains("EFRAIM"))
    }
}
