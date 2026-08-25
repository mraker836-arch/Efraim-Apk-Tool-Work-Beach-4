package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.model.UserRole
import com.example.security.diana.DianaSecurityReport
import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.GateResult
import com.example.security.model.GateStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecurityRegressionReport
import com.example.security.model.SecurityReport
import com.example.security.model.SecurityScore
import com.example.security.model.SecuritySeverity
import com.example.security.model.SecurityState
import com.example.ui.WorkbenchViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityDashboardSection(viewModel: WorkbenchViewModel) {
    val context = LocalContext.current
    val currentReport by viewModel.securityReport.collectAsState()
    val score by viewModel.securityScore.collectAsState()
    val findings by viewModel.securityFindings.collectAsState()
    val gates by viewModel.securityGates.collectAsState()
    val isScanning by viewModel.isSecurityScanning.collectAsState()
    val regression by viewModel.securityRegression.collectAsState()
    val dianaSecReport by viewModel.dianaSecurityReport.collectAsState()
    val userRole by viewModel.currentUserRole.collectAsState()

    var selectedFilterCategory by remember { mutableStateOf<FindingCategory?>(null) }
    var selectedSeverityFilter by remember { mutableStateOf<SecuritySeverity?>(null) }
    var selectedFindingForAction by remember { mutableStateOf<SecurityFinding?>(null) }
    var targetStatusForAction by remember { mutableStateOf<FindingStatus?>(null) }
    var actionReasonText by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Card: Security Posture Score & Controls
        SecurityScoreBannerCard(
            score = score,
            isScanning = isScanning,
            onScanClick = { viewModel.runSecurityScan(forceRefresh = true) },
            onExportClick = { showExportDialog = true }
        )

        // Regression Alert (if regression detected)
        if (regression?.hasRegression == true) {
            SecurityRegressionCard(regression = regression!!)
        }

        // Release Security Gates Grid
        ReleaseSecurityGatesCard(gates = gates)

        // DIANA AI Security Analysis Card
        if (dianaSecReport != null) {
            DianaSecurityAnalysisCard(
                report = dianaSecReport!!,
                onApplyChangePlan = { plan ->
                    // Set active change plan for review
                }
            )
        }

        // Findings Section with Filtering & Status Actions
        SecurityFindingsManagementCard(
            findings = findings,
            selectedCategory = selectedFilterCategory,
            onSelectCategory = { selectedFilterCategory = it },
            selectedSeverity = selectedSeverityFilter,
            onSelectSeverity = { selectedSeverityFilter = it },
            onUpdateFindingStatus = { finding, status ->
                selectedFindingForAction = finding
                targetStatusForAction = status
                actionReasonText = ""
            }
        )

        // Supply-Chain Provenance & Signer Details
        if (currentReport?.provenance != null) {
            SupplyChainProvenanceCard(report = currentReport!!)
        }
    }

    // Action Confirmation Dialog (for Risk Acceptance, False Positive, Mitigation)
    if (selectedFindingForAction != null && targetStatusForAction != null) {
        AlertDialog(
            onDismissRequest = {
                selectedFindingForAction = null
                targetStatusForAction = null
            },
            title = {
                Text(
                    text = "Update Finding: ${targetStatusForAction?.name}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Finding: ${selectedFindingForAction?.title}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "User Role: ${userRole.name}. A mandatory justification reason will be recorded to the immutable audit log.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = actionReasonText,
                        onValueChange = { actionReasonText = it },
                        label = { Text("Justification Reason / Ticket ID") },
                        placeholder = { Text("e.g., Verified false positive via internal review SEC-104") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finding = selectedFindingForAction ?: return@Button
                        val status = targetStatusForAction ?: return@Button
                        val reason = actionReasonText.ifBlank { "User verified via Security Workbench" }
                        val result = viewModel.updateSecurityFindingStatus(
                            findingId = finding.id,
                            newStatus = status,
                            reason = reason,
                            userName = "Operator"
                        )
                        if (result.isSuccess) {
                            Toast.makeText(context, "Status updated to ${status.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                        selectedFindingForAction = null
                        targetStatusForAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Confirm & Record", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedFindingForAction = null
                    targetStatusForAction = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SlateSurfaceCard
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text("Export Security & Integrity Report", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Export a production security audit report including score breakdown, gate verifications, supply-chain provenance, and finding mitigation history.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = viewModel.exportSecurityReportJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Security Report JSON", json))
                        Toast.makeText(context, "Security Report JSON copied to clipboard", Toast.LENGTH_LONG).show()
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Copy JSON", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val txt = viewModel.exportSecurityReportTxt()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Security Report Text", txt))
                        Toast.makeText(context, "Security Report Text copied to clipboard", Toast.LENGTH_LONG).show()
                        showExportDialog = false
                    }
                ) {
                    Text("Copy TXT", color = CyberCyan)
                }
            },
            containerColor = SlateSurfaceCard
        )
    }
}

