package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.diana.DianaDiagnosticReport
import com.example.diagnostics.model.AlertNotification
import com.example.diagnostics.model.ComponentHealth
import com.example.diagnostics.model.DiagnosticErrorRecord
import com.example.diagnostics.model.ErrorCategory
import com.example.diagnostics.model.ErrorSeverity
import com.example.diagnostics.model.ErrorStatus
import com.example.diagnostics.model.HealthState
import com.example.diagnostics.model.Incident
import com.example.diagnostics.model.IncidentStatus
import com.example.diagnostics.model.PerformanceCategory
import com.example.diagnostics.model.PerformanceMetricRecord
import com.example.diagnostics.model.PerformanceRating
import com.example.diagnostics.model.ReleaseHealthSummary
import com.example.diagnostics.model.UserRole
import com.example.release.model.ChangePlan
import com.example.release.model.ChangePlanStatus
import com.example.ui.WorkbenchViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class MonitorTabSection(val title: String) {
    HEALTH("Health & Releases"),
    SECURITY("Security & Integrity"),
    ERROR_CENTER("Error Center"),
    INCIDENTS("Incidents"),
    PERFORMANCE("Performance"),
    EXPORT_AUDIT("Export & Audit")
}

@Composable
fun MonitorScreen(viewModel: WorkbenchViewModel) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf(MonitorTabSection.HEALTH) }

    val systemHealth by viewModel.systemHealth.collectAsState()
    val errors by viewModel.diagnosticErrors.collectAsState()
    val incidents by viewModel.incidents.collectAsState()
    val releases by viewModel.releaseHistory.collectAsState()
    val perfMetrics by viewModel.performanceMetrics.collectAsState()
    val settings by viewModel.observabilitySettings.collectAsState()
    val userRole by viewModel.currentUserRole.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val activeChangePlan by viewModel.activeChangePlan.collectAsState()
    val selectedDianaReport by viewModel.selectedDiagnosticReport.collectAsState()
    val systemMetrics by viewModel.systemMetrics.collectAsState()

    var showReportDialog by remember { mutableStateOf(false) }
    var reportDialogContent by remember { mutableStateOf("") }
    var reportDialogTitle by remember { mutableStateOf("") }

    var showNewIncidentDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "PRODUCTION OBSERVABILITY & DIAGNOSTICS",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Health engine, incident manager, telemetry & DIANA root-cause analysis",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlateSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                ) {
                    Text(
                        text = "ROLE: ${userRole.name}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Section Tabs
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedSection.ordinal,
                containerColor = SlateSurfaceCard,
                contentColor = CyberCyan,
                edgePadding = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SlateOutline, RoundedCornerShape(12.dp))
            ) {
                MonitorTabSection.values().forEach { section ->
                    Tab(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        text = {
                            Text(
                                section.title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedSection == section) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedSection == section) CyberCyan else TextSecondary
                            )
                        }
                    )
                }
            }
        }

        // Content based on selected section
        when (selectedSection) {
            MonitorTabSection.HEALTH -> {
                item {
                    SystemHealthOverviewSection(
                        health = systemHealth,
                        onRefresh = { viewModel.runSystemHealthCheck() }
                    )
                }

                item {
                    ReleaseHealthSection(releases = releases)
                }

                item {
                    HardwareTelemetrySection(metrics = systemMetrics)
                }
            }

            MonitorTabSection.SECURITY -> {
                item {
                    SecurityDashboardSection(viewModel = viewModel)
                }
            }

            MonitorTabSection.ERROR_CENTER -> {
                item {
                    ErrorCenterSection(
                        errors = errors,
                        onStatusChange = { id, status, notes ->
                            viewModel.updateErrorStatus(id, status, notes)
                        },
                        onAnalyzeError = { err ->
                            viewModel.analyzeErrorWithDiana(err)
                        }
                    )
                }
            }

            MonitorTabSection.INCIDENTS -> {
                item {
                    IncidentsSection(
                        incidents = incidents,
                        onOpenNewDialog = { showNewIncidentDialog = true },
                        onUpdateStatus = { id, status, rootCause, res ->
                            viewModel.updateIncidentStatus(id, status, rootCause, res)
                        },
                        onAnalyzeIncident = { inc ->
                            viewModel.analyzeIncidentWithDiana(inc)
                        }
                    )
                }
            }

            MonitorTabSection.PERFORMANCE -> {
                item {
                    PerformanceMonitorSection(
                        metrics = perfMetrics,
                        onClearMetrics = { viewModel.clearLocalDiagnostics() }
                    )
                }
            }

            MonitorTabSection.EXPORT_AUDIT -> {
                item {
                    ExportAndAuditSection(
                        userRole = userRole,
                        auditLogs = auditLogs,
                        settings = settings,
                        onRoleChange = { viewModel.setUserRole(it) },
                        onUpdateSettings = { viewModel.updateObservabilitySettings(it) },
                        onExportJson = {
                            val json = viewModel.exportDiagnosticReportJson()
                            reportDialogTitle = "Sanitized Diagnostic Report (JSON)"
                            reportDialogContent = json
                            showReportDialog = true
                        },
                        onExportTxt = {
                            val txt = viewModel.exportDiagnosticReportTxt()
                            reportDialogTitle = "Sanitized Diagnostic Report (TXT)"
                            reportDialogContent = txt
                            showReportDialog = true
                        },
                        onClearAll = {
                            viewModel.clearLocalDiagnostics()
                            Toast.makeText(context, "Local diagnostics history purged", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // DIANA Diagnostic Analysis Dialog
    if (selectedDianaReport != null) {
        DianaDiagnosticModal(
            report = selectedDianaReport!!,
            activePlan = activeChangePlan,
            onDismiss = { viewModel.dismissSelectedDiagnosticReport() },
            onApprovePlan = { plan -> viewModel.approveChangePlan(plan) },
            onApplyPlan = { plan -> viewModel.applyApprovedChangePlan(plan) }
        )
    }

    // Exported Report View Dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(reportDialogTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberCyan) },
            text = {
                Column(modifier = Modifier.height(350.dp)) {
                    Text("Sensitive credentials, keys & tokens have been safely [REDACTED].", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlateSurfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            item {
                                Text(
                                    text = reportDialogContent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("DiagnosticReport", reportDialogContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied sanitized report to clipboard", Toast.LENGTH_SHORT).show()
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B))
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReportDialog = false }) {
                    Text("Close")
                }
            },
            containerColor = SlateSurfaceCard
        )
    }

    // New Incident Dialog
    if (showNewIncidentDialog) {
        var title by remember { mutableStateOf("") }
        var module by remember { mutableStateOf("ApkLab") }
        var severity by remember { mutableStateOf(ErrorSeverity.ERROR) }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewIncidentDialog = false },
            title = { Text("Declare Incident", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AmberWarning) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Incident Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SlateOutline
                        )
                    )
                    OutlinedTextField(
                        value = module,
                        onValueChange = { module = it },
                        label = { Text("Affected Module") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SlateOutline
                        )
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Initial Assessment / Details") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SlateOutline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.createIncident(title, severity, module, desc)
                            showNewIncidentDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = Color.Black)
                ) {
                    Text("Declare Incident")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewIncidentDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = SlateSurfaceCard
        )
    }
}

// ----------------------------------------------------
// Section 1: System & Release Health
// ----------------------------------------------------
@Composable
fun SystemHealthOverviewSection(
    health: com.example.diagnostics.model.SystemHealthReport,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = health.overallState.symbol,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (health.overallState) {
                            HealthState.HEALTHY -> NeonEmerald
                            HealthState.DEGRADED -> AmberWarning
                            HealthState.UNHEALTHY -> CrimsonError
                            HealthState.UNKNOWN -> TextSecondary
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "SYSTEM HEALTH: ${health.overallState.label}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (health.overallState) {
                                HealthState.HEALTHY -> NeonEmerald
                                HealthState.DEGRADED -> AmberWarning
                                HealthState.UNHEALTHY -> CrimsonError
                                HealthState.UNKNOWN -> TextSecondary
                            }
                        )
                        Text(
                            "Live evaluation of core runtime, database, storage & CI pipeline",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Component Grid
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                health.components.forEach { comp ->
                    ComponentHealthRow(comp)
                }
            }
        }
    }
}

