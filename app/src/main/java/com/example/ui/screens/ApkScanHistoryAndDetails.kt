package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.apk.export.ScanReportExporter
import com.example.apk.model.*
import com.example.database.ApkScanEntity
import com.example.security.model.FindingCategory
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import com.example.ui.WorkbenchViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScanDetailSection(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Info),
    FILE_INFO("File Info", Icons.Default.Description),
    HASHES("Hashes", Icons.Default.Fingerprint),
    MANIFEST("Manifest", Icons.Default.Code),
    PERMISSIONS("Permissions", Icons.Default.VpnKey),
    DEX("DEX", Icons.Default.Memory),
    NATIVE_LIBS("Native Libs", Icons.Default.Layers),
    CERTIFICATE("Certificate", Icons.Default.VerifiedUser),
    FINDINGS("Findings", Icons.Default.Security)
}

@Composable
fun ApkHistoryTab(
    viewModel: WorkbenchViewModel,
    onImportApkClick: () -> Unit
) {
    val context = LocalContext.current
    val scans by viewModel.filteredScans.collectAsStateWithLifecycle()
    val allScans by viewModel.allScans.collectAsStateWithLifecycle()
    val currentFilter by viewModel.scanFilter.collectAsStateWithLifecycle()
    val currentSort by viewModel.scanSortOption.collectAsStateWithLifecycle()
    val searchQuery by viewModel.scanSearchQuery.collectAsStateWithLifecycle()
    val selectedHistoryScan by viewModel.selectedHistoryScan.collectAsStateWithLifecycle()
    val currentScanResult by viewModel.currentScanResult.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val exportNotification by viewModel.exportNotificationMessage.collectAsStateWithLifecycle()

    var scanToDelete by remember { mutableStateOf<ApkScanEntity?>(null) }
    var scanToExport by remember { mutableStateOf<ApkScanResult?>(null) }
    var exportIsJson by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // SAF File Creator Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (exportIsJson) "application/json" else "text/plain"
        )
    ) { uri ->
        if (uri != null) {
            viewModel.exportScanReport(uri, exportIsJson)
        }
    }

    LaunchedEffect(exportNotification) {
        exportNotification?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearExportNotification()
        }
    }

    // Delete Confirmation Dialog
    if (scanToDelete != null) {
        AlertDialog(
            onDismissRequest = { scanToDelete = null },
            title = { Text("Delete Scan Record", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete the scan record for \"${scanToDelete?.fileName}\"?\n\nThis will remove the persisted analysis from local history. It will NOT delete or modify the original APK file.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scanToDelete?.let { viewModel.deleteScanRecord(it.scanId) }
                        scanToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError)
                ) {
                    Text("Delete Record", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { scanToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = SlateSurfaceCard
        )
    }

    // If a scan is selected for inspection, show the comprehensive Scan Details / APK Workspace view
    if (selectedHistoryScan != null) {
        val activeResult = currentScanResult ?: ScanReportExporter.fromEntity(selectedHistoryScan!!)
        ApkWorkspaceAndDetailsView(
            scanResult = activeResult,
            scanEntity = selectedHistoryScan!!,
            viewModel = viewModel,
            onBack = { viewModel.clearSelectedHistoryScan() },
            onExportClick = { isJson ->
                exportIsJson = isJson
                scanToExport = activeResult
                val defaultName = "${selectedHistoryScan!!.fileName.removeSuffix(".apk")}_scan_report.${if (isJson) "json" else "txt"}"
                exportLauncher.launch(defaultName)
            }
        )
        return
    }

    // Main Scan History View
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("scan_history_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "APK SCAN HISTORY",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Real analysis records loaded from Room database (${allScans.size} total)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = onImportApkClick,
                    modifier = Modifier.testTag("history_import_new_apk_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Scan", fontSize = 12.sp)
                }
            }
        }

        // Error message banner if any
        if (scanError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CrimsonError.copy(alpha = 0.15f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CrimsonError, CrimsonError)))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = scanError ?: "", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setScanSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_history_search_input"),
                placeholder = { Text("Search by filename, SHA-256, or package name...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setScanSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = SlateOutline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Filter and Sort Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        AssistChip(
                            onClick = { showSortMenu = true },
                            label = {
                                Text(
                                    text = "Sort: ${when (currentSort) {
                                        ScanSortOption.TIMESTAMP_DESC -> "Newest First"
                                        ScanSortOption.TIMESTAMP_ASC -> "Oldest First"
                                        ScanSortOption.NAME_ASC -> "Name (A-Z)"
                                        ScanSortOption.FILE_SIZE_DESC -> "Largest Size"
                                        ScanSortOption.FILE_SIZE_ASC -> "Smallest Size"
                                        ScanSortOption.FINDINGS_COUNT_DESC -> "Most Findings"
                                    }}",
                                    fontSize = 11.sp,
                                    color = CyberCyan
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Sort, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = SlateSurfaceCard),
                            border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = CyberCyan.copy(alpha = 0.5f))
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            containerColor = SlateSurfaceCard
                        ) {
                            ScanSortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (option) {
                                                ScanSortOption.TIMESTAMP_DESC -> "Newest First"
                                                ScanSortOption.TIMESTAMP_ASC -> "Oldest First"
                                                ScanSortOption.NAME_ASC -> "File Name (A-Z)"
                                                ScanSortOption.FILE_SIZE_DESC -> "File Size (Largest)"
                                                ScanSortOption.FILE_SIZE_ASC -> "File Size (Smallest)"
                                                ScanSortOption.FINDINGS_COUNT_DESC -> "Findings Count (Highest)"
                                            },
                                            color = if (currentSort == option) CyberCyan else TextPrimary,
                                            fontWeight = if (currentSort == option) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.selectScanSortOption(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    ScanFilter.values().forEach { filter ->
                    val isSelected = currentFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectScanFilter(filter) },
                        label = {
                            Text(
                                text = when (filter) {
                                    ScanFilter.ALL -> "All Scans"
                                    ScanFilter.COMPLETED -> "Completed"
                                    ScanFilter.FAILED -> "Failed"
                                    ScanFilter.SECURITY_ISSUES -> "Security Issues"
                                    ScanFilter.RECENT -> "Recent (20)"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan,
                            containerColor = SlateSurfaceCard,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = SlateOutline,
                            selectedBorderColor = CyberCyan
                        )
                    )
                }
            }
            }
        }

        // Scans List or Empty State
        if (scans.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(SlateOutline, SlateOutline)))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No scans match your search query" else "No scan records found",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try changing your filter or search terms" else "Import an APK or run sample analysis to populate local scan history.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onImportApkClick,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B))
                        ) {
                            Text("Import APK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(scans, key = { it.scanId }) { scan ->
                ScanHistoryCard(
                    scan = scan,
                    onInspect = { viewModel.selectHistoryScan(scan) },
                    onReanalyze = { viewModel.reanalyzeScan(scan) },
                    onDelete = { scanToDelete = scan },
                    onSelectForDiff = {
                        viewModel.selectForDiff(scan)
                        viewModel.selectApkLabSubTab(com.example.ui.ApkLabSubTab.DIFF)
                    },
                    onExport = { isJson ->
                        exportIsJson = isJson
                        val scanResult = ScanReportExporter.fromEntity(scan)
                        scanToExport = scanResult
                        val defaultName = "${scan.fileName.removeSuffix(".apk")}_scan_report.${if (isJson) "json" else "txt"}"
                        exportLauncher.launch(defaultName)
                    }
                )
            }
        }
    }
}

