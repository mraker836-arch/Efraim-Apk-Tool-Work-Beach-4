package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.LinearProgressIndicator
import com.example.apk.model.APKInfo
import com.example.apk.model.ApkScanResult
import com.example.apk.model.BuildStatus
import com.example.apk.model.CertificateStatus
import com.example.apk.model.CustomAssetItem
import com.example.apk.model.ProtectionCategory
import com.example.apk.model.RebuildConfig
import com.example.apk.model.RiskLevel
import com.example.apk.model.ScanStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import com.example.ui.ApkLabSubTab
import com.example.ui.WorkbenchViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanDark
import com.example.ui.theme.CyberCyanLight
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ApkLabScreen(
    viewModel: WorkbenchViewModel,
    onImportApkClick: () -> Unit
) {
    val currentApk by viewModel.currentApk.collectAsState()
    val subTab by viewModel.apkLabSubTab.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentScanResult by viewModel.currentScanResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sub-tabs navigation bar
        val subTabs = ApkLabSubTab.values()
        ScrollableTabRow(
            selectedTabIndex = subTab.ordinal,
            containerColor = SlateSurfaceCard,
            contentColor = CyberCyan,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[subTab.ordinal]),
                    color = CyberCyan,
                    height = 3.dp
                )
            }
        ) {
            subTabs.forEach { tab ->
                Tab(
                    selected = subTab == tab,
                    onClick = { viewModel.selectApkLabSubTab(tab) },
                    text = {
                        Text(
                            text = tab.name.replace("_", " "),
                            fontSize = 12.sp,
                            fontWeight = if (subTab == tab) FontWeight.Bold else FontWeight.Normal,
                            color = if (subTab == tab) CyberCyan else TextSecondary
                        )
                    }
                )
            }
        }

        when (subTab) {
            ApkLabSubTab.UPLOAD -> {
                ApkUploadTab(viewModel, onImportApkClick)
            }
            ApkLabSubTab.OVERVIEW -> {
                if (currentApk == null) {
                    ApkEmptyWorkspaceCard(onImportApkClick, onLoadSample = { viewModel.loadSampleApk() })
                } else {
                    ApkOverviewTab(apk = currentApk!!, scanResult = currentScanResult, onImportClick = onImportApkClick, onLoadSample = { viewModel.loadSampleApk() }, onNavigateToFindings = { viewModel.selectApkLabSubTab(ApkLabSubTab.SECURITY_FINDINGS) })
                }
            }
            ApkLabSubTab.INSPECT -> {
                if (currentApk == null) {
                    ApkEmptyWorkspaceCard(onImportApkClick, onLoadSample = { viewModel.loadSampleApk() })
                } else {
                    ApkInspectTab(apk = currentApk!!, scanResult = currentScanResult)
                }
            }
            ApkLabSubTab.SECURITY_FINDINGS -> {
                if (currentApk == null && currentScanResult == null) {
                    ApkEmptyWorkspaceCard(onImportApkClick, onLoadSample = { viewModel.loadSampleApk() })
                } else {
                    ApkSecurityFindingsTab(currentScanResult)
                }
            }
            ApkLabSubTab.DIANA_ANALYSIS -> {
                if (currentApk == null) {
                    ApkEmptyWorkspaceCard(onImportApkClick, onLoadSample = { viewModel.loadSampleApk() })
                } else {
                    ApkDianaAnalysisTab(viewModel, currentApk!!)
                }
            }
            ApkLabSubTab.REBUILD -> {
                if (currentApk == null) {
                    ApkEmptyWorkspaceCard(onImportApkClick, onLoadSample = { viewModel.loadSampleApk() })
                } else {
                    ApkRebuildTab(viewModel, currentApk!!)
                }
            }
            ApkLabSubTab.SIGN_VERIFY -> {
                if (currentApk == null) {
                    ApkEmptyWorkspaceCard(onImportApkClick, onLoadSample = { viewModel.loadSampleApk() })
                } else {
                    ApkSignVerifyTab(viewModel, currentApk!!)
                }
            }
            ApkLabSubTab.EXPORT -> {
                if (currentApk == null) {
                    ApkEmptyWorkspaceCard(onImportApkClick, onLoadSample = { viewModel.loadSampleApk() })
                } else {
                    ApkExportTab(viewModel, currentApk!!)
                }
            }
        }
    }
}

@Composable
fun ApkEmptyWorkspaceCard(
    onImportApkClick: () -> Unit,
    onLoadSample: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No APK in Workspace", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Import an Android APK file to inspect its manifest, permissions, DEX, native libraries, certificates, and run deterministic security checks.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImportApkClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B))
                    ) {
                        Text("Select APK", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onLoadSample) {
                        Text("Load Sample APK")
                    }
                }
            }
        }
    }
}