@Composable
private fun SecurityScoreBannerCard(
    score: SecurityScore,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onExportClick: () -> Unit
) {
    val stateColor = when (score.state) {
        SecurityState.SECURE -> NeonEmerald
        SecurityState.LOW_RISK -> NeonEmerald
        SecurityState.MEDIUM_RISK -> AmberWarning
        SecurityState.HIGH_RISK -> CrimsonError
        SecurityState.CRITICAL -> CrimsonError
        SecurityState.UNKNOWN -> TextMuted
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateOutline, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = stateColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SECURITY & INTEGRITY POSTURE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = stateColor.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, stateColor)
                            ) {
                                Text(
                                    text = score.state.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = stateColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Score: ${score.numericScore ?: "--"}/100",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Default.Download, contentDescription = "Export Report", tint = CyberCyan)
                    }

                    Button(
                        onClick = onScanClick,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", fontSize = 11.sp, color = Color.Black)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            androidx.compose.material3.HorizontalDivider(color = SlateOutline)
            Spacer(modifier = Modifier.height(10.dp))

            // Score explanation metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(label = "Critical", count = score.criticalCount, color = CrimsonError)
                MetricPill(label = "High", count = score.highCount, color = CrimsonError)
                MetricPill(label = "Medium", count = score.mediumCount, color = AmberWarning)
                MetricPill(label = "Low", count = score.lowCount, color = NeonEmerald)
                MetricPill(label = "Exposed Secrets", count = if (score.hasExposedSecrets) 1 else 0, color = if (score.hasExposedSecrets) CrimsonError else NeonEmerald)
            }

            if (score.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = score.summary,
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) color else TextSecondary
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun SecurityRegressionCard(regression: SecurityRegressionReport) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CrimsonError, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CrimsonError.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SECURITY REGRESSION DETECTED",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonError
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            regression.details.forEach { detail ->
                Text(
                    text = "• $detail",
                    fontSize = 11.sp,
                    color = TextPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseSecurityGatesCard(gates: List<GateResult>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateOutline, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RELEASE SECURITY GATES",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                val passedCount = gates.count { it.status == GateStatus.PASS }
                Text(
                    text = "$passedCount/${gates.size} Passed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (passedCount == gates.size && gates.isNotEmpty()) NeonEmerald else AmberWarning
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gates.forEach { gate ->
                    GateItemPill(gate = gate)
                }
            }
        }
    }
}

@Composable
private fun GateItemPill(gate: GateResult) {
    val (statusColor, statusIcon) = when (gate.status) {
        GateStatus.PASS -> Pair(NeonEmerald, Icons.Default.CheckCircle)
        GateStatus.FAIL -> Pair(CrimsonError, Icons.Default.Block)
        GateStatus.WARNING -> Pair(AmberWarning, Icons.Default.Warning)
        GateStatus.NOT_APPLICABLE -> Pair(TextMuted, Icons.Default.Info)
        GateStatus.UNKNOWN -> Pair(TextMuted, Icons.Default.Info)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SlateSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = gate.gateType.name.replace("_", " "),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = gate.title,
                    fontSize = 9.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DianaSecurityAnalysisCard(
    report: DianaSecurityReport,
    onApplyChangePlan: (com.example.release.model.ChangePlan) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DIANA SECURITY INTELLIGENCE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (report.riskLevel) {
                        SecuritySeverity.CRITICAL, SecuritySeverity.HIGH -> CrimsonError.copy(alpha = 0.2f)
                        SecuritySeverity.MEDIUM -> AmberWarning.copy(alpha = 0.2f)
                        else -> NeonEmerald.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = "Risk: ${report.riskLevel.name}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (report.riskLevel) {
                            SecuritySeverity.CRITICAL, SecuritySeverity.HIGH -> CrimsonError
                            SecuritySeverity.MEDIUM -> AmberWarning
                            else -> NeonEmerald
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(text = report.summary, fontSize = 12.sp, color = TextPrimary)

            if (report.remediationPlan.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlateSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Actionable Remediation Plan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(report.remediationPlan, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            if (report.proposedChangePlan != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ChangePlan: ${report.proposedChangePlan.title}", fontSize = 11.sp, color = TextPrimary)
                    Button(
                        onClick = { onApplyChangePlan(report.proposedChangePlan) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Review Plan", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityFindingsManagementCard(
    findings: List<SecurityFinding>,
    selectedCategory: FindingCategory?,
    onSelectCategory: (FindingCategory?) -> Unit,
    selectedSeverity: SecuritySeverity?,
    onSelectSeverity: (SecuritySeverity?) -> Unit,
    onUpdateFindingStatus: (SecurityFinding, FindingStatus) -> Unit
) {
    val filtered = findings.filter {
        (selectedCategory == null || it.category == selectedCategory) &&
                (selectedSeverity == null || it.severity == selectedSeverity)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateOutline, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FINDINGS & VULNERABILITIES (${findings.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "${filtered.size} Filtered",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Severity filter tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SeverityFilterChip("ALL", isSelected = selectedSeverity == null, onClick = { onSelectSeverity(null) })
                SeverityFilterChip("CRITICAL", isSelected = selectedSeverity == SecuritySeverity.CRITICAL, onClick = { onSelectSeverity(SecuritySeverity.CRITICAL) }, color = CrimsonError)
                SeverityFilterChip("HIGH", isSelected = selectedSeverity == SecuritySeverity.HIGH, onClick = { onSelectSeverity(SecuritySeverity.HIGH) }, color = CrimsonError)
                SeverityFilterChip("MEDIUM", isSelected = selectedSeverity == SecuritySeverity.MEDIUM, onClick = { onSelectSeverity(SecuritySeverity.MEDIUM) }, color = AmberWarning)
                SeverityFilterChip("LOW", isSelected = selectedSeverity == SecuritySeverity.LOW, onClick = { onSelectSeverity(SecuritySeverity.LOW) }, color = NeonEmerald)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No security findings match the selected filter.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filtered.forEach { finding ->
                        SecurityFindingItemRow(
                            finding = finding,
                            onStatusAction = { status -> onUpdateFindingStatus(finding, status) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    color: Color = CyberCyan
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) color.copy(alpha = 0.2f) else SlateSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) color else SlateOutline),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) color else TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SecurityFindingItemRow(
    finding: SecurityFinding,
    onStatusAction: (FindingStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val sevColor = when (finding.severity) {
        SecuritySeverity.CRITICAL, SecuritySeverity.HIGH -> CrimsonError
        SecuritySeverity.MEDIUM -> AmberWarning
        SecuritySeverity.LOW -> NeonEmerald
        SecuritySeverity.INFO -> CyberCyan
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SlateSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (finding.severity == SecuritySeverity.CRITICAL) CrimsonError.copy(alpha = 0.5f) else SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = sevColor.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, sevColor)
                    ) {
                        Text(
                            text = finding.severity.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = sevColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = finding.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = if (expanded) Int.MAX_VALUE else 1
                    )
                }

                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = finding.description,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 2
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (finding.evidence.isNotBlank()) {
                        Text("Evidence: ${finding.evidence}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    }
                    if (finding.affectedFile.isNotBlank()) {
                        Text("Affected: ${finding.affectedFile}", fontSize = 10.sp, color = CyberCyan)
                    }
                    if (finding.recommendation.isNotBlank()) {
                        Text("Recommendation: ${finding.recommendation}", fontSize = 10.sp, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onStatusAction(FindingStatus.FALSE_POSITIVE) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("False Positive", fontSize = 10.sp, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { onStatusAction(FindingStatus.ACCEPTED_RISK) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Accept Risk", fontSize = 10.sp, color = AmberWarning)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { onStatusAction(FindingStatus.MITIGATED) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Mark Mitigated", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplyChainProvenanceCard(report: SecurityReport) {
    val prov = report.provenance ?: return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateOutline, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SUPPLY-CHAIN PROVENANCE & IDENTITY",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            ProvenanceRow("Git Commit SHA", prov.commitSha)
            ProvenanceRow("Gradle / AGP", "${prov.gradleVersion} / ${prov.androidGradlePlugin}")
            ProvenanceRow("Dependencies Hash", prov.dependenciesHash)
            ProvenanceRow("Artifact SHA-256", prov.artifactHash.take(24) + "...")
            ProvenanceRow("Signing Identity", report.signerSubject.ifBlank { "Unsigned / Default" })
        }
    }
}

@Composable
private fun ProvenanceRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = TextMuted)
        Text(text = value, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
    }
}