@Composable
fun ScanHistoryCard(
    scan: ApkScanEntity,
    onInspect: () -> Unit,
    onReanalyze: () -> Unit,
    onDelete: () -> Unit,
    onSelectForDiff: () -> Unit,
    onExport: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val statusColor = when (scan.status.uppercase(Locale.US)) {
        "COMPLETED" -> NeonEmerald
        "FAILED" -> CrimsonError
        "CANCELLED" -> TextMuted
        else -> CyberCyan
    }

    val certStatusColor = when (scan.certificateStatus) {
        "CERTIFICATE_VERIFIED" -> NeonEmerald
        "CERTIFICATE_READ", "SIGNATURE_FILES_PRESENT" -> CyberCyan
        "SIGNATURE_INVALID" -> CrimsonError
        else -> AmberWarning
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() }
            .testTag("scan_card_${scan.scanId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(SlateOutline, SlateOutline)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Filename, Status Badge, More Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scan.fileName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (scan.packageName.isNotBlank()) {
                        Text(
                            text = scan.packageName,
                            fontSize = 11.sp,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = scan.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = SlateSurfaceCard
                    ) {
                        DropdownMenuItem(
                            text = { Text("Inspect Analysis", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onInspect()
                            },
                            leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = CyberCyan) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Report (TXT)", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onExport(false)
                            },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = TextSecondary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Report (JSON)", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onExport(true)
                            },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = TextSecondary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Re-Analyze", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onReanalyze()
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonEmerald) }
                        )
                        DropdownMenuItem(
                            text = { Text("Compare in Diff Tool", color = CyberCyan) },
                            onClick = {
                                showMenu = false
                                onSelectForDiff()
                            },
                            leadingIcon = { Icon(Icons.Default.CompareArrows, contentDescription = null, tint = CyberCyan) }
                        )
                        HorizontalDivider(color = SlateOutline)
                        DropdownMenuItem(
                            text = { Text("Delete Record", color = CrimsonError) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonError) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Grid: Date/Time, File Size, DEX count, Permissions, Findings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetadataPill(
                    label = "Date",
                    value = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(scan.timestamp))
                )
                MetadataPill(
                    label = "Size",
                    value = formatFileSize(scan.fileSize)
                )
                MetadataPill(
                    label = "DEX",
                    value = "${scan.dexCount} files"
                )
                MetadataPill(
                    label = "Perms",
                    value = "${scan.permissionCount}"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Certificate & Security Finding Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = certStatusColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = scan.certificateStatus,
                        fontSize = 11.sp,
                        color = certStatusColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (scan.securityFindingCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CrimsonError.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${scan.securityFindingCount} findings",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonError
                            )
                        }
                    }
                } else {
                    Text(
                        text = "0 findings",
                        fontSize = 11.sp,
                        color = NeonEmerald
                    )
                }
            }

            // SHA-256 with copy button
            if (scan.sha256.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SHA: ${scan.sha256.take(16)}...",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SHA256", scan.sha256))
                            Toast.makeText(context, "SHA-256 copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy SHA-256", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (!scan.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Error: ${scan.errorMessage}",
                    fontSize = 11.sp,
                    color = CrimsonError,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onInspect,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Inspect", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onReanalyze,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-Analyze", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { onExport(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun MetadataPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SlateSurfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(text = label, fontSize = 9.sp, color = TextMuted)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }
}

@Composable
fun ApkWorkspaceAndDetailsView(
    scanResult: ApkScanResult,
    scanEntity: ApkScanEntity,
    viewModel: WorkbenchViewModel,
    onBack: () -> Unit,
    onExportClick: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf(ScanDetailSection.OVERVIEW) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("scan_details_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Navigation bar with Back button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("details_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to History", tint = CyberCyan)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "APK WORKSPACE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = scanEntity.fileName,
                            fontSize = 12.sp,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onExportClick(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark, contentColor = CyberCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export TXT", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onExportClick(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Safe APK Workspace Representation Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apk_workspace_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CyberCyan, SlateOutline)))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Safe APK Inspection Workspace",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Text(
                                text = "Read-Only Environment: Execution and installation disabled for safety.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonEmerald.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = scanEntity.status,
                                color = NeonEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Workspace specs grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WorkspaceSpecTag(label = "Filename", value = scanEntity.fileName)
                        WorkspaceSpecTag(label = "Size", value = formatFileSize(scanEntity.fileSize))
                        WorkspaceSpecTag(
                            label = "Analyzed At",
                            value = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(scanEntity.timestamp))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // URI & SHA256
                    Text(
                        text = "URI: ${scanEntity.uriString ?: "Local storage"}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "SHA-256: ${scanEntity.sha256}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Section Jump Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionJumpButton("View Analysis", Icons.Default.Analytics) {
                            selectedSection = ScanDetailSection.OVERVIEW
                        }
                        ActionJumpButton("View Security Findings", Icons.Default.Security) {
                            selectedSection = ScanDetailSection.FINDINGS
                        }
                        ActionJumpButton("View Manifest", Icons.Default.Code) {
                            selectedSection = ScanDetailSection.MANIFEST
                        }
                        ActionJumpButton("View DEX", Icons.Default.Memory) {
                            selectedSection = ScanDetailSection.DEX
                        }
                        ActionJumpButton("View Certificate", Icons.Default.VerifiedUser) {
                            selectedSection = ScanDetailSection.CERTIFICATE
                        }
                    }
                }
            }
        }

        // Section Selector Tabs
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedSection.ordinal,
                containerColor = SlateSurfaceCard,
                contentColor = CyberCyan,
                edgePadding = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                ScanDetailSection.values().forEach { section ->
                    Tab(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(section.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(section.title, fontSize = 12.sp, fontWeight = if (selectedSection == section) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }
            }
        }

        // Render Active Section
        item {
            when (selectedSection) {
                ScanDetailSection.OVERVIEW -> DetailsOverviewSection(scanResult, scanEntity)
                ScanDetailSection.FILE_INFO -> DetailsFileInfoSection(scanResult, scanEntity)
                ScanDetailSection.HASHES -> DetailsHashesSection(scanResult, scanEntity)
                ScanDetailSection.MANIFEST -> DetailsManifestSection(scanResult.manifestInfo)
                ScanDetailSection.PERMISSIONS -> DetailsPermissionsSection(scanResult.permissionsList)
                ScanDetailSection.DEX -> DetailsDexSection(scanResult.dexList)
                ScanDetailSection.NATIVE_LIBS -> DetailsNativeLibsSection(scanResult.nativeLibrariesList, scanResult.abiCoverage)
                ScanDetailSection.CERTIFICATE -> DetailsCertificateSection(scanResult.certificatesList)
                ScanDetailSection.FINDINGS -> DetailsSecurityFindingsSection(scanResult.securityFindings)
            }
        }
    }
}

@Composable
fun ActionJumpButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = CyberCyan)
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, fontSize = 11.sp)
    }
}

