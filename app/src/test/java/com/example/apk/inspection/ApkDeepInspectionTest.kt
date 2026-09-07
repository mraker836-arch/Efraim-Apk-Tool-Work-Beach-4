package com.example.apk.inspection

import com.example.apk.export.ScanReportExporter
import com.example.apk.model.*
import com.example.security.model.FindingCategory
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ApkDeepInspectionTest {

    @Test
    fun testSecurityScoreCalculator_CleanState() {
        val breakdown = ApkSecurityScoreCalculator.calculateScore(emptyList())
        assertEquals(100, breakdown.score)
        assertEquals("A+", breakdown.grade)
        assertTrue(breakdown.isClean)
        assertEquals(0, breakdown.criticalCount)
        assertEquals(0, breakdown.highCount)
        assertEquals(0, breakdown.mediumCount)
        assertEquals(0, breakdown.lowCount)
        assertEquals(0, breakdown.infoCount)
        assertEquals(0, breakdown.totalFindings)
    }

    @Test
    fun testSecurityScoreCalculator_WithFindings() {
        val findings = listOf(
            SecurityFinding("1", FindingCategory.EXPORTED_COMPONENTS, SecuritySeverity.CRITICAL, "Debuggable App", "App is debuggable", "android:debuggable=\"true\"", "Set to false", "AndroidManifest.xml"),
            SecurityFinding("2", FindingCategory.APK_PERMISSIONS, SecuritySeverity.HIGH, "SMS Permission", "Sends SMS", "android.permission.SEND_SMS", "Remove if unused", "AndroidManifest.xml"),
            SecurityFinding("3", FindingCategory.NETWORK_SECURITY, SecuritySeverity.MEDIUM, "Cleartext Traffic", "Cleartext HTTP allowed", "usesCleartextTraffic=\"true\"", "Enforce HTTPS", "AndroidManifest.xml"),
            SecurityFinding("4", FindingCategory.CRYPTOGRAPHY, SecuritySeverity.LOW, "Weak Algorithm", "SHA1 in cert", "SHA1withRSA", "Upgrade to SHA256", "META-INF/CERT.RSA"),
            SecurityFinding("5", FindingCategory.SOURCE_INTEGRITY, SecuritySeverity.INFO, "Backup Info", "Backup enabled", "allowBackup=\"true\"", "Review data rules", "AndroidManifest.xml")
        )

        val breakdown = ApkSecurityScoreCalculator.calculateScore(findings)
        // 100 - 25 - 15 - 8 - 3 - 0 = 49
        assertEquals(49, breakdown.score)
        assertEquals("F", breakdown.grade) // Critical > 0 gives F
        assertFalse(breakdown.isClean)
        assertEquals(1, breakdown.criticalCount)
        assertEquals(1, breakdown.highCount)
        assertEquals(1, breakdown.mediumCount)
        assertEquals(1, breakdown.lowCount)
        assertEquals(1, breakdown.infoCount)
        assertEquals(5, breakdown.totalFindings)
    }

    @Test
    fun testInspectionSearchEngine_MatchesCorrectFields() {
        val scanResult = ApkScanResult(
            scanId = "test-scan-1",
            fileInfo = ApkFileInfo(
                scanId = "test-scan-1",
                fileName = "testapp.apk",
                fileSize = 1048576L,
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                md5 = "d41d8cd98f00b204e9800998ecf8427e",
                mimeType = "application/vnd.android.package-archive",
                uri = "/tmp/test.apk",
                totalZipEntries = 42,
                dexCount = 2,
                nativeLibraryCount = 1
            ),
            manifestInfo = ManifestInfo(
                packageName = "com.example.bankapp",
                appName = "Bank Mobile",
                versionCode = 100,
                versionName = "1.0.0",
                minSdk = 26,
                targetSdk = 34,
                compileSdk = 34,
                permissions = listOf("android.permission.INTERNET", "android.permission.CAMERA"),
                usesFeatures = listOf("android.hardware.camera"),
                activities = listOf(
                    ComponentInfo("MainActivity", "com.example.bankapp.MainActivity", "Activity", true, null, listOf("android.intent.action.MAIN"))
                ),
                services = listOf(
                    ComponentInfo("SyncService", "com.example.bankapp.SyncService", "Service", false, null, emptyList())
                ),
                receivers = emptyList(),
                providers = emptyList(),
                intentFiltersCount = 1,
                exportedComponentsCount = 1,
                isDebuggable = false,
                allowBackup = false,
                usesCleartextTraffic = false,
                networkSecurityConfig = null,
                theme = null,
                supportedArchitectures = listOf("arm64-v8a"),
                rawXmlText = ""
            ),
            dexList = listOf(
                DexInfo("classes.dex", 524288L, "sha256-dex1", "dex\n", "039", "adler123", "sha1-1", 120, 450, 200, 100, 50, 80, listOf("com.example.bankapp.CryptoUtil"), "Analyzed")
            ),
            permissionsList = listOf(
                ScanPermissionInfo("android.permission.CAMERA", "CAMERA", ProtectionCategory.DANGEROUS, RiskLevel.HIGH, "Hardware capture", "Access camera device")
            ),
            nativeLibrariesList = listOf(
                NativeLibraryInfo("libcrypto_native.so", "arm64-v8a", 262144L, "sha256-libcrypto")
            ),
            abiCoverage = listOf("arm64-v8a"),
            certificatesList = listOf(
                ScanCertificateInfo(
                    subject = "CN=Bank Corp",
                    issuer = "CN=Bank Corp",
                    serialNumber = "123456",
                    validFrom = "2024-01-01",
                    validUntil = "2044-01-01",
                    signatureAlgorithm = "SHA256withRSA",
                    publicKeyAlgorithm = "RSA",
                    sha256Fingerprint = "cert-sha256-hash",
                    sha1Fingerprint = "cert-sha1-hash",
                    md5Fingerprint = "",
                    status = CertificateStatus.CERTIFICATE_VERIFIED,
                    verificationDetails = "Valid",
                    isSelfSigned = true,
                    keySizeBits = 2048
                )
            ),
            securityFindings = listOf(
                SecurityFinding("F1", FindingCategory.EXPORTED_COMPONENTS, SecuritySeverity.HIGH, "Exported Activity Vulnerability", "MainActivity is exported without intent protection", "exported=true", "Add permission or set exported=false", "AndroidManifest.xml")
            ),
            status = ScanStatus.COMPLETED
        )

        val archiveEntries = listOf(
            ArchiveEntryDetail("AndroidManifest.xml", 1200L, 3500L, "Deflated (8)", false, 12345L),
            ArchiveEntryDetail("classes.dex", 500000L, 524288L, "Deflated (8)", false, 67890L),
            ArchiveEntryDetail("lib/arm64-v8a/libcrypto_native.so", 200000L, 262144L, "Deflated (8)", false, 11223L)
        )

        // Search for "crypto"
        val cryptoResults = ApkInspectionSearchEngine.search(scanResult, archiveEntries, "crypto")
        assertTrue("Should find DEX class containing Crypto", cryptoResults.matchingDex.isNotEmpty())
        assertTrue("Should find native lib containing crypto", cryptoResults.matchingNativeLibs.isNotEmpty())
        assertTrue("Should find archive entry containing crypto", cryptoResults.matchingArchiveEntries.isNotEmpty())
        assertTrue("Total matches should be >= 3", cryptoResults.totalMatches >= 3)

        // Search for "camera"
        val cameraResults = ApkInspectionSearchEngine.search(scanResult, archiveEntries, "CAMERA")
        assertTrue("Should find camera permission", cameraResults.matchingPermissions.isNotEmpty())

        // Search for "exported"
        val exportedResults = ApkInspectionSearchEngine.search(scanResult, archiveEntries, "exported")
        assertTrue("Should find exported component", exportedResults.matchingComponents.isNotEmpty())
        assertTrue("Should find exported finding", exportedResults.matchingFindings.isNotEmpty())
    }

    @Test
    fun testReportExport_IncludesArchiveAndSignatureIntegrity() {
        val scanResult = ApkScanResult(
            scanId = "scan-export-test",
            fileInfo = ApkFileInfo(
                scanId = "scan-export-test",
                fileName = "sample.apk",
                fileSize = 2048000L,
                sha256 = "abc123sha256",
                md5 = "abc123md5",
                mimeType = "application/vnd.android.package-archive",
                uri = "/path/sample.apk",
                totalZipEntries = 120,
                compressedSize = 1500000L,
                uncompressedSize = 2500000L,
                compressionRatio = 40.0,
                dexCount = 1,
                nativeLibraryCount = 0,
                signingRelatedFiles = listOf("META-INF/MANIFEST.MF", "META-INF/CERT.SF", "META-INF/CERT.RSA")
            ),
            manifestInfo = ManifestInfo(
                packageName = "com.sample.test",
                appName = "Sample Test",
                versionCode = 1,
                versionName = "1.0",
                minSdk = 24,
                targetSdk = 33,
                compileSdk = 33,
                permissions = emptyList(),
                usesFeatures = emptyList(),
                activities = emptyList(),
                services = emptyList(),
                receivers = emptyList(),
                providers = emptyList(),
                intentFiltersCount = 0,
                exportedComponentsCount = 0,
                isDebuggable = false,
                allowBackup = false,
                usesCleartextTraffic = false,
                networkSecurityConfig = null,
                theme = null,
                supportedArchitectures = emptyList(),
                rawXmlText = "",
                dataExtractionRules = "data_extraction_rules.xml",
                allowClearUserData = true,
                requestLegacyExternalStorage = false
            ),
            dexList = listOf(
                DexInfo("classes.dex", 1024000L, "dexsha256", "dex\n", "038", "adler789", "sha1", 50, 150, 80, 40, 20, 30, emptyList(), "Semantic OK", 4096L)
            ),
            permissionsList = emptyList(),
            nativeLibrariesList = emptyList(),
            abiCoverage = emptyList(),
            certificatesList = listOf(
                ScanCertificateInfo(
                    subject = "CN=Sample",
                    issuer = "CN=Sample",
                    serialNumber = "999",
                    validFrom = "2023-01-01",
                    validUntil = "2033-01-01",
                    signatureAlgorithm = "SHA256withRSA",
                    publicKeyAlgorithm = "RSA",
                    sha256Fingerprint = "certsha256",
                    sha1Fingerprint = "certsha1",
                    md5Fingerprint = "",
                    status = CertificateStatus.CERTIFICATE_VERIFIED,
                    verificationDetails = "Valid cert",
                    isSelfSigned = true,
                    keySizeBits = 2048
                )
            ),
            securityFindings = listOf(
                SecurityFinding("F-01", FindingCategory.EXPORTED_COMPONENTS, SecuritySeverity.MEDIUM, "Storage Rule Test", "Custom rules defined", "dataExtractionRules present", "Verify rules", "AndroidManifest.xml")
            ),
            status = ScanStatus.COMPLETED
        )

        // 1. Plain text report
        val txtReport = ScanReportExporter.generatePlainTextReport(scanResult)
        assertTrue(txtReport.contains("ARCHIVE SUMMARY & STRUCTURAL INTEGRITY"))
        assertTrue(txtReport.contains("Total Entries:       120"))
        assertTrue(txtReport.contains("Compression Ratio:   40.0%"))
        assertTrue(txtReport.contains("Advanced APK signature verification unavailable"))
        assertTrue(txtReport.contains("Data Extraction:     data_extraction_rules.xml"))
        assertTrue(txtReport.contains("DETERMINISTIC SECURITY FINDINGS"))

        // 2. JSON report
        val jsonReport = ScanReportExporter.generateJsonReport(scanResult)
        assertTrue(jsonReport.contains("\"archiveSummary\""))
        assertTrue(jsonReport.contains("\"signatureInfo\""))
        assertTrue(jsonReport.contains("Advanced APK signature verification unavailable"))
        assertTrue(jsonReport.contains("\"dataExtractionRules\": \"data_extraction_rules.xml\""))

        // 3. JSON round-trip reconstruction
        val reconstructed = ScanReportExporter.parseJsonToScanResult(jsonReport)
        assertNotNull(reconstructed)
        assertEquals("com.sample.test", reconstructed!!.manifestInfo.packageName)
        assertEquals("data_extraction_rules.xml", reconstructed.manifestInfo.dataExtractionRules)
        assertEquals(true, reconstructed.manifestInfo.allowClearUserData)
        assertEquals(false, reconstructed.manifestInfo.requestLegacyExternalStorage)
        assertEquals(1, reconstructed.securityFindings.size)
        assertEquals("Storage Rule Test", reconstructed.securityFindings[0].title)
    }
}
