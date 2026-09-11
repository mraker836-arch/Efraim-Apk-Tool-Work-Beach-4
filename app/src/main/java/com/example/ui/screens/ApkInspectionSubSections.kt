package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.inspection.ArchiveInspectionSummary
import com.example.apk.inspection.SecurityScoreBreakdown
import com.example.apk.model.*
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import com.example.ui.components.DexMethodClassBrowser
import com.example.ui.components.ManifestXmlViewer
import com.example.ui.components.NativeLibraryAbiBreakdownCard
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun OverviewInspectionSection(
    apk: APKInfo,
    scanResult: ApkScanResult?,
    scoreBreakdown: SecurityScoreBreakdown,
    context: Context
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Security Summary Card
        item {
            SecuritySummaryCard(scoreBreakdown)
        }

        // APK Identity Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apk_identity_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "APK IDENTITY & CONFIGURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val pkg = scanResult?.manifestInfo?.packageName?.ifBlank { null } ?: apk.packageName.ifBlank { "Unavailable" }
                    val appName = scanResult?.manifestInfo?.appName?.ifBlank { null } ?: apk.appName.ifBlank { "Unavailable" }
                    val verName = scanResult?.manifestInfo?.versionName?.ifBlank { null } ?: apk.versionName.ifBlank { "Unavailable" }
                    val verCode = scanResult?.manifestInfo?.versionCode ?: apk.versionCode
                    val fileSize = scanResult?.fileInfo?.fileSize ?: apk.fileSize
                    val sha256 = scanResult?.fileInfo?.sha256?.ifBlank { null } ?: apk.sha256.ifBlank { "Unavailable" }
                    val md5 = scanResult?.fileInfo?.md5?.ifBlank { null } ?: apk.md5.ifBlank { "Unavailable" }
                    val minSdk = scanResult?.manifestInfo?.minSdk ?: apk.minSdk
                    val targetSdk = scanResult?.manifestInfo?.targetSdk ?: apk.targetSdk
                    val compileSdk = scanResult?.manifestInfo?.compileSdk ?: apk.compileSdk
                    val debuggable = scanResult?.manifestInfo?.isDebuggable ?: apk.isDebuggable
                    val backup = scanResult?.manifestInfo?.allowBackup ?: apk.allowsBackup
                    val cleartext = scanResult?.manifestInfo?.usesCleartextTraffic ?: false

                    InspectionIdentityRow("Package Name", pkg, context, copyLabel = "Package Name", isMonospace = true)
                    InspectionIdentityRow("Application Label", appName, context)
                    InspectionIdentityRow("Version Name", verName, context)
                    InspectionIdentityRow("Version Code", verCode.toString(), context)
                    InspectionIdentityRow("APK Size", "${formatBytes(fileSize)} ($fileSize bytes)", context)
                    InspectionIdentityRow("SHA-256", sha256, context, copyLabel = "SHA-256", isMonospace = true)
                    InspectionIdentityRow("MD5", md5, context, copyLabel = "MD5", isMonospace = true)
                    InspectionIdentityRow("Min SDK", if (minSdk > 0) "Android $minSdk" else "Unavailable", context)
                    InspectionIdentityRow("Target SDK", if (targetSdk > 0) "Android $targetSdk" else "Unavailable", context)
                    InspectionIdentityRow("Compile SDK", compileSdk?.let { "Android $it" } ?: "Unavailable", context)
                    InspectionIdentityRow(
                        "Debuggable State",
                        if (debuggable) "ENABLED (Risky)" else "DISABLED (Secure)",
                        context,
                        badgeColor = if (debuggable) NeonDanger else NeonSuccess
                    )
                    InspectionIdentityRow(
                        "Backup Allowed",
                        if (backup) "ENABLED" else "DISABLED",
                        context,
                        badgeColor = if (backup) NeonWarning else NeonSuccess
                    )
                    InspectionIdentityRow(
                        "Cleartext Traffic",
                        if (cleartext) "PERMITTED (HTTP Allowed)" else "DISABLED (HTTPS Enforced)",
                        context,
                        badgeColor = if (cleartext) NeonDanger else NeonSuccess
                    )
                }
            }
        }

        // Structural Summary Cards
        item {
            Text("STRUCTURAL SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
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
    }
}