@Composable
fun WorkspaceSpecTag(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SlateSurfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(text = label, fontSize = 9.sp, color = TextMuted)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}

// =========================================================================
// 1. OVERVIEW SECTION
// =========================================================================
@Composable
fun DetailsOverviewSection(scanResult: ApkScanResult, scanEntity: ApkScanEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("APPLICATION SUMMARY", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)

            DetailRow("Package Name", scanResult.manifestInfo.packageName.ifBlank { "Unavailable" })
            DetailRow("Application Label", scanResult.manifestInfo.appName.ifBlank { "Unavailable" })
            DetailRow("Version Name", scanResult.manifestInfo.versionName.ifBlank { "Unavailable" })
            DetailRow("Version Code", "${scanResult.manifestInfo.versionCode}")
            DetailRow("Min SDK", "${scanResult.manifestInfo.minSdk}")
            DetailRow("Target SDK", "${scanResult.manifestInfo.targetSdk}")
            DetailRow("Compile SDK", scanResult.manifestInfo.compileSdk?.toString() ?: "Unavailable")
            DetailRow("Debuggable", if (scanResult.manifestInfo.isDebuggable) "YES (Warning)" else "NO")
            DetailRow("Allow Backup", if (scanResult.manifestInfo.allowBackup) "YES" else "NO")
            DetailRow("Uses Cleartext Traffic", if (scanResult.manifestInfo.usesCleartextTraffic) "YES (Warning)" else "NO")

            HorizontalDivider(color = SlateOutline)

            Text("ARCHIVE SUMMARY", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
            DetailRow("DEX Files", "${scanResult.dexList.size.coerceAtLeast(scanEntity.dexCount)}")
            DetailRow("Native Architectures", if (scanResult.abiCoverage.isEmpty()) scanEntity.abiSummary else scanResult.abiCoverage.joinToString())
            DetailRow("Declared Permissions", "${scanResult.permissionsList.size.coerceAtLeast(scanEntity.permissionCount)}")
            DetailRow("Security Findings", "${scanResult.securityFindings.size.coerceAtLeast(scanEntity.securityFindingCount)}")
        }
    }
}