@Composable
fun ApkOverviewTab(
    apk: APKInfo,
    scanResult: ApkScanResult? = null,
    onImportClick: () -> Unit,
    onLoadSample: () -> Unit,
    onNavigateToFindings: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // App header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(apk.appName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(apk.packageName, fontSize = 12.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (apk.signingStatus != com.example.apk.model.SigningStatus.UNSIGNED) NeonEmerald.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = apk.signingStatus.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (apk.signingStatus != com.example.apk.model.SigningStatus.UNSIGNED) NeonEmerald else AmberWarning,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InfoTag("Version", "v${apk.versionName} (${apk.versionCode})")
                        InfoTag("Min SDK", "API ${apk.minSdk}")
                        InfoTag("Target SDK", "API ${apk.targetSdk}")
                        InfoTag("File Size", formatBytes(apk.fileSize))
                    }

                    if (scanResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoTag("Archive Entries", "${scanResult.fileInfo.totalZipEntries}")
                            InfoTag("Asset Files", "${scanResult.fileInfo.assetCount}")
                            InfoTag("DEX Files", "${scanResult.dexList.size}")
                            InfoTag("ABIs", "${scanResult.nativeLibrariesList.map { it.abi }.distinct().size}")
                        }
                    }
                }
            }
        }

        // Security Findings Quick Banner (if any findings detected)
        if (scanResult != null && scanResult.securityFindings.isNotEmpty()) {
            val criticalCount = scanResult.securityFindings.count { it.severity == SecuritySeverity.CRITICAL }
            val highCount = scanResult.securityFindings.count { it.severity == SecuritySeverity.HIGH }
            val bannerColor = if (criticalCount > 0 || highCount > 0) CrimsonError else AmberWarning

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, bannerColor.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { onNavigateToFindings() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = bannerColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${scanResult.securityFindings.size} Security Findings Detected",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$criticalCount Critical, $highCount High severity items",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Button(
                            onClick = onNavigateToFindings,
                            colors = ButtonDefaults.buttonColors(containerColor = bannerColor, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("View Audit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Cryptographic Hashes Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CRYPTOGRAPHIC CHECKSUMS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    HashRow(label = "SHA-256", hash = scanResult?.fileInfo?.sha256 ?: apk.sha256)
                    Spacer(modifier = Modifier.height(6.dp))
                    HashRow(label = "MD5", hash = scanResult?.fileInfo?.md5 ?: apk.md5)
                }
            }
        }

        // Component tallies grid
        item {
            Text("STRUCTURE SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TallyCard("Activities", "${scanResult?.manifestInfo?.activities?.size ?: apk.activities.size}", modifier = Modifier.weight(1f))
                    TallyCard("Services", "${scanResult?.manifestInfo?.services?.size ?: apk.services.size}", modifier = Modifier.weight(1f))
                    TallyCard("Receivers", "${scanResult?.manifestInfo?.receivers?.size ?: apk.receivers.size}", modifier = Modifier.weight(1f))
                    TallyCard("Providers", "${scanResult?.manifestInfo?.providers?.size ?: apk.providers.size}", modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TallyCard("Permissions", "${scanResult?.permissionsList?.size ?: apk.permissions.size}", modifier = Modifier.weight(1f))
                    TallyCard("DEX Classes", "${scanResult?.dexList?.sumOf { it.classDefsCount } ?: apk.totalDexClasses}", modifier = Modifier.weight(1f))
                    TallyCard("Native ABIs", "${scanResult?.nativeLibrariesList?.map { it.abi }?.distinct()?.size ?: apk.supportedAbis.size}", modifier = Modifier.weight(1f))
                    TallyCard("Certificates", "${scanResult?.certificatesList?.size ?: apk.certificates.size}", modifier = Modifier.weight(1f))
                }
            }
        }

        // Import actions
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark, contentColor = CyberCyan)
                ) {
                    Text("Import Another APK")
                }
                OutlinedButton(
                    onClick = onLoadSample,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset Sample APK")
                }
            }
        }
    }
}

