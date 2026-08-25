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
import com.example.apk.model.APKInfo
import com.example.apk.model.BuildStatus
import com.example.apk.model.CustomAssetItem
import com.example.apk.model.RebuildConfig
import com.example.apk.model.RiskLevel
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

        if (isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyberCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing APK archive...", color = TextSecondary)
                }
            }
            return
        }

        if (currentApk == null) {
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
                            "Import an Android APK file to inspect, audit with DIANA, or run the local rebuild pipeline.",
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
                                Text("Import APK", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { viewModel.loadSampleApk() }) {
                                Text("Load Sample")
                            }
                        }
                    }
                }
            }
            return
        }

        val apk = currentApk!!

        when (subTab) {
            ApkLabSubTab.OVERVIEW -> ApkOverviewTab(apk, onImportApkClick, onLoadSample = { viewModel.loadSampleApk() })
            ApkLabSubTab.INSPECT -> ApkInspectTab(apk)
            ApkLabSubTab.DIANA_ANALYSIS -> ApkDianaAnalysisTab(viewModel, apk)
            ApkLabSubTab.REBUILD -> ApkRebuildTab(viewModel, apk)
            ApkLabSubTab.SIGN_VERIFY -> ApkSignVerifyTab(viewModel, apk)
            ApkLabSubTab.EXPORT -> ApkExportTab(viewModel, apk)
        }
    }
}

@Composable
fun ApkOverviewTab(
    apk: APKInfo,
    onImportClick: () -> Unit,
    onLoadSample: () -> Unit
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
                    HashRow(label = "SHA-256", hash = apk.sha256)
                    Spacer(modifier = Modifier.height(6.dp))
                    HashRow(label = "MD5", hash = apk.md5)
                }
            }
        }

        // Component tallies grid
        item {
            Text("STRUCTURE SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TallyCard("Activities", "${apk.activities.size}", modifier = Modifier.weight(1f))
                    TallyCard("Services", "${apk.services.size}", modifier = Modifier.weight(1f))
                    TallyCard("Receivers", "${apk.receivers.size}", modifier = Modifier.weight(1f))
                    TallyCard("Providers", "${apk.providers.size}", modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TallyCard("Permissions", "${apk.permissions.size}", modifier = Modifier.weight(1f))
                    TallyCard("DEX Classes", "${apk.totalDexClasses}", modifier = Modifier.weight(1f))
                    TallyCard("Native ABIs", "${apk.supportedAbis.size}", modifier = Modifier.weight(1f))
                    TallyCard("Certificates", "${apk.certificates.size}", modifier = Modifier.weight(1f))
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
fun ApkInspectTab(apk: APKInfo) {
    var inspectCategory by remember { mutableStateOf("Permissions") }
    val categories = listOf("Permissions", "Components", "DEX", "Native ABIs", "Assets", "Resources", "Certificates")

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
                "Permissions" -> {
                    if (apk.permissions.isEmpty()) {
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
                    items(apk.dexFiles) { dex ->
                        DexItemCard(dex)
                    }
                }
                "Native ABIs" -> {
                    if (apk.nativeLibraries.isEmpty()) {
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
                    if (apk.certificates.isEmpty()) {
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