@Composable
fun ArchiveInspectionSection(summary: ArchiveInspectionSummary, context: Context) {
    var entrySearch by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    val filteredEntries = remember(summary.entries, entrySearch) {
        if (entrySearch.isBlank()) summary.entries
        else summary.entries.filter { it.name.contains(entrySearch, ignoreCase = true) || it.compressionMethod.contains(entrySearch, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ZIP Stats Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("ARCHIVE INTEGRITY & METRICS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    InspectionIdentityRow("Total Archive Entries", summary.totalEntries.toString(), context)
                    InspectionIdentityRow("Total Uncompressed Size", "${formatBytes(summary.uncompressedSize)} (${summary.uncompressedSize} B)", context)
                    InspectionIdentityRow("Compressed Archive Size", "${formatBytes(summary.compressedSize)} (${summary.compressedSize} B)", context)
                    InspectionIdentityRow("Compression Ratio", "${String.format(Locale.US, "%.1f", summary.compressionRatio)}%", context)
                }
            }
        }

        // Structural Presence Chips
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("KEY STRUCTURAL COMPONENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PresenceChip("AndroidManifest.xml", summary.hasManifest, Modifier.weight(1f))
                        PresenceChip("resources.arsc", summary.hasResourcesArsc, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PresenceChip("classes.dex (${summary.dexFiles.size})", summary.dexFiles.isNotEmpty(), Modifier.weight(1f))
                        PresenceChip("classes2.dex", summary.dexFiles.contains("classes2.dex"), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PresenceChip("META-INF/ (${summary.metaInfFiles.size})", summary.metaInfFiles.isNotEmpty(), Modifier.weight(1f))
                        PresenceChip("lib/ .so (${summary.nativeLibFiles.size})", summary.nativeLibFiles.isNotEmpty(), Modifier.weight(1f))
                        PresenceChip("assets/ (${summary.assetFiles.size})", summary.assetFiles.isNotEmpty(), Modifier.weight(1f))
                    }
                }
            }
        }

        // Expandable Archive-Entry Viewer
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("EXPANDABLE ARCHIVE ENTRY VIEWER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                            Text("${summary.entries.size} entries detected in ZIP table", fontSize = 11.sp, color = TextMuted)
                        }
                        IconButton(onClick = { isExpanded = !isExpanded }) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Entry Viewer",
                                tint = CyberCyan
                            )
                        }
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = entrySearch,
                            onValueChange = { entrySearch = it },
                            placeholder = { Text("Filter archive entries...", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = SlateOutline,
                                focusedContainerColor = SlateSurfaceVariant,
                                unfocusedContainerColor = SlateSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                }
            }
        }

        if (isExpanded) {
            if (filteredEntries.isEmpty()) {
                item {
                    Text("No archive entries matching filter.", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(8.dp))
                }
            } else {
                items(filteredEntries.take(150)) { entry ->
                    ArchiveEntryRow(entry, context)
                }
                if (filteredEntries.size > 150) {
                    item {
                        Text("Showing 150 of ${filteredEntries.size} entries. Refine search for specific files.", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun PresenceChip(label: String, isPresent: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isPresent) NeonSuccess.copy(alpha = 0.12f) else SlateSurfaceVariant,
        border = BorderStroke(0.5.dp, if (isPresent) NeonSuccess.copy(alpha = 0.4f) else SlateOutline),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPresent) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isPresent) NeonSuccess else TextMuted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPresent) TextPrimary else TextMuted
            )
        }
    }
}