@Composable
fun ApkInspectTab(apk: APKInfo, scanResult: ApkScanResult? = null) {
    var inspectCategory by remember { mutableStateOf("Manifest") }
    val categories = listOf("Manifest", "Permissions", "Components", "DEX", "Native ABIs", "Assets", "Resources", "Certificates")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val selected = inspectCategory == cat
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) CyberCyan else SlateSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) CyberCyan else SlateOutline),
                    modifier = Modifier.testTag("inspect_filter_${cat.lowercase().replace(" ", "_")}")
                ) {
                    Text(
                        text = cat,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color(0xFF00363B) else TextPrimary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { inspectCategory = cat }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (inspectCategory) {
                "Manifest" -> {
                    item {
                        ManifestInspectSection(manifest = scanResult?.manifestInfo, apk = apk)
                    }
                }
                "Permissions" -> {
                    if (scanResult != null && scanResult.permissionsList.isNotEmpty()) {
                        items(scanResult.permissionsList) { perm ->
                            ScanPermissionItemCard(perm)
                        }
                    } else if (apk.permissions.isEmpty()) {
                        item { Text("No permissions declared in manifest.", color = TextMuted, fontSize = 13.sp) }
                    } else {
                        items(apk.permissions) { perm ->
                            PermissionItemCard(perm)
                        }
                    }
                }
                "Components" -> {
                    val allComponents = apk.activities + apk.services + apk.receivers + apk.providers
                    if (allComponents.isEmpty()) {
                        item { Text("No components found in manifest.", color = TextMuted, fontSize = 13.sp) }
                    } else {
                        items(allComponents) { comp ->
                            ComponentItemCard(comp)
                        }
                    }
                }
                "DEX" -> {
                    if (scanResult != null && scanResult.dexList.isNotEmpty()) {
                        items(scanResult.dexList) { dex ->
                            ScanDexItemCard(dex)
                        }
                    } else {
                        items(apk.dexFiles) { dex ->
                            DexItemCard(dex)
                        }
                    }
                }
                "Native ABIs" -> {
                    if (scanResult != null && scanResult.nativeLibrariesList.isNotEmpty()) {
                        val grouped = scanResult.nativeLibrariesList.groupBy { it.abi }
                        grouped.forEach { (abi, libs) ->
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(abi, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                            Text("${libs.size} libraries", fontSize = 11.sp, color = TextMuted)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        libs.forEach { lib ->
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("• ${lib.libraryName}", fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                                    Text(formatBytes(lib.fileSize), fontSize = 11.sp, color = TextSecondary)
                                                }
                                                Text("SHA-256: ${lib.sha256}", fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("ABI COVERAGE SUMMARY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val is64bit = grouped.keys.any { it.contains("64") }
                                    val is32bit = grouped.keys.any { !it.contains("64") }
                                    Text("64-bit Architecture: ${if (is64bit) "Supported" else "Missing"}", fontSize = 11.sp, color = TextPrimary)
                                    Text("32-bit Architecture: ${if (is32bit) "Supported" else "Missing"}", fontSize = 11.sp, color = TextPrimary)
                                }
                            }
                        }
                    } else if (apk.nativeLibraries.isEmpty()) {
                        item { Text("Pure Dalvik/ART Java app - no native .so shared libraries.", color = TextMuted, fontSize = 13.sp) }
                    } else {
                        apk.nativeLibraries.forEach { (abi, libs) ->
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(abi, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        libs.forEach { lib ->
                                            Text("• $lib", fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "Assets" -> {
                    if (apk.assets.isEmpty()) {
                        item { Text("No assets bundled in assets/ directory.", color = TextMuted, fontSize = 13.sp) }
                    } else {
                        items(apk.assets) { asset ->
                            ResourceItemRow(path = "assets/${asset.path}", size = asset.sizeBytes)
                        }
                    }
                }
                "Resources" -> {
                    items(apk.resources) { res ->
                        ResourceItemRow(path = res.path, size = res.sizeBytes)
                    }
                }
                "Certificates" -> {
                    if (scanResult != null && scanResult.certificatesList.isNotEmpty()) {
                        items(scanResult.certificatesList) { cert ->
                            ScanCertItemCard(cert)
                        }
                    } else if (apk.certificates.isEmpty()) {
                        item { Text("No valid X.509 signature block found.", color = AmberWarning, fontSize = 13.sp) }
                    } else {
                        items(apk.certificates) { cert ->
                            CertItemCard(cert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApkDianaAnalysisTab(viewModel: WorkbenchViewModel, apk: APKInfo) {
    val report by viewModel.dianaReport.collectAsState()

    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { viewModel.runDianaAnalysis(apk) }) {
                Text("Run DIANA Security & Architecture Audit")
            }
        }
        return
    }

    val rep = report!!

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Score Header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    rep.overallSecurityScore >= 80 -> NeonEmerald.copy(alpha = 0.2f)
                                    rep.overallSecurityScore >= 50 -> AmberWarning.copy(alpha = 0.2f)
                                    else -> CrimsonError.copy(alpha = 0.2f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${rep.overallSecurityScore}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                rep.overallSecurityScore >= 80 -> NeonEmerald
                                rep.overallSecurityScore >= 50 -> AmberWarning
                                else -> CrimsonError
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text("DIANA Security Rating", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            text = if (rep.overallSecurityScore >= 80) "Optimal Security Posture" else "Recommendations Detected",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Overview
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ARCHITECTURE AUDIT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(rep.apkOverview, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(rep.architectureSummary, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                }
            }
        }

        // Findings
        item {
            Text("SECURITY FINDINGS (${rep.findings.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
        }

        items(rep.findings) { finding ->
            FindingCard(finding)
        }

        // Recommendations
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("REBUILD RECOMMENDATIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    rep.rebuildRecommendations.forEach { rec ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("• ", color = NeonEmerald, fontWeight = FontWeight.Bold)
                            Text(rec, fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkRebuildTab(viewModel: WorkbenchViewModel, apk: APKInfo) {
    var newAppName by remember(apk) { mutableStateOf(apk.appName) }
    var newVersionName by remember(apk) { mutableStateOf(apk.versionName) }
    var newVersionCode by remember(apk) { mutableStateOf(apk.versionCode.toString()) }
    var newMinSdk by remember(apk) { mutableStateOf(apk.minSdk.toString()) }
    var newTargetSdk by remember(apk) { mutableStateOf(apk.targetSdk.toString()) }

    var customAssetPath by remember { mutableStateOf("config/overrides.json") }
    var customAssetContent by remember { mutableStateOf("{\"environment\":\"custom_rebuild\",\"debug\":false}") }

    val keysList by viewModel.keysList.collectAsState()
    val selectedKey by viewModel.selectedKeyAlias.collectAsState()

    val rebuildStatus by viewModel.rebuildStatus.collectAsState()
    val rebuildLogs by viewModel.rebuildLogs.collectAsState()
    val rebuildResult by viewModel.rebuildResult.collectAsState()

    val isRunning = rebuildStatus != BuildStatus.IDLE && rebuildStatus != BuildStatus.COMPLETED && rebuildStatus != BuildStatus.FAILED

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("REBUILD CONFIGURATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
        }

        // Manifest attributes form
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newAppName,
                        onValueChange = { newAppName = it },
                        label = { Text("Application Label (android:label)") },
                        modifier = Modifier.fillMaxWidth().testTag("rebuild_app_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SlateOutline
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newVersionName,
                            onValueChange = { newVersionName = it },
                            label = { Text("Version Name") },
                            modifier = Modifier.weight(1f).testTag("rebuild_version_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = SlateOutline
                            )
                        )
                        OutlinedTextField(
                            value = newVersionCode,
                            onValueChange = { newVersionCode = it },
                            label = { Text("Version Code") },
                            modifier = Modifier.weight(1f).testTag("rebuild_version_code_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = SlateOutline
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newMinSdk,
                            onValueChange = { newMinSdk = it },
                            label = { Text("Min SDK") },
                            modifier = Modifier.weight(1f).testTag("rebuild_min_sdk_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = SlateOutline
                            )
                        )
                        OutlinedTextField(
                            value = newTargetSdk,
                            onValueChange = { newTargetSdk = it },
                            label = { Text("Target SDK") },
                            modifier = Modifier.weight(1f).testTag("rebuild_target_sdk_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = SlateOutline
                            )
                        )
                    }
                }
            }
        }

        // Custom Asset Injector
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("INJECT CUSTOM ASSET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    OutlinedTextField(
                        value = customAssetPath,
                        onValueChange = { customAssetPath = it },
                        label = { Text("Relative Asset Path") },
                        modifier = Modifier.fillMaxWidth().testTag("rebuild_asset_path_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SlateOutline
                        )
                    )
                    OutlinedTextField(
                        value = customAssetContent,
                        onValueChange = { customAssetContent = it },
                        label = { Text("Content (JSON/Text)") },
                        modifier = Modifier.fillMaxWidth().testTag("rebuild_asset_content_input"),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SlateOutline
                        )
                    )
                }
            }
        }

        // Signing Key Selection
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SIGNING CERTIFICATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select private key alias for v1 signing:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    keysList.forEach { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedKey == key.alias) CyberCyanDark.copy(alpha = 0.5f) else SlateSurfaceVariant)
                                .clickable { viewModel.selectKey(key.alias) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(key.alias, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(key.algorithm, fontSize = 11.sp, color = TextMuted)
                            }
                            if (selectedKey == key.alias) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // Action: Execute Rebuild
        item {
            Button(
                onClick = {
                    val config = RebuildConfig(
                        newAppName = newAppName,
                        newVersionName = newVersionName,
                        newVersionCode = newVersionCode.toLongOrNull() ?: 100L,
                        newMinSdk = newMinSdk.toIntOrNull() ?: 24,
                        newTargetSdk = newTargetSdk.toIntOrNull() ?: 35,
                        customAssets = if (customAssetPath.isNotBlank() && customAssetContent.isNotBlank()) {
                            listOf(CustomAssetItem(customAssetPath, customAssetContent))
                        } else {
                            emptyList()
                        },
                        keyAlias = selectedKey
                    )
                    viewModel.executeRebuild(config)
                },
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("execute_rebuild_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B))
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF00363B), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Executing Pipeline (${rebuildStatus.name})...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Execute 12-Stage Rebuild Pipeline", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Rebuild logs & result
        if (rebuildLogs.isNotEmpty()) {
            item {
                Text("PIPELINE EXECUTION LOGS (${rebuildStatus.name})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF050810)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        rebuildLogs.forEach { log ->
                            Text(
                                text = "• [${log.stage.name}] ${log.message}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (log.isError) CrimsonError else CyberCyanLight,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        if (rebuildResult != null) {
            item {
                val res = rebuildResult!!
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (res.isSuccess) NeonEmerald.copy(alpha = 0.15f) else CrimsonError.copy(alpha = 0.15f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (res.isSuccess) NeonEmerald else CrimsonError
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (res.isSuccess) "REBUILD SUCCESSFUL" else "REBUILD FAILED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (res.isSuccess) NeonEmerald else CrimsonError
                        )
                        if (res.isSuccess) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Output Artifact: ${res.outputApkFile?.name}", fontSize = 13.sp, color = TextPrimary)
                            Text("SHA-256: ${res.outputSha256}", fontSize = 11.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                            Text("Duration: ${res.durationMs}ms", fontSize = 11.sp, color = TextSecondary)
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Error: ${res.errorMessage}", fontSize = 12.sp, color = CrimsonError)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApkSignVerifyTab(viewModel: WorkbenchViewModel, apk: APKInfo) {
    val keysList by viewModel.keysList.collectAsState()
    val selectedKey by viewModel.selectedKeyAlias.collectAsState()
    val verificationResult by viewModel.verificationResult.collectAsState()

    var showNewKeyDialog by remember { mutableStateOf(false) }
    var newKeyAlias by remember { mutableStateOf("release_key_2") }
    var newKeySubject by remember { mutableStateOf("CN=Release Key 2, O=My Studio, C=US") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Signing Keys Management
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("KEYSTORE & SIGNING KEYS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                        Button(
                            onClick = { showNewKeyDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark, contentColor = CyberCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate Key", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    keysList.forEach { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedKey == key.alias) CyberCyanDark.copy(alpha = 0.4f) else SlateSurfaceVariant)
                                .clickable { viewModel.selectKey(key.alias) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(key.alias, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(key.subject, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                Text("SHA-256: ${key.sha256Fingerprint}", fontSize = 10.sp, color = CyberCyan, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            if (selectedKey == key.alias) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // Sign APK button
        item {
            Button(
                onClick = { viewModel.signCurrentApk(selectedKey) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_apk_action_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B))
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign APK with '${selectedKey}'", fontWeight = FontWeight.Bold)
            }
        }

        // Verify APK button
        item {
            OutlinedButton(
                onClick = { viewModel.verifyCurrentApk() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("verify_apk_action_button")
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verify Cryptographic Signature & Digests")
            }
        }

        // Verification Report Card
        if (verificationResult != null) {
            item {
                val res = verificationResult!!
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (res.isValid) NeonEmerald.copy(alpha = 0.15f) else CrimsonError.copy(alpha = 0.15f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (res.isValid) NeonEmerald else CrimsonError
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (res.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (res.isValid) NeonEmerald else CrimsonError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (res.isValid) "SIGNATURE VERIFIED (INTEGRITY OK)" else "SIGNATURE INVALID",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (res.isValid) NeonEmerald else CrimsonError
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Scheme: ${res.signingScheme}", fontSize = 12.sp, color = TextPrimary)
                        Text("Verified Archive Entries: ${res.verifiedEntriesCount}", fontSize = 12.sp, color = TextPrimary)

                        if (res.certificates.isNotEmpty()) {
                            val cert = res.certificates.first()
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Cert Subject: ${cert.subject}", fontSize = 11.sp, color = TextSecondary)
                            Text("Fingerprint: ${cert.sha256Fingerprint}", fontSize = 10.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                        }

                        if (res.issues.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            res.issues.forEach { issue ->
                                Text("⚠️ $issue", fontSize = 11.sp, color = CrimsonError)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewKeyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNewKeyDialog = false },
            title = { Text("Generate RSA-2048 Signing Key", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newKeyAlias,
                        onValueChange = { newKeyAlias = it },
                        label = { Text("Key Alias") }
                    )
                    OutlinedTextField(
                        value = newKeySubject,
                        onValueChange = { newKeySubject = it },
                        label = { Text("Subject DN") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyAlias.isNotBlank()) {
                            viewModel.generateNewKey(newKeyAlias, newKeySubject)
                            showNewKeyDialog = false
                        }
                    }
                ) {
                    Text("Generate & Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ApkExportTab(viewModel: WorkbenchViewModel, apk: APKInfo) {
    val buildHistory by viewModel.buildHistory.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("ACTIVE ARTIFACT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(apk.fileName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Location: ${apk.filePath}", fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("SHA-256: ${apk.sha256}", fontSize = 11.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("BUILD & REBUILD ARTIFACT HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
        }

        if (buildHistory.isEmpty()) {
            item {
                Text("No past rebuild logs yet. Execute a rebuild pipeline to generate artifacts.", fontSize = 12.sp, color = TextMuted)
            }
        } else {
            items(buildHistory) { hist ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(hist.projectName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(hist.buildType, fontSize = 11.sp, color = CyberCyan)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(hist.logSummary, fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

// Subcomponents
@Composable
fun HashRow(label: String, hash: String) {
    Column {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Text(
            text = hash,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = CyberCyanLight
        )
    }
}

@Composable
fun TallyCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
            Text(label, fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
fun PermissionItemCard(perm: com.example.apk.model.PermissionInfo) {
    val color = when (perm.riskLevel) {
        RiskLevel.CRITICAL -> CrimsonError
        RiskLevel.HIGH -> CrimsonError
        RiskLevel.WARNING -> AmberWarning
        RiskLevel.INFO -> NeonEmerald
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(perm.simpleName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = perm.riskLevel.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(perm.name, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            if (perm.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(perm.description, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun ComponentItemCard(comp: com.example.apk.model.ComponentInfo) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(comp.simpleName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(comp.type, fontSize = 10.sp, color = CyberCyan)
            }
            Text(comp.name, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun DexItemCard(dex: com.example.apk.model.DexFileInfo) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dex.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(formatBytes(dex.sizeBytes), fontSize = 11.sp, color = TextMuted)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Classes: ${dex.classDefsCount}", fontSize = 11.sp, color = CyberCyan)
                Text("Methods: ~${dex.methodIdsEstimate}", fontSize = 11.sp, color = TextSecondary)
                Text("Fields: ${dex.fieldIdsCount}", fontSize = 11.sp, color = TextSecondary)
                Text("DEX v${dex.dexVersion}", fontSize = 11.sp, color = TextMuted)
            }

            if (dex.classNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SlateSurfaceVariant)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Hide Extracted Classes (${dex.classNames.size})" else "View Extracted Classes (${dex.classNames.size})",
                        fontSize = 11.sp,
                        color = CyberCyanLight
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        fontSize = 11.sp,
                        color = CyberCyanLight
                    )
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dex.classNames.forEach { className ->
                            Text(
                                text = className,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResourceItemRow(path: String, size: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateSurfaceCard)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(path, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        Text(formatBytes(size), fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
fun CertItemCard(cert: com.example.apk.model.CertificateInfo) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(cert.algorithm, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Subject: ${cert.subject}", fontSize = 11.sp, color = TextPrimary)
            Text("SHA-256: ${cert.sha256Fingerprint}", fontSize = 10.sp, color = CyberCyanLight, fontFamily = FontFamily.Monospace)
            Text("Valid: ${cert.validFrom} to ${cert.validUntil}", fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
fun FindingCard(finding: com.example.ai.diana.DianaFinding) {
    val color = when (finding.severity) {
        RiskLevel.CRITICAL -> CrimsonError
        RiskLevel.HIGH -> CrimsonError
        RiskLevel.WARNING -> AmberWarning
        RiskLevel.INFO -> NeonEmerald
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(finding.category, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = finding.severity.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(finding.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(finding.description, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("💡 Recommendation: ${finding.recommendation}", fontSize = 11.sp, color = CyberCyan, lineHeight = 15.sp)
        }
    }
}

fun formatBytes(bytes: Long): String {
    return if (bytes < 1024 * 1024) {
        "${bytes / 1024} KB"
    } else {
        String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
    }
}

@Composable
fun ApkUploadTab(
    viewModel: WorkbenchViewModel,
    onSelectApkClick: () -> Unit
) {
    val stagedFileName by viewModel.stagedFileName.collectAsState()
    val stagedFileSize by viewModel.stagedFileSize.collectAsState()
    val stagedUri by viewModel.stagedUri.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val pipelineStatus by viewModel.scanPipelineStatus.collectAsState()
    val pipelineMessage by viewModel.scanPipelineMessage.collectAsState()
    val scanError by viewModel.scanError.collectAsState()
    val currentScanResult by viewModel.currentScanResult.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("SELECT APK FROM STORAGE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                            Text("Real Android Storage Access Framework (SAF)", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select an APK from device storage. The pipeline operates strictly read-only on-device, calculating SHA-256/MD5 hashes, decoding the binary AndroidManifest.xml, inspecting DEX headers, cataloging native .so shared libraries, reading X.509 signatures, and evaluating deterministic security findings.",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onSelectApkClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("select_apk_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select APK File", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Staged APK details
        if (stagedFileName != null || stagedUri != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyanDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("STAGED APK FOR ANALYSIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stagedFileName ?: "Unknown Package", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Size: ${formatBytes(stagedFileSize ?: 0L)} (${stagedFileSize ?: 0L} bytes)", fontSize = 11.sp, color = TextMuted)
                            }
                            if (stagedUri != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NeonEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "READY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonEmerald,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (!isScanning) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.startStagedAnalysis() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("start_analysis_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start On-Device Analysis", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Real-time Pipeline Progress Card
        if (isScanning || pipelineStatus != ScanStatus.QUEUED) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = CyberCyan
                                    )
                                } else if (pipelineStatus == ScanStatus.COMPLETED) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(20.dp))
                                } else if (pipelineStatus == ScanStatus.FAILED) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "PIPELINE: ${pipelineStatus.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pipelineStatus == ScanStatus.COMPLETED) NeonEmerald else if (pipelineStatus == ScanStatus.FAILED) CrimsonError else CyberCyan
                                )
                            }

                            if (isScanning) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelActiveScan() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonError)
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(pipelineMessage, fontSize = 12.sp, color = TextPrimary)

                        if (isScanning) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = CyberCyan,
                                trackColor = SlateOutline
                            )
                        }
                    }
                }
            }
        }

        // Error message card
        if (scanError != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CrimsonError.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonError)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ANALYSIS FAILED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonError)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(scanError!!, fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }
        }

        // Sample APK fixture card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("BUILT-IN TEST FIXTURE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Load the actual installed application package from Android storage into the workbench pipeline to test DEX parsing, manifest extraction, native library listing, and certificate analysis.", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.loadSampleApk() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Installed Application APK")
                    }
                }
            }
        }
    }
}

@Composable
fun ApkSecurityFindingsTab(scanResult: ApkScanResult?) {
    var selectedSeverity by remember { mutableStateOf<SecuritySeverity?>(null) }

    if (scanResult == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No scan results available. Import or load an APK first.", color = TextMuted, fontSize = 14.sp)
        }
        return
    }

    val allFindings = scanResult.securityFindings
    val filteredFindings = if (selectedSeverity == null) allFindings else allFindings.filter { it.severity == selectedSeverity }

    val criticalCount = allFindings.count { it.severity == SecuritySeverity.CRITICAL }
    val highCount = allFindings.count { it.severity == SecuritySeverity.HIGH }
    val mediumCount = allFindings.count { it.severity == SecuritySeverity.MEDIUM }
    val lowCount = allFindings.count { it.severity == SecuritySeverity.LOW }
    val infoCount = allFindings.count { it.severity == SecuritySeverity.INFO }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DETERMINISTIC SECURITY AUDIT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Extracted deterministically from binary manifest flags, permission risk profiles, DEX counts, and signing certificates without simulation.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SeverityMetricBadge("Total", allFindings.size, CyberCyan, modifier = Modifier.weight(1f))
                        SeverityMetricBadge("Critical", criticalCount, CrimsonError, modifier = Modifier.weight(1f))
                        SeverityMetricBadge("High", highCount, CrimsonError, modifier = Modifier.weight(1f))
                        SeverityMetricBadge("Medium", mediumCount, AmberWarning, modifier = Modifier.weight(1f))
                        SeverityMetricBadge("Low", lowCount, Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                        SeverityMetricBadge("Info", infoCount, NeonEmerald, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChipItem(
                    label = "All (${allFindings.size})",
                    selected = selectedSeverity == null,
                    onClick = { selectedSeverity = null }
                )
                FilterChipItem(
                    label = "Critical ($criticalCount)",
                    selected = selectedSeverity == SecuritySeverity.CRITICAL,
                    onClick = { selectedSeverity = SecuritySeverity.CRITICAL }
                )
                FilterChipItem(
                    label = "High ($highCount)",
                    selected = selectedSeverity == SecuritySeverity.HIGH,
                    onClick = { selectedSeverity = SecuritySeverity.HIGH }
                )
                FilterChipItem(
                    label = "Medium ($mediumCount)",
                    selected = selectedSeverity == SecuritySeverity.MEDIUM,
                    onClick = { selectedSeverity = SecuritySeverity.MEDIUM }
                )
                FilterChipItem(
                    label = "Low ($lowCount)",
                    selected = selectedSeverity == SecuritySeverity.LOW,
                    onClick = { selectedSeverity = SecuritySeverity.LOW }
                )
                FilterChipItem(
                    label = "Info ($infoCount)",
                    selected = selectedSeverity == SecuritySeverity.INFO,
                    onClick = { selectedSeverity = SecuritySeverity.INFO }
                )
            }
        }

        if (filteredFindings.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No security findings for this filter.", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(filteredFindings) { finding ->
                DeterministicFindingCard(finding)
            }
        }
    }
}

@Composable
fun SeverityMetricBadge(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 9.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

@Composable
fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) CyberCyan else SlateSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) CyberCyan else SlateOutline),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFF00363B) else TextPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun DeterministicFindingCard(finding: SecurityFinding) {
    val color = when (finding.severity) {
        SecuritySeverity.CRITICAL -> CrimsonError
        SecuritySeverity.HIGH -> CrimsonError
        SecuritySeverity.MEDIUM -> AmberWarning
        SecuritySeverity.LOW -> Color(0xFFF59E0B)
        SecuritySeverity.INFO -> NeonEmerald
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(finding.category.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("• ${finding.id.take(8)}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = finding.severity.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(finding.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(finding.description, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)

            if (finding.evidence.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0B1220))
                        .padding(8.dp)
                ) {
                    Text("EVIDENCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = finding.evidence,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("💡 ", fontSize = 11.sp)
                Text(
                    text = finding.recommendation,
                    fontSize = 11.sp,
                    color = CyberCyanLight,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun ManifestInspectSection(manifest: com.example.apk.model.ManifestInfo?, apk: APKInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BINARY MANIFEST PARAMETERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                ManifestRow("Package Name", manifest?.packageName ?: apk.packageName, isMono = true)
                ManifestRow("Application Name", manifest?.appName ?: apk.appName)
                ManifestRow("Version Name", manifest?.versionName ?: apk.versionName)
                ManifestRow("Version Code", "${manifest?.versionCode ?: apk.versionCode}")
                ManifestRow("Min SDK", "API ${manifest?.minSdk ?: apk.minSdk}")
                ManifestRow("Target SDK", "API ${manifest?.targetSdk ?: apk.targetSdk}")
                ManifestRow("Compile SDK", "API ${manifest?.compileSdk ?: apk.targetSdk}")
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("APPLICATION SECURITY FLAGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                ManifestFlagRow("android:debuggable", manifest?.isDebuggable ?: apk.isDebuggable, isRiskWhenTrue = true)
                ManifestFlagRow("android:allowBackup", manifest?.allowBackup ?: apk.allowsBackup, isRiskWhenTrue = true)
                ManifestFlagRow("android:usesCleartextTraffic", manifest?.usesCleartextTraffic ?: true, isRiskWhenTrue = true)
                ManifestFlagRow("android:supportsRtl", apk.supportsRtl, isRiskWhenTrue = false)
                ManifestRow("android:networkSecurityConfig", manifest?.networkSecurityConfig ?: "None")
                ManifestRow("android:theme", manifest?.theme ?: "Default")
            }
        }

        if (manifest != null && manifest.usesFeatures.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("DECLARED USES-FEATURE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    manifest.usesFeatures.forEach { feat ->
                        Text("• $feat", fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun ManifestRow(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
fun ManifestFlagRow(label: String, value: Boolean, isRiskWhenTrue: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        val color = if (value && isRiskWhenTrue) CrimsonError else if (!value && !isRiskWhenTrue) AmberWarning else NeonEmerald
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = value.toString().uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ScanPermissionItemCard(perm: com.example.apk.model.ScanPermissionInfo) {
    val color = when (perm.riskIndicator) {
        RiskLevel.CRITICAL -> CrimsonError
        RiskLevel.HIGH -> CrimsonError
        RiskLevel.WARNING -> AmberWarning
        RiskLevel.INFO -> NeonEmerald
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(perm.name.substringAfterLast("."), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CyberCyanDark.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = perm.protectionCategory.name,
                            fontSize = 9.sp,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = perm.riskIndicator.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(perm.name, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            if (perm.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(perm.reason, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun ScanDexItemCard(dex: com.example.apk.model.DexInfo) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dex.fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(formatBytes(dex.fileSize), fontSize = 11.sp, color = TextMuted)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Classes: ${dex.classDefsCount}", fontSize = 11.sp, color = CyberCyan)
                Text("Magic: ${dex.magic} v${dex.version}", fontSize = 11.sp, color = TextSecondary)
                Text("Checksum: ${dex.adler32Checksum}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("SHA-256: ${dex.sha256}", fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("SHA-1 Signature: ${dex.sha1Signature}", fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = SlateSurfaceVariant
            ) {
                Text(
                    text = dex.semanticAnalysisStatus,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun ScanCertItemCard(cert: com.example.apk.model.ScanCertificateInfo) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cert.signatureAlgorithm, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (cert.status == CertificateStatus.CERTIFICATE_VERIFIED) NeonEmerald.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = cert.status.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (cert.status == CertificateStatus.CERTIFICATE_VERIFIED) NeonEmerald else AmberWarning,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Subject: ${cert.subject}", fontSize = 11.sp, color = TextPrimary)
            Text("Issuer: ${cert.issuer}", fontSize = 11.sp, color = TextSecondary)
            Text("Serial Number: ${cert.serialNumber}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("Public Key: ${cert.publicKeyAlgorithm}", fontSize = 10.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("SHA-256: ${cert.sha256Fingerprint}", fontSize = 10.sp, color = CyberCyanLight, fontFamily = FontFamily.Monospace)
            Text("SHA-1: ${cert.sha1Fingerprint}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("Valid: ${cert.validFrom} to ${cert.validUntil}", fontSize = 10.sp, color = TextMuted)

            if (cert.verificationDetails.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Note: ${cert.verificationDetails}", fontSize = 11.sp, color = AmberWarning)
            }
        }
    }
}