// =========================================================================
// 2. FILE INFORMATION SECTION
// =========================================================================
@Composable
fun DetailsFileInfoSection(scanResult: ApkScanResult, scanEntity: ApkScanEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("FILE ATTRIBUTES", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)

            DetailRow("File Name", scanEntity.fileName)
            DetailRow("File Size (Bytes)", "${scanEntity.fileSize} bytes")
            DetailRow("File Size (Formatted)", formatFileSize(scanEntity.fileSize))
            DetailRow("MIME Type", scanResult.fileInfo.mimeType)
            DetailRow("URI / Origin", scanEntity.uriString ?: "Local internal sandbox")
            DetailRow("Zip Entries", "${scanResult.fileInfo.totalZipEntries}")
            DetailRow("DEX Count", "${scanResult.fileInfo.dexCount}")
            DetailRow("Native Libraries Count", "${scanResult.fileInfo.nativeLibraryCount}")
            DetailRow("Scan ID", scanEntity.scanId)
            DetailRow("Analysis Status", scanEntity.status)
        }
    }
}

// =========================================================================
// 3. HASHES SECTION
// =========================================================================
@Composable
fun DetailsHashesSection(scanResult: ApkScanResult, scanEntity: ApkScanEntity) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("CRYPTOGRAPHIC CHECKSUMS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)

            HashItem(
                title = "SHA-256",
                hash = scanEntity.sha256.ifBlank { scanResult.fileInfo.sha256 },
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SHA256", it))
                    Toast.makeText(context, "SHA-256 copied", Toast.LENGTH_SHORT).show()
                }
            )

            HashItem(
                title = "MD5",
                hash = scanEntity.md5.ifBlank { scanResult.fileInfo.md5 },
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MD5", it))
                    Toast.makeText(context, "MD5 copied", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun HashItem(title: String, hash: String, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            IconButton(onClick = { onCopy(hash) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            text = hash.ifBlank { "Unavailable" },
            fontSize = 12.sp,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// =========================================================================
// 4. MANIFEST VIEW SECTION
// =========================================================================
@Composable
fun DetailsManifestSection(manifest: ManifestInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("STRUCTURED ANDROID MANIFEST", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)

            DetailRow("Package", manifest.packageName.ifBlank { "Unavailable" })
            DetailRow("Version Name", manifest.versionName.ifBlank { "Unavailable" })
            DetailRow("Version Code", "${manifest.versionCode}")
            DetailRow("Min SDK", "${manifest.minSdk}")
            DetailRow("Target SDK", "${manifest.targetSdk}")
            DetailRow("Compile SDK", manifest.compileSdk?.toString() ?: "Unavailable")
            DetailRow("Application Label", manifest.appName.ifBlank { "Unavailable" })
            DetailRow("Exported Components", "${manifest.exportedComponentsCount}")

            HorizontalDivider(color = SlateOutline)

            Text("COMPONENTS SUMMARY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            DetailRow("Activities", if (manifest.activities.isEmpty()) "Unavailable / 0 detected" else "${manifest.activities.size} declared")
            DetailRow("Services", if (manifest.services.isEmpty()) "Unavailable / 0 detected" else "${manifest.services.size} declared")
            DetailRow("Broadcast Receivers", if (manifest.receivers.isEmpty()) "Unavailable / 0 detected" else "${manifest.receivers.size} declared")
            DetailRow("Content Providers", if (manifest.providers.isEmpty()) "Unavailable / 0 detected" else "${manifest.providers.size} declared")

            if (manifest.usesFeatures.isNotEmpty()) {
                HorizontalDivider(color = SlateOutline)
                Text("FEATURES REQUESTED (${manifest.usesFeatures.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                manifest.usesFeatures.forEach { feature ->
                    Text(
                        text = "• $feature",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// =========================================================================
// 5. PERMISSIONS SECTION
// =========================================================================
@Composable
fun DetailsPermissionsSection(permissions: List<ScanPermissionInfo>) {
    if (permissions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Security, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Permissions Declared", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("This APK does not request any Android framework permissions.", color = TextSecondary, fontSize = 12.sp)
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "DECLARED PERMISSIONS (${permissions.size})",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            letterSpacing = 1.sp
        )

        permissions.forEach { perm ->
            val riskColor = when (perm.riskIndicator) {
                RiskLevel.CRITICAL, RiskLevel.HIGH -> CrimsonError
                RiskLevel.WARNING -> AmberWarning
                RiskLevel.INFO -> TextSecondary
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = perm.simpleName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = riskColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = perm.riskIndicator.name,
                                color = riskColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = perm.name,
                        fontSize = 11.sp,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Category: ${perm.protectionCategory.name}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )

                    if (perm.reason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = perm.reason,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 6. DEX VIEW SECTION
// =========================================================================
@Composable
fun DetailsDexSection(dexList: List<DexInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "DETECTED DEX FILES (${dexList.size})",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            letterSpacing = 1.sp
        )

        // Mandatory explicit semantic notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant)
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Advanced DEX semantic parsing unavailable.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        if (dexList.isEmpty()) {
            Text("No DEX files parsed from archive.", color = TextMuted, fontSize = 12.sp)
        } else {
            dexList.forEach { dex ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = dex.fileName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = formatFileSize(dex.fileSize), fontSize = 12.sp, color = CyberCyan)
                        }

                        DetailRow("DEX Magic", dex.magic.ifBlank { "Unavailable" })
                        DetailRow("DEX Version", dex.version.ifBlank { "Unavailable" })
                        DetailRow("Adler32 Checksum", dex.adler32Checksum.ifBlank { "Unavailable" })
                        DetailRow("SHA-1 Signature", dex.sha1Signature.ifBlank { "Unavailable" })
                        DetailRow("Class Definitions", "${dex.classDefsCount}")
                        DetailRow("SHA-256", dex.sha256.take(20) + "...")
                    }
                }
            }
        }
    }
}

// =========================================================================
// 7. NATIVE LIBRARIES SECTION
// =========================================================================
@Composable
fun DetailsNativeLibsSection(libs: List<NativeLibraryInfo>, abiCoverage: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "NATIVE BINARIES (.SO)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            letterSpacing = 1.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ABI COVERAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (abiCoverage.isEmpty()) "Pure Java / DEX (No native binaries detected)" else abiCoverage.joinToString(", "),
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (libs.isNotEmpty()) {
            libs.forEach { lib ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = lib.libraryName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Surface(shape = RoundedCornerShape(4.dp), color = CyberCyan.copy(alpha = 0.15f)) {
                                Text(text = lib.abi, color = CyberCyan, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        DetailRow("Size", formatFileSize(lib.fileSize))
                        DetailRow("SHA-256", lib.sha256.take(20) + "...")
                    }
                }
            }
        }
    }
}

// =========================================================================
// 8. CERTIFICATE VIEW SECTION
// =========================================================================
@Composable
fun DetailsCertificateSection(certs: List<ScanCertificateInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "CODE SIGNING & CERTIFICATE INTEGRITY",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            letterSpacing = 1.sp
        )

        if (certs.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("SIGNATURE_UNAVAILABLE", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("No X.509 certificate signatures were found in this APK archive.", color = TextSecondary, fontSize = 12.sp)
                }
            }
            return
        }

        certs.forEach { cert ->
            val statusColor = when (cert.status) {
                CertificateStatus.CERTIFICATE_VERIFIED -> NeonEmerald
                CertificateStatus.CERTIFICATE_READ, CertificateStatus.SIGNATURE_FILES_PRESENT -> CyberCyan
                CertificateStatus.SIGNATURE_INVALID -> CrimsonError
                CertificateStatus.SIGNATURE_UNAVAILABLE -> AmberWarning
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("X.509 CERTIFICATE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                            Text(
                                text = cert.status.name,
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    DetailRow("Subject", cert.subject.ifBlank { "Unavailable" })
                    DetailRow("Issuer", cert.issuer.ifBlank { "Unavailable" })
                    DetailRow("Serial Number", cert.serialNumber.ifBlank { "Unavailable" })
                    DetailRow("Validity Period", "${cert.validFrom.ifBlank { "Unavailable" }} to ${cert.validUntil.ifBlank { "Unavailable" }}")
                    DetailRow("Signature Algorithm", cert.signatureAlgorithm.ifBlank { "Unavailable" })
                    DetailRow("Public Key Algorithm", cert.publicKeyAlgorithm.ifBlank { "Unavailable" })
                    DetailRow("Key Size", "${cert.keySizeBits} bits")
                    DetailRow("Self-Signed", if (cert.isSelfSigned) "YES" else "NO")

                    HorizontalDivider(color = SlateOutline)

                    Text("FINGERPRINTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    DetailRow("SHA-256", cert.sha256Fingerprint.ifBlank { "Unavailable" })
                    DetailRow("SHA-1", cert.sha1Fingerprint.ifBlank { "Unavailable" })
                    if (cert.verificationDetails.isNotBlank()) {
                        DetailRow("Verification Details", cert.verificationDetails)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 9. SECURITY FINDINGS VIEW SECTION
// =========================================================================
@Composable
fun DetailsSecurityFindingsSection(findings: List<SecurityFinding>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "REAL SECURITY FINDINGS (${findings.size})",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            letterSpacing = 1.sp
        )

        if (findings.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Security Anomalies Detected", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("No critical, high, or medium security findings were observed in this APK archive.", color = TextSecondary, fontSize = 12.sp)
                }
            }
            return
        }

        // Group findings by CRITICAL, HIGH, MEDIUM, LOW, INFO
        val severities = listOf(
            SecuritySeverity.CRITICAL,
            SecuritySeverity.HIGH,
            SecuritySeverity.MEDIUM,
            SecuritySeverity.LOW,
            SecuritySeverity.INFO
        )

        severities.forEach { severity ->
            val matching = findings.filter { it.severity == severity }
            if (matching.isNotEmpty()) {
                val groupColor = when (severity) {
                    SecuritySeverity.CRITICAL, SecuritySeverity.HIGH -> CrimsonError
                    SecuritySeverity.MEDIUM -> AmberWarning
                    SecuritySeverity.LOW -> CyberCyan
                    SecuritySeverity.INFO -> TextSecondary
                }

                Text(
                    text = "${severity.name} (${matching.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = groupColor
                )

                matching.forEach { finding ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(groupColor.copy(alpha = 0.5f), SlateOutline)))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = finding.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(shape = RoundedCornerShape(6.dp), color = groupColor.copy(alpha = 0.15f)) {
                                    Text(
                                        text = finding.severity.name,
                                        color = groupColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = finding.description,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )

                            if (finding.evidence.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SlateSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("EVIDENCE FROM APK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                        Text(text = finding.evidence, fontSize = 11.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            if (finding.recommendation.isNotBlank()) {
                                Column {
                                    Text("RECOMMENDATION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text(text = finding.recommendation, fontSize = 11.sp, color = NeonEmerald)
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
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.weight(0.4f))
        Text(
            text = value,
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            fontFamily = if (value.contains(".") || value.length > 20) FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
        else -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
    }
}