@Composable
fun ArchiveEntryRow(entry: ArchiveEntryDetail, context: Context) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant),
        border = BorderStroke(0.5.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("C: ${formatBytes(entry.compressedSize)}", fontSize = 9.sp, color = TextSecondary)
                    Text("U: ${formatBytes(entry.uncompressedSize)}", fontSize = 9.sp, color = TextMuted)
                    Text(entry.compressionMethod, fontSize = 9.sp, color = CyberCyan)
                }
            }
            IconButton(
                onClick = { copyToClipboard(context, "Archive Entry Name", entry.name) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Name", tint = CyberCyan, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
fun DexInspectionSection(apk: APKInfo, scanResult: ApkScanResult?, context: Context) {
    val dexList = scanResult?.dexList ?: apk.dexFiles.map { d ->
        DexInfo(
            fileName = d.name,
            fileSize = d.sizeBytes,
            sha256 = "Unavailable",
            magic = "dex\n",
            version = d.dexVersion,
            adler32Checksum = "Unavailable",
            sha1Signature = "Unavailable",
            classDefsCount = d.classDefsCount,
            methodIdsEstimate = d.methodIdsEstimate,
            stringIdsCount = d.stringIdsCount,
            typeIdsCount = d.typeIdsCount,
            protoIdsCount = d.protoIdsCount,
            fieldIdsCount = d.fieldIdsCount,
            classNames = d.classNames,
            semanticAnalysisStatus = "Advanced DEX semantic parsing unavailable"
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            DexMethodClassBrowser(dexList = dexList)
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("DEX BYTECODE OVERVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Total DEX Files: ${dexList.size}", fontSize = 12.sp, color = TextPrimary)
                    Text("Total Defined Classes: ${dexList.sumOf { it.classDefsCount }}", fontSize = 12.sp, color = TextPrimary)
                    Text("Total Method IDs Estimate: ~${dexList.sumOf { it.methodIdsEstimate }}", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        items(dexList) { dex ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(dex.fileName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberCyan, fontFamily = FontFamily.Monospace)
                        Surface(shape = RoundedCornerShape(4.dp), color = CyberCyan.copy(alpha = 0.12f)) {
                            Text(formatBytes(dex.fileSize), fontSize = 11.sp, color = CyberCyan, modifier = Modifier.padding(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    InspectionIdentityRow("DEX Magic / Version", "${dex.magic.trim()} / v${dex.version}", context)
                    InspectionIdentityRow("Adler32 Checksum", dex.adler32Checksum, context, copyLabel = "Adler32 Checksum", isMonospace = true)
                    InspectionIdentityRow("SHA-256", dex.sha256, context, copyLabel = "DEX SHA-256", isMonospace = true)
                    InspectionIdentityRow("Defined Classes", dex.classDefsCount.toString(), context)
                    InspectionIdentityRow("Estimated Method IDs", "~${dex.methodIdsEstimate}", context)
                    InspectionIdentityRow("String / Type IDs", "${dex.stringIdsCount} / ${dex.typeIdsCount}", context)
                    InspectionIdentityRow("Proto / Field IDs", "${dex.protoIdsCount} / ${dex.fieldIdsCount}", context)
                    InspectionIdentityRow("File Offset", if (dex.fileOffset > 0) "${dex.fileOffset} bytes" else "0 (Archive-Relative)", context)
                    InspectionIdentityRow("Semantic Parsing", dex.semanticAnalysisStatus, context)

                    if (dex.classNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("SAMPLED CLASS NAMES (${dex.classNames.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        dex.classNames.take(8).forEach { c ->
                            Text("• $c", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManifestIntelligenceSection(apk: APKInfo, scanResult: ApkScanResult?, context: Context) {
    var manifestTab by remember { mutableStateOf("Application") }
    val tabs = listOf("Application", "Activities", "Services", "Receivers", "Providers", "Permissions", "Features", "Raw XML")

    val man = scanResult?.manifestInfo

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { t ->
                val isSelected = manifestTab == t
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) CyberCyan else SlateSurfaceVariant,
                    border = BorderStroke(1.dp, if (isSelected) CyberCyan else SlateOutline),
                    modifier = Modifier.clickable { manifestTab = t }
                ) {
                    Text(
                        text = t,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF00363B) else TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (manifestTab == "Raw XML") {
            ManifestXmlViewer(
                manifest = man,
                rawXml = man?.rawXmlText
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (manifestTab) {
                    "Application" -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                            border = BorderStroke(1.dp, SlateOutline)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("APPLICATION NODE PROPERTIES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                InspectionIdentityRow("Package Name", man?.packageName ?: apk.packageName, context, copyLabel = "Package Name", isMonospace = true)
                                InspectionIdentityRow("App Label", man?.appName ?: apk.appName, context)
                                InspectionIdentityRow("Min / Target SDK", "${man?.minSdk ?: apk.minSdk} / ${man?.targetSdk ?: apk.targetSdk}", context)
                                InspectionIdentityRow("Compile SDK", man?.compileSdk?.toString() ?: apk.compileSdk?.toString() ?: "Unavailable", context)
                                InspectionIdentityRow("Theme Reference", man?.theme ?: "Unavailable", context)
                                InspectionIdentityRow("Network Security Config", man?.networkSecurityConfig ?: "Unavailable", context)
                                InspectionIdentityRow(
                                    "Debuggable",
                                    if (man?.isDebuggable == true) "ENABLED (Risky)" else "DISABLED (Secure)",
                                    context,
                                    badgeColor = if (man?.isDebuggable == true) NeonDanger else NeonSuccess
                                )
                                InspectionIdentityRow(
                                    "Allow Backup",
                                    if (man?.allowBackup == true) "ENABLED" else "DISABLED",
                                    context,
                                    badgeColor = if (man?.allowBackup == true) NeonWarning else NeonSuccess
                                )
                                InspectionIdentityRow(
                                    "Cleartext Traffic",
                                    if (man?.usesCleartextTraffic == true) "PERMITTED (HTTP)" else "DISABLED (HTTPS)",
                                    context,
                                    badgeColor = if (man?.usesCleartextTraffic == true) NeonDanger else NeonSuccess
                                )
                            }
                        }
                    }
                }
                "Activities" -> {
                    val list = man?.activities ?: apk.activities
                    if (list.isEmpty()) {
                        item { Text("No activities declared in manifest.", fontSize = 12.sp, color = TextMuted) }
                    } else {
                        items(list) { comp ->
                            ManifestComponentCard(comp, context, isDebuggable = man?.isDebuggable ?: apk.isDebuggable)
                        }
                    }
                }
                "Services" -> {
                    val list = man?.services ?: apk.services
                    if (list.isEmpty()) {
                        item { Text("No services declared in manifest.", fontSize = 12.sp, color = TextMuted) }
                    } else {
                        items(list) { comp ->
                            ManifestComponentCard(comp, context, isDebuggable = man?.isDebuggable ?: apk.isDebuggable)
                        }
                    }
                }
                "Receivers" -> {
                    val list = man?.receivers ?: apk.receivers
                    if (list.isEmpty()) {
                        item { Text("No broadcast receivers declared in manifest.", fontSize = 12.sp, color = TextMuted) }
                    } else {
                        items(list) { comp ->
                            ManifestComponentCard(comp, context, isDebuggable = man?.isDebuggable ?: apk.isDebuggable)
                        }
                    }
                }
                "Providers" -> {
                    val list = man?.providers ?: apk.providers
                    if (list.isEmpty()) {
                        item { Text("No content providers declared in manifest.", fontSize = 12.sp, color = TextMuted) }
                    } else {
                        items(list) { comp ->
                            ManifestComponentCard(comp, context, isDebuggable = man?.isDebuggable ?: apk.isDebuggable)
                        }
                    }
                }
                "Permissions" -> {
                    val perms = man?.permissions ?: apk.permissions.map { it.name }
                    if (perms.isEmpty()) {
                        item { Text("No permissions requested in manifest.", fontSize = 12.sp, color = TextMuted) }
                    } else {
                        items(perms) { p ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlateSurfaceVariant,
                                border = BorderStroke(0.5.dp, SlateOutline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { copyToClipboard(context, "Permission", p) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                "Features" -> {
                    val feats = man?.usesFeatures ?: emptyList()
                    if (feats.isEmpty()) {
                        item { Text("No hardware or software features declared in manifest.", fontSize = 12.sp, color = TextMuted) }
                    } else {
                        items(feats) { f ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlateSurfaceVariant,
                                border = BorderStroke(0.5.dp, SlateOutline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(f, fontSize = 11.sp, color = TextPrimary, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun ManifestComponentCard(comp: ComponentInfo, context: Context, isDebuggable: Boolean) {
    val isRiskyExported = comp.exported && comp.permission.isNullOrBlank()
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = BorderStroke(1.dp, if (isRiskyExported) NeonDanger.copy(alpha = 0.5f) else SlateOutline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comp.simpleName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (comp.exported) (if (isRiskyExported) NeonDanger.copy(alpha = 0.15f) else NeonWarning.copy(alpha = 0.15f)) else NeonSuccess.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, if (comp.exported) (if (isRiskyExported) NeonDanger else NeonWarning) else NeonSuccess)
                ) {
                    Text(
                        text = if (comp.exported) "EXPORTED" else "PRIVATE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (comp.exported) (if (isRiskyExported) NeonDanger else NeonWarning) else NeonSuccess,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comp.name,
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { copyToClipboard(context, "Component Name", comp.name) }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Component Name", tint = CyberCyan, modifier = Modifier.size(11.dp))
                }
            }

            if (isRiskyExported) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NeonDanger.copy(alpha = 0.1f),
                    border = BorderStroke(0.5.dp, NeonDanger.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Risky configuration", tint = NeonDanger, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Risk: Exported without protection permission (Evidence: exported=true, permission=null)", fontSize = 9.sp, color = NeonDanger)
                    }
                }
            }

            if (!comp.permission.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Permission: ${comp.permission}", fontSize = 10.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
            }

            if (comp.intentActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("INTENT ACTIONS (${comp.intentActions.size})", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                comp.intentActions.forEach { a ->
                    Text("• $a", fontSize = 9.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun PermissionsIntelligenceSection(apk: APKInfo, scanResult: ApkScanResult?, context: Context) {
    val perms = scanResult?.permissionsList ?: apk.permissions.map { p ->
        ScanPermissionInfo(
            name = p.name,
            simpleName = p.simpleName,
            protectionCategory = ProtectionCategory.UNKNOWN,
            riskIndicator = p.riskLevel,
            reason = "Standard Android permission declaration",
            description = p.description
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("PERMISSION INTELLIGENCE SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Total Declared Permissions: ${perms.size}", fontSize = 12.sp, color = TextPrimary)
                    Text("Dangerous / Critical Permissions: ${perms.count { it.riskIndicator == RiskLevel.CRITICAL || it.riskIndicator == RiskLevel.HIGH }}", fontSize = 12.sp, color = NeonDanger)
                }
            }
        }

        if (perms.isEmpty()) {
            item { Text("No permissions requested in manifest.", fontSize = 12.sp, color = TextMuted) }
        } else {
            items(perms) { perm ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    border = BorderStroke(1.dp, SlateOutline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(perm.simpleName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (perm.riskIndicator) {
                                    RiskLevel.CRITICAL -> NeonDanger.copy(alpha = 0.15f)
                                    RiskLevel.HIGH -> Color(0xFFFF7043).copy(alpha = 0.15f)
                                    RiskLevel.WARNING -> NeonWarning.copy(alpha = 0.15f)
                                    else -> NeonSuccess.copy(alpha = 0.15f)
                                },
                                border = BorderStroke(0.5.dp, when (perm.riskIndicator) {
                                    RiskLevel.CRITICAL -> NeonDanger
                                    RiskLevel.HIGH -> Color(0xFFFF7043)
                                    RiskLevel.WARNING -> NeonWarning
                                    else -> NeonSuccess
                                })
                            ) {
                                Text(
                                    text = perm.riskIndicator.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (perm.riskIndicator) {
                                        RiskLevel.CRITICAL -> NeonDanger
                                        RiskLevel.HIGH -> Color(0xFFFF7043)
                                        RiskLevel.WARNING -> NeonWarning
                                        else -> NeonSuccess
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(perm.name, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            IconButton(onClick = { copyToClipboard(context, "Permission Name", perm.name) }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan, modifier = Modifier.size(11.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Category: ${perm.protectionCategory.name}", fontSize = 10.sp, color = CyberCyan)

                        if (perm.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(perm.description, fontSize = 11.sp, color = TextSecondary)
                        }

                        if (perm.reason.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Evidence & Assessment: ${perm.reason}", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NativeLibrariesSection(apk: APKInfo, scanResult: ApkScanResult?, context: Context) {
    val libs = scanResult?.nativeLibrariesList ?: emptyList()
    val abis = (scanResult?.abiCoverage ?: apk.supportedAbis).filter { it.isNotBlank() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            NativeLibraryAbiBreakdownCard(
                nativeLibs = libs,
                abiCoverage = abis
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("NATIVE ARCHITECTURE COVERAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val is64bit = abis.any { it.contains("64") }
                    val is32bit = abis.any { !it.contains("64") }
                    InspectionIdentityRow("64-Bit Architecture (arm64-v8a / x86_64)", if (is64bit) "Supported" else "Missing", context, badgeColor = if (is64bit) NeonSuccess else NeonWarning)
                    InspectionIdentityRow("32-Bit Architecture (armeabi-v7a / x86)", if (is32bit) "Supported" else "Missing", context, badgeColor = if (is32bit) NeonSuccess else TextMuted)
                    InspectionIdentityRow("Present ABIs", if (abis.isNotEmpty()) abis.joinToString(", ") else "Pure Java/Kotlin (No .so binaries)", context)
                }
            }
        }

        if (libs.isNotEmpty()) {
            val grouped = libs.groupBy { it.abi }
            grouped.forEach { (abi, abiLibs) ->
                item {
                    Text("ABI: $abi (${abiLibs.size} LIBRARIES)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                }
                items(abiLibs) { lib ->
                    NativeLibItemCard(lib, context)
                }
            }
        } else if (apk.nativeLibraries.isNotEmpty()) {
            apk.nativeLibraries.forEach { (abi, list) ->
                item { Text("ABI: $abi (${list.size} LIBRARIES)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan) }
                items(list) { libName ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("• $libName", fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        } else {
            item {
                Text("Pure Dalvik/ART Java bytecode app - no native .so shared libraries present in APK.", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

@Composable
fun NativeLibItemCard(lib: NativeLibraryInfo, context: Context) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = BorderStroke(1.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lib.libraryName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                Text(formatBytes(lib.fileSize), fontSize = 11.sp, color = CyberCyan)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SHA-256: ${lib.sha256}", fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { copyToClipboard(context, "Library SHA-256", lib.sha256) }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy SHA-256", tint = CyberCyan, modifier = Modifier.size(11.dp))
                }
            }
        }
    }
}

@Composable
fun CertificateAndSignatureSection(apk: APKInfo, scanResult: ApkScanResult?, context: Context) {
    val certs = scanResult?.certificatesList ?: emptyList()
    val signingFiles = scanResult?.fileInfo?.signingRelatedFiles ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Signature Inspection Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("SIGNATURE SCHEME & INTEGRITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val hasSig = signingFiles.isNotEmpty()
                    InspectionIdentityRow(
                        "META-INF Signature Files",
                        if (hasSig) "${signingFiles.size} detected (${signingFiles.joinToString(", ")})" else "Absent",
                        context,
                        badgeColor = if (hasSig) NeonSuccess else NeonWarning
                    )
                    InspectionIdentityRow("v1 Scheme (JAR Signing)", if (signingFiles.any { it.endsWith(".RSA") || it.endsWith(".DSA") || it.endsWith(".EC") }) "PKCS#7 Block Detected" else "Not Present", context)
                    InspectionIdentityRow("v2 / v3 / v4 Scheme Verification", "Advanced APK signature verification unavailable.", context)
                }
            }
        }

        // Certificate Intelligence Cards
        if (certs.isEmpty()) {
            item {
                Text("No X.509 certificates extracted or APK signature absent.", fontSize = 12.sp, color = TextMuted)
            }
        } else {
            items(certs) { cert ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    border = BorderStroke(1.dp, SlateOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("X.509 CERTIFICATE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (cert.isExpired) NeonDanger.copy(alpha = 0.15f) else NeonSuccess.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, if (cert.isExpired) NeonDanger else NeonSuccess)
                            ) {
                                Text(
                                    text = if (cert.isExpired) "EXPIRED" else if (cert.isNotYetValid) "NOT YET VALID" else "VALID",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cert.isExpired) NeonDanger else NeonSuccess,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        InspectionIdentityRow("Certificate Readable", if (cert.isReadable) "YES" else "NO", context)
                        InspectionIdentityRow("Subject", cert.subject, context, copyLabel = "Cert Subject")
                        InspectionIdentityRow("Issuer", cert.issuer, context)
                        InspectionIdentityRow("Serial Number", cert.serialNumber, context)
                        InspectionIdentityRow("Valid From", cert.validFrom, context)
                        InspectionIdentityRow("Valid Until", cert.validUntil, context)
                        InspectionIdentityRow("Signature Algorithm", cert.signatureAlgorithm, context)
                        InspectionIdentityRow("Public Key Algorithm", "${cert.publicKeyAlgorithm} (${cert.keySizeBits} bits)", context)
                        InspectionIdentityRow("SHA-256 Fingerprint", cert.sha256Fingerprint, context, copyLabel = "Cert SHA-256 Fingerprint", isMonospace = true)
                        InspectionIdentityRow("SHA-1 Fingerprint", cert.sha1Fingerprint.ifBlank { "Unavailable" }, context, copyLabel = "Cert SHA-1 Fingerprint", isMonospace = true)
                        InspectionIdentityRow("Self-Signed", if (cert.isSelfSigned) "YES (Self-Generated)" else "NO (CA-Issued)", context)
                        InspectionIdentityRow("Verification Details", cert.verificationDetails, context)
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityFindingsSection(scanResult: ApkScanResult?, onFindingClick: (SecurityFinding) -> Unit) {
    var severityFilter by remember { mutableStateOf<SecuritySeverity?>(null) }
    val findings = scanResult?.securityFindings ?: emptyList()

    val filtered = remember(findings, severityFilter) {
        if (severityFilter == null) findings else findings.filter { it.severity == severityFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (severityFilter == null) CyberCyan else SlateSurfaceVariant,
                    modifier = Modifier.clickable { severityFilter = null }
                ) {
                    Text("ALL (${findings.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (severityFilter == null) Color(0xFF00363B) else TextPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                listOf(SecuritySeverity.CRITICAL, SecuritySeverity.HIGH, SecuritySeverity.MEDIUM, SecuritySeverity.LOW, SecuritySeverity.INFO).forEach { sev ->
                    val count = findings.count { it.severity == sev }
                    val isSel = severityFilter == sev
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) CyberCyan else SlateSurfaceVariant,
                        modifier = Modifier.clickable { severityFilter = sev }
                    ) {
                        Text("${sev.name} ($count)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color(0xFF00363B) else TextPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No security findings for selected filter.", fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
        } else {
            items(filtered) { f ->
                SecurityFindingCard(f, onClick = { onFindingClick(f) })
            }
        }
    }
}

@Composable
fun SecurityFindingCard(finding: SecurityFinding, onClick: () -> Unit) {
    val sevColor = when (finding.severity) {
        SecuritySeverity.CRITICAL -> NeonDanger
        SecuritySeverity.HIGH -> Color(0xFFFF7043)
        SecuritySeverity.MEDIUM -> NeonWarning
        SecuritySeverity.LOW -> Color(0xFF42A5F5)
        SecuritySeverity.INFO -> TextMuted
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = BorderStroke(1.dp, sevColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = sevColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, sevColor)
                ) {
                    Text(
                        text = finding.severity.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = sevColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = mapFindingCategory(finding.category),
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(finding.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Evidence: ${finding.evidence}", fontSize = 10.sp, color = CyberCyan, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
fun StorageAndNetworkSection(apk: APKInfo, scanResult: ApkScanResult?, context: Context) {
    val man = scanResult?.manifestInfo

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Network Security
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("NETWORK SECURITY INSPECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val cleartext = man?.usesCleartextTraffic ?: false
                    InspectionIdentityRow(
                        "Cleartext Traffic (HTTP)",
                        if (cleartext) "PERMITTED (android:usesCleartextTraffic=\"true\")" else "DISABLED (HTTPS Enforced)",
                        context,
                        badgeColor = if (cleartext) NeonDanger else NeonSuccess
                    )
                    InspectionIdentityRow(
                        "Network Security Config",
                        man?.networkSecurityConfig?.let { "Referenced: $it" } ?: "UNAVAILABLE (Default System Trust)",
                        context
                    )
                    val exportedNet = (man?.services ?: emptyList()).filter { it.exported && it.intentActions.any { a -> a.contains("sync", ignoreCase = true) || a.contains("http", ignoreCase = true) } }
                    InspectionIdentityRow(
                        "Exported Network Components",
                        if (exportedNet.isNotEmpty()) "${exportedNet.size} component(s)" else "None detected",
                        context
                    )
                }
            }
        }

        // Data Safety / Storage Flags
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("DATA SAFETY & STORAGE FLAGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    val backup = man?.allowBackup ?: apk.allowsBackup
                    val dataExtraction = man?.dataExtractionRules
                    val debuggable = man?.isDebuggable ?: apk.isDebuggable
                    val allowClear = man?.allowClearUserData
                    val legacyStorage = man?.requestLegacyExternalStorage

                    InspectionIdentityRow("Allow Backup", if (backup) "ENABLED (Evidence: allowBackup=\"true\")" else "DISABLED", context, badgeColor = if (backup) NeonWarning else NeonSuccess)
                    InspectionIdentityRow("Data Extraction Rules", dataExtraction?.let { "ENABLED ($it)" } ?: "UNAVAILABLE", context)
                    InspectionIdentityRow("Debuggable Mode", if (debuggable) "ENABLED (android:debuggable=\"true\")" else "DISABLED", context, badgeColor = if (debuggable) NeonDanger else NeonSuccess)
                    InspectionIdentityRow("Allow Clear User Data", allowClear?.let { if (it) "ENABLED" else "DISABLED" } ?: "UNAVAILABLE", context)
                    InspectionIdentityRow("Legacy External Storage", legacyStorage?.let { if (it) "ENABLED (Scoped storage bypassed)" else "DISABLED" } ?: "UNAVAILABLE", context)
                }
            }
        }
    }
}