@Composable
fun ComponentHealthRow(comp: ComponentHealth) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SlateSurfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = comp.state.symbol,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (comp.state) {
                        HealthState.HEALTHY -> NeonEmerald
                        HealthState.DEGRADED -> AmberWarning
                        HealthState.UNHEALTHY -> CrimsonError
                        HealthState.UNKNOWN -> TextMuted
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = comp.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = comp.details,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (comp.state) {
                    HealthState.HEALTHY -> NeonEmerald.copy(alpha = 0.15f)
                    HealthState.DEGRADED -> AmberWarning.copy(alpha = 0.15f)
                    HealthState.UNHEALTHY -> CrimsonError.copy(alpha = 0.15f)
                    HealthState.UNKNOWN -> SlateSurfaceVariant
                }
            ) {
                Text(
                    text = comp.state.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (comp.state) {
                        HealthState.HEALTHY -> NeonEmerald
                        HealthState.DEGRADED -> AmberWarning
                        HealthState.UNHEALTHY -> CrimsonError
                        HealthState.UNKNOWN -> TextMuted
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ReleaseHealthSection(releases: List<ReleaseHealthSummary>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "RELEASE OBSERVABILITY & REGRESSION DETECTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (releases.isEmpty()) {
                Text("No releases recorded.", fontSize = 12.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    releases.forEach { rel ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SlateSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Release v${rel.versionName} (Build ${rel.versionCode})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (rel.criticalErrorCount == 0) NeonEmerald.copy(alpha = 0.2f) else CrimsonError.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            if (rel.criticalErrorCount == 0) "STATUS: HEALTHY" else "STATUS: AT RISK",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rel.criticalErrorCount == 0) NeonEmerald else CrimsonError,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SmallBadge("BUILD: ${rel.buildStatus}", if (rel.buildStatus == "PASS") NeonEmerald else CrimsonError)
                                    SmallBadge("TESTS: ${rel.testStatus}", if (rel.testStatus == "PASS") NeonEmerald else CrimsonError)
                                    SmallBadge("SIGNING: ${rel.signingStatus}", NeonEmerald)
                                    SmallBadge("CRITICAL: ${rel.criticalErrorCount}", if (rel.criticalErrorCount == 0) NeonEmerald else CrimsonError)
                                }

                                if (rel.regressions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    rel.regressions.forEach { reg ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(reg, fontSize = 11.sp, color = AmberWarning)
                                        }
                                    }
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
fun HardwareTelemetrySection(metrics: com.example.core.SystemMetrics) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("RUNTIME HARDWARE TELEMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricPurple, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("JVM Heap", "${metrics.allocatedRamMb}/${metrics.maxRamMb}MB", CyberCyan, Modifier.weight(1f))
                MetricCard("Storage Free", "${metrics.availableStorageMb}MB", NeonEmerald, Modifier.weight(1f))
                MetricCard("Threads", "${metrics.activeThreadsCount} Active", ElectricPurple, Modifier.weight(1f))
            }
        }
    }
}

// ----------------------------------------------------
// Section 2: Error Center
// ----------------------------------------------------
@Composable
fun ErrorCenterSection(
    errors: List<DiagnosticErrorRecord>,
    onStatusChange: (String, ErrorStatus, String) -> Unit,
    onAnalyzeError: (DiagnosticErrorRecord) -> Unit
) {
    var severityFilter by remember { mutableStateOf<ErrorSeverity?>(null) }
    val filtered = if (severityFilter != null) errors.filter { it.severity == severityFilter } else errors

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DIAGNOSTIC ERROR REGISTRY (${errors.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.sp
                )

                Text(
                    "Critical: ${errors.count { it.severity == ErrorSeverity.CRITICAL }}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (errors.any { it.severity == ErrorSeverity.CRITICAL }) CrimsonError else NeonEmerald
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = severityFilter == null,
                    onClick = { severityFilter = null },
                    label = { Text("ALL (${errors.size})", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberCyan, selectedLabelColor = Color(0xFF00363B))
                )
                ErrorSeverity.values().forEach { sev ->
                    val count = errors.count { it.severity == sev }
                    FilterChip(
                        selected = severityFilter == sev,
                        onClick = { severityFilter = sev },
                        label = { Text("${sev.name} ($count)", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (sev) {
                                ErrorSeverity.CRITICAL -> CrimsonError
                                ErrorSeverity.ERROR -> AmberWarning
                                ErrorSeverity.WARNING -> ElectricPurple
                                ErrorSeverity.INFO -> CyberCyan
                            },
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Text("No errors matching filter criteria.", fontSize = 12.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { err ->
                        ErrorRecordCard(
                            error = err,
                            onStatusChange = { newStatus -> onStatusChange(err.id, newStatus, "") },
                            onAnalyze = { onAnalyzeError(err) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorRecordCard(
    error: DiagnosticErrorRecord,
    onStatusChange: (ErrorStatus) -> Unit,
    onAnalyze: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SlateSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (error.severity == ErrorSeverity.CRITICAL) CrimsonError.copy(alpha = 0.5f) else SlateOutline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (error.severity) {
                            ErrorSeverity.CRITICAL -> CrimsonError
                            ErrorSeverity.ERROR -> AmberWarning
                            ErrorSeverity.WARNING -> ElectricPurple
                            ErrorSeverity.INFO -> CyberCyan
                        }
                    ) {
                        Text(
                            text = error.severity.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "[${error.category.label}] in ${error.module}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (error.status) {
                        ErrorStatus.NEW -> AmberWarning.copy(alpha = 0.2f)
                        ErrorStatus.INVESTIGATING -> ElectricPurple.copy(alpha = 0.2f)
                        ErrorStatus.RESOLVED -> NeonEmerald.copy(alpha = 0.2f)
                        ErrorStatus.IGNORED -> SlateSurfaceCard
                    }
                ) {
                    Text(
                        error.status.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (error.status) {
                            ErrorStatus.NEW -> AmberWarning
                            ErrorStatus.INVESTIGATING -> ElectricPurple
                            ErrorStatus.RESOLVED -> NeonEmerald
                            ErrorStatus.IGNORED -> TextMuted
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(error.message, fontSize = 11.sp, color = TextSecondary)

            if (error.stackTrace.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error.stackTrace.lines().take(2).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Switcher
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (error.status != ErrorStatus.RESOLVED) {
                        SmallActionBtn("Resolve", NeonEmerald) { onStatusChange(ErrorStatus.RESOLVED) }
                    }
                    if (error.status != ErrorStatus.INVESTIGATING) {
                        SmallActionBtn("Investigate", ElectricPurple) { onStatusChange(ErrorStatus.INVESTIGATING) }
                    }
                    if (error.status != ErrorStatus.IGNORED) {
                        SmallActionBtn("Ignore", TextMuted) { onStatusChange(ErrorStatus.IGNORED) }
                    }
                }

                OutlinedButton(
                    onClick = onAnalyze,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DIANA Triage", fontSize = 10.sp, color = ElectricPurple)
                }
            }
        }
    }
}

// ----------------------------------------------------
// Section 3: Incidents
// ----------------------------------------------------
@Composable
fun IncidentsSection(
    incidents: List<Incident>,
    onOpenNewDialog: () -> Unit,
    onUpdateStatus: (String, IncidentStatus, String, String) -> Unit,
    onAnalyzeIncident: (Incident) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("INCIDENT DESK & LIFECYCLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberWarning, letterSpacing = 1.sp)
                    Text("Structured timeline: Detection → Triage → Root Cause → Fix → Resolution", fontSize = 10.sp, color = TextMuted)
                }

                Button(
                    onClick = onOpenNewDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Declare", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (incidents.isEmpty()) {
                Text("No active or historical incidents recorded.", fontSize = 12.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    incidents.forEach { inc ->
                        IncidentItemCard(
                            incident = inc,
                            onUpdateStatus = { st -> onUpdateStatus(inc.id, st, "", "") },
                            onAnalyze = { onAnalyzeIncident(inc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IncidentItemCard(
    incident: Incident,
    onUpdateStatus: (IncidentStatus) -> Unit,
    onAnalyze: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SlateSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (incident.status == IncidentStatus.OPEN) AmberWarning.copy(alpha = 0.6f) else SlateOutline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    incident.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (incident.status) {
                        IncidentStatus.OPEN -> AmberWarning.copy(alpha = 0.2f)
                        IncidentStatus.INVESTIGATING -> ElectricPurple.copy(alpha = 0.2f)
                        IncidentStatus.MITIGATED -> CyberCyan.copy(alpha = 0.2f)
                        IncidentStatus.RESOLVED -> NeonEmerald.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        incident.status.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (incident.status) {
                            IncidentStatus.OPEN -> AmberWarning
                            IncidentStatus.INVESTIGATING -> ElectricPurple
                            IncidentStatus.MITIGATED -> CyberCyan
                            IncidentStatus.RESOLVED -> NeonEmerald
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Module: ${incident.affectedModule} | Release: ${incident.affectedRelease}", fontSize = 10.sp, color = TextMuted)

            Spacer(modifier = Modifier.height(8.dp))

            // Timeline summary
            Text("Incident Timeline (${incident.timeline.size} events):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
            incident.timeline.forEach { ev ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("• [${ev.stage}] ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Text(ev.description, fontSize = 9.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (incident.status != IncidentStatus.RESOLVED) {
                        SmallActionBtn("Resolve", NeonEmerald) { onUpdateStatus(IncidentStatus.RESOLVED) }
                    }
                    if (incident.status != IncidentStatus.INVESTIGATING) {
                        SmallActionBtn("Investigate", ElectricPurple) { onUpdateStatus(IncidentStatus.INVESTIGATING) }
                    }
                    if (incident.status != IncidentStatus.MITIGATED) {
                        SmallActionBtn("Mitigate", CyberCyan) { onUpdateStatus(IncidentStatus.MITIGATED) }
                    }
                }

                OutlinedButton(
                    onClick = onAnalyze,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("DIANA Analysis", fontSize = 10.sp, color = ElectricPurple)
                }
            }
        }
    }
}

// ----------------------------------------------------
// Section 4: Performance Monitor
// ----------------------------------------------------
@Composable
fun PerformanceMonitorSection(
    metrics: List<PerformanceMetricRecord>,
    onClearMetrics: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PERFORMANCE METRICS & LATENCY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.sp
                )
                OutlinedButton(
                    onClick = onClearMetrics,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("Clear", fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (metrics.isEmpty()) {
                Text(
                    "No operation benchmarks captured yet. Import, scan, or rebuild an APK to track timings.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    metrics.take(10).forEach { m ->
                        PerformanceRow(m)
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceRow(metric: PerformanceMetricRecord) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SlateSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(metric.category.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("${metric.durationMs} ms ${if (metric.contextInfo.isNotEmpty()) "(${metric.contextInfo})" else ""}", fontSize = 10.sp, color = TextSecondary)
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (metric.rating) {
                    PerformanceRating.FAST -> NeonEmerald.copy(alpha = 0.2f)
                    PerformanceRating.NORMAL -> CyberCyan.copy(alpha = 0.2f)
                    PerformanceRating.SLOW -> AmberWarning.copy(alpha = 0.2f)
                    PerformanceRating.FAILED -> CrimsonError.copy(alpha = 0.2f)
                    PerformanceRating.NOT_AVAILABLE -> SlateSurfaceCard
                }
            ) {
                Text(
                    metric.rating.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (metric.rating) {
                        PerformanceRating.FAST -> NeonEmerald
                        PerformanceRating.NORMAL -> CyberCyan
                        PerformanceRating.SLOW -> AmberWarning
                        PerformanceRating.FAILED -> CrimsonError
                        PerformanceRating.NOT_AVAILABLE -> TextMuted
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ----------------------------------------------------
// Section 5: Export & Audit
// ----------------------------------------------------
@Composable
fun ExportAndAuditSection(
    userRole: UserRole,
    auditLogs: List<com.example.diagnostics.engine.AuditLogEntry>,
    settings: com.example.diagnostics.model.ObservabilitySettings,
    onRoleChange: (UserRole) -> Unit,
    onUpdateSettings: (com.example.diagnostics.model.ObservabilitySettings) -> Unit,
    onExportJson: () -> Unit,
    onExportTxt: () -> Unit,
    onClearAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Export Actions Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("EXPORT SANITIZED DIAGNOSTIC REPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Generates an authenticated report containing health telemetry, errors, incidents, and release history. All passwords, API keys, and private tokens are automatically sanitized.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onExportJson,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON")
                    }

                    Button(
                        onClick = onExportTxt,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export TXT")
                    }
                }
            }
        }

        // Role & Settings Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ACCESS CONTROL & OBSERVABILITY POLICIES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Text("Active User Role:", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserRole.values().forEach { role ->
                        FilterChip(
                            selected = userRole == role,
                            onClick = { onRoleChange(role) },
                            label = { Text(role.name, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = Color(0xFF00363B)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Purge Local Diagnostic History")
                }
            }
        }

        // Audit Log Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("DIAGNOSTIC AUDIT TRAIL (${auditLogs.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (auditLogs.isEmpty()) {
                    Text("No audit events recorded yet.", fontSize = 11.sp, color = TextMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        auditLogs.take(8).forEach { log ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SlateSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(log.action, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                        Text(log.userRole, fontSize = 9.sp, color = TextMuted)
                                    }
                                    Text(log.details, fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// DIANA Diagnostic Modal (Root Cause & ChangePlan)
// ----------------------------------------------------
@Composable
fun DianaDiagnosticModal(
    report: DianaDiagnosticReport,
    activePlan: ChangePlan?,
    onDismiss: () -> Unit,
    onApprovePlan: (ChangePlan) -> Unit,
    onApplyPlan: (ChangePlan) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DIANA Diagnostic Analysis", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(report.summary, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlateSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("ROOT CAUSE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberWarning)
                            Text(report.rootCause, fontSize = 11.sp, color = TextPrimary)
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlateSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("RECOMMENDED FIX:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                            Text(report.recommendedFix, fontSize = 11.sp, color = TextPrimary)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallBadge("RISK: ${report.risk}", if (report.risk == "HIGH") CrimsonError else AmberWarning)
                        SmallBadge("CONFIDENCE: ${report.confidence}%", NeonEmerald)
                    }
                }

                if (report.suggestedChangePlan != null) {
                    val plan = activePlan ?: report.suggestedChangePlan
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SlateSurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("PROPOSED CHANGE PLAN (User Approval Required):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricPurple)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(plan.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(plan.description, fontSize = 10.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (plan.status == ChangePlanStatus.PENDING) {
                                        Button(
                                            onClick = { onApprovePlan(plan) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Approve Plan", fontSize = 10.sp)
                                        }
                                    } else if (plan.status == ChangePlanStatus.APPROVED) {
                                        Button(
                                            onClick = { onApplyPlan(plan) },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Apply Fix", fontSize = 10.sp)
                                        }
                                    } else {
                                        Text("Applied ✓", fontSize = 10.sp, color = NeonEmerald, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        containerColor = SlateSurfaceCard
    )
}

// ----------------------------------------------------
// UI Helpers
// ----------------------------------------------------
@Composable
fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = SlateSurfaceVariant
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun SmallBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SmallActionBtn(text: String, color: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
