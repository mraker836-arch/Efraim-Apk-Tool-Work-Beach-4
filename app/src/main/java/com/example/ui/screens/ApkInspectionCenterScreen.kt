package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.inspection.ApkArchiveInspector
import com.example.apk.inspection.ApkInspectionSearchEngine
import com.example.apk.inspection.ApkSecurityScoreCalculator
import com.example.apk.inspection.ArchiveInspectionSummary
import com.example.apk.model.*
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import com.example.ui.WorkbenchViewModel
import com.example.ui.components.ResourceTableInspector
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

enum class InspectionTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    ARCHIVE("Archive", Icons.Default.FolderZip),
    DEX("DEX", Icons.Default.Memory),
    MANIFEST("Manifest", Icons.Default.Code),
    RESOURCES("Resources", Icons.Default.Article),
    PERMISSIONS("Permissions", Icons.Default.VpnKey),
    NATIVE_LIBS("Native Libs", Icons.Default.Layers),
    CERTIFICATE("Cert & Sign", Icons.Default.VerifiedUser),
    FINDINGS("Findings", Icons.Default.Security),
    STORAGE_NET("Storage & Net", Icons.Default.Storage)
}

@Composable
fun ApkInspectionCenterScreen(
    apk: APKInfo,
    scanResult: ApkScanResult?,
    viewModel: WorkbenchViewModel
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(InspectionTab.OVERVIEW) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFindingDetail by remember { mutableStateOf<SecurityFinding?>(null) }

    // Read real archive details
    val archiveSummary = remember(apk, scanResult) {
        val f = File(apk.filePath)
        if (f.exists() && f.canRead()) {
            ApkArchiveInspector.inspectArchive(f)
        } else if (scanResult != null) {
            ApkArchiveInspector.fromFileInfo(scanResult.fileInfo)
        } else {
            ApkArchiveInspector.fromFileInfo(
                ApkFileInfo(
                    scanId = apk.id,
                    fileName = apk.fileName,
                    fileSize = apk.fileSize,
                    sha256 = apk.sha256,
                    md5 = apk.md5,
                    mimeType = "application/vnd.android.package-archive",
                    uri = apk.filePath,
                    totalZipEntries = apk.totalEntriesCount,
                    dexCount = apk.dexFiles.size,
                    nativeLibraryCount = apk.nativeLibraries.values.sumOf { it.size },
                    assetCount = apk.assets.size,
                    resourcePresence = apk.resources.isNotEmpty()
                )
            )
        }
    }

    // Deterministic security score
    val scoreBreakdown = remember(scanResult) {
        ApkSecurityScoreCalculator.calculateScore(scanResult?.securityFindings ?: emptyList())
    }

    // Active search filter
    val searchResults = remember(searchQuery, scanResult, archiveSummary) {
        if (searchQuery.isNotBlank()) {
            ApkInspectionSearchEngine.search(scanResult, archiveSummary.entries, searchQuery)
        } else null
    }

    activeFindingDetail?.let { finding ->
        FindingDetailDialog(
            finding = finding,
            context = context,
            onDismiss = { activeFindingDetail = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackground)
            .padding(14.dp)
    ) {
        // Inspection Center Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Inspection Center",
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "APK INSPECTION CENTER",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.2.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (scoreBreakdown.isClean) NeonSuccess.copy(alpha = 0.15f) else NeonWarning.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, if (scoreBreakdown.isClean) NeonSuccess else NeonWarning)
            ) {
                Text(
                    text = "Score: ${scoreBreakdown.score} (${scoreBreakdown.grade})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (scoreBreakdown.isClean) NeonSuccess else NeonWarning,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Local In-Memory Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search components, permissions, DEX, native libs, certs, entries...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberCyan, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = SlateOutline,
                focusedContainerColor = SlateSurfaceVariant,
                unfocusedContainerColor = SlateSurfaceVariant,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("inspection_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Category Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InspectionTab.values().forEach { tab ->
                val isSelected = selectedTab == tab && searchQuery.isBlank()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) CyberCyan else SlateSurfaceVariant,
                    border = BorderStroke(1.dp, if (isSelected) CyberCyan else SlateOutline),
                    modifier = Modifier
                        .clickable {
                            selectedTab = tab
                            searchQuery = ""
                        }
                        .testTag("inspection_tab_${tab.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) Color(0xFF00363B) else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF00363B) else TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content Area: Search Mode or Selected Section
        if (searchResults != null) {
            SearchResultsSection(
                results = searchResults,
                context = context,
                onFindingClick = { activeFindingDetail = it }
            )
        } else {
            when (selectedTab) {
                InspectionTab.OVERVIEW -> OverviewInspectionSection(apk, scanResult, scoreBreakdown, context)
                InspectionTab.ARCHIVE -> ArchiveInspectionSection(archiveSummary, context)
                InspectionTab.DEX -> DexInspectionSection(apk, scanResult, context)
                InspectionTab.MANIFEST -> ManifestIntelligenceSection(apk, scanResult, context)
                InspectionTab.RESOURCES -> {
                    val strings = scanResult?.manifestInfo?.rawXmlText?.let { xml ->
                        // Extract text tokens or fallback to components and actions
                        val list = mutableListOf<String>()
                        list.addAll(scanResult.manifestInfo.activities.map { it.name })
                        list.addAll(scanResult.manifestInfo.services.map { it.name })
                        list.addAll(scanResult.manifestInfo.receivers.map { it.name })
                        list.addAll(scanResult.manifestInfo.providers.map { it.name })
                        list.addAll(scanResult.manifestInfo.permissions)
                        list.addAll(scanResult.manifestInfo.usesFeatures)
                        list.distinct()
                    } ?: (apk.permissions.map { it.name } + apk.activities.map { it.name } + apk.services.map { it.name })
                    ResourceTableInspector(
                        resourceStrings = strings,
                        totalStringPoolCount = strings.size
                    )
                }
                InspectionTab.PERMISSIONS -> PermissionsIntelligenceSection(apk, scanResult, context)
                InspectionTab.NATIVE_LIBS -> NativeLibrariesSection(apk, scanResult, context)
                InspectionTab.CERTIFICATE -> CertificateAndSignatureSection(apk, scanResult, context)
                InspectionTab.FINDINGS -> SecurityFindingsSection(scanResult, onFindingClick = { activeFindingDetail = it })
                InspectionTab.STORAGE_NET -> StorageAndNetworkSection(apk, scanResult, context)
            }
        }
    }
}

@Composable
fun SearchResultsSection(
    results: com.example.apk.inspection.InspectionSearchResults,
    context: Context,
    onFindingClick: (SecurityFinding) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "SEARCH RESULTS: ${results.totalMatches} MATCH(ES) FOR \"${results.query}\"",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 1.sp
            )
        }

        if (results.isEmpty) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No matching components, permissions, DEX, native libs, or findings.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        if (results.matchingFindings.isNotEmpty()) {
            item { Text("SECURITY FINDINGS (${results.matchingFindings.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted) }
            items(results.matchingFindings) { f ->
                SecurityFindingCard(f, onClick = { onFindingClick(f) })
            }
        }

        if (results.matchingComponents.isNotEmpty()) {
            item { Text("COMPONENTS (${results.matchingComponents.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted) }
            items(results.matchingComponents) { comp ->
                ComponentItemCard(comp)
            }
        }

        if (results.matchingPermissions.isNotEmpty()) {
            item { Text("PERMISSIONS (${results.matchingPermissions.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted) }
            items(results.matchingPermissions) { perm ->
                ScanPermissionItemCard(perm)
            }
        }

        if (results.matchingDex.isNotEmpty()) {
            item { Text("DEX BYTECODE (${results.matchingDex.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted) }
            items(results.matchingDex) { dex ->
                ScanDexItemCard(dex)
            }
        }

        if (results.matchingNativeLibs.isNotEmpty()) {
            item { Text("NATIVE LIBRARIES (${results.matchingNativeLibs.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted) }
            items(results.matchingNativeLibs) { lib ->
                NativeLibItemCard(lib, context)
            }
        }

        if (results.matchingCertificates.isNotEmpty()) {
            item { Text("CERTIFICATES (${results.matchingCertificates.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted) }
            items(results.matchingCertificates) { cert ->
                ScanCertItemCard(cert)
            }
        }

        if (results.matchingArchiveEntries.isNotEmpty()) {
            item { Text("ARCHIVE ENTRIES (${results.matchingArchiveEntries.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted) }
            items(results.matchingArchiveEntries) { entry ->
                ArchiveEntryRow(entry, context)
            }
        }
    }
}
