package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.builder.ProjectBuildConfig
import com.example.apk.model.AbiOption
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import com.example.release.model.ChangePlanStatus
import com.example.release.model.CiFailureRecord
import com.example.apk.model.BuildStatus
import com.example.apk.model.BuildType
import com.example.ui.WorkbenchViewModel
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BuildScreen(viewModel: WorkbenchViewModel) {
    var projectName by remember { mutableStateOf("CustomApp") }
    var packageName by remember { mutableStateOf("com.example.customapp") }
    var versionName by remember { mutableStateOf("1.0.0") }
    var versionCode by remember { mutableStateOf("100") }
    var buildType by remember { mutableStateOf(BuildType.DEBUG) }
    var selectedAbis by remember { mutableStateOf(setOf(AbiOption.ARM64_V8A, AbiOption.X86_64)) }

    val buildStatus by viewModel.buildStatus.collectAsState()
    val buildLogs by viewModel.buildLogs.collectAsState()
    val buildResult by viewModel.projectBuildResult.collectAsState()

    val ciWorkflowState by viewModel.ciWorkflowState.collectAsState()
    val ciErrors by viewModel.ciErrors.collectAsState()
    val activeChangePlan by viewModel.activeChangePlan.collectAsState()
    val ciOperationStatus by viewModel.ciOperationStatus.collectAsState()

    var showTokenDialog by remember { mutableStateOf(false) }
    var githubToken by remember { mutableStateOf("") }
    var customReleaseNotes by remember { mutableStateOf("") }

    val isRunning = buildStatus != BuildStatus.IDLE && buildStatus != BuildStatus.COMPLETED && buildStatus != BuildStatus.FAILED

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("NATIVE APK BUILD ENGINE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Compile and assemble new standalone Android APK packages", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }

        // Project Configuration Form
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PROJECT SPECIFICATIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)

                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth().testTag("build_project_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SlateOutline
                        )
                    )

                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("Package Name (e.g. com.myorg.app)") },
                        modifier = Modifier.fillMaxWidth().testTag("build_package_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = SlateOutline
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("Version Name") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = SlateOutline
                            )
                        )
                        OutlinedTextField(
                            value = versionCode,
                            onValueChange = { versionCode = it },
                            label = { Text("Version Code") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = SlateOutline
                            )
                        )
                    }

                    // Build Type Selection
                    Text("Build Variant", fontSize = 12.sp, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = buildType == BuildType.DEBUG,
                            onClick = { buildType = BuildType.DEBUG },
                            label = { Text("Debug (Unsigned/Dev Key)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = Color(0xFF00363B)
                            )
                        )
                        FilterChip(
                            selected = buildType == BuildType.RELEASE,
                            onClick = { buildType = BuildType.RELEASE },
                            label = { Text("Release (Signed)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonEmerald,
                                selectedLabelColor = Color(0xFF00381B)
                            )
                        )
                    }

                    // ABIs Selection
                    Text("Target CPU Architectures (ABIs)", fontSize = 12.sp, color = TextMuted)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AbiOption.values().forEach { abi ->
                            val isChecked = selectedAbis.contains(abi)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedAbis = if (checked) selectedAbis + abi else selectedAbis - abi
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                                )
                                Text(abi.label, fontSize = 13.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Build Button
        item {
            Button(
                onClick = {
                    val cfg = ProjectBuildConfig(
                        projectName = projectName,
                        packageName = packageName,
                        versionName = versionName,
                        versionCode = versionCode.toLongOrNull() ?: 100L,
                        buildType = buildType,
                        targetAbis = selectedAbis.toList().ifEmpty { listOf(AbiOption.ARM64_V8A) }
                    )
                    viewModel.executeProjectBuild(cfg)
                },
                enabled = !isRunning && projectName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("execute_build_button"),
                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color(0xFF00381B))
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF00381B), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Building APK (${buildStatus.name})...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compile & Build APK Package", fontWeight = FontWeight.Bold)
                }
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.cancelProjectBuild() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("cancel_build_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonError)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Build Process", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Build logs terminal
        if (buildLogs.isNotEmpty()) {
            item {
                Text("BUILD OUTPUT TERMINAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF050810)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        buildLogs.forEach { log ->
                            Text(
                                text = "• [${log.stage.name}] ${log.message}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (log.isError) CrimsonError else NeonEmerald,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Phase 9C: GITHUB ACTIONS ANDROID BUILD & RELEASE PIPELINE
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("GITHUB ACTIONS CI/CD & RELEASE PIPELINE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Automated verification, test execution, signing & GitHub Release", fontSize = 11.sp, color = CyberCyan)
                        }
                    }

                    // Metadata Grid
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF070B14),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GitHub Repository:", fontSize = 12.sp, color = TextMuted)
                                Text(ciWorkflowState.repositoryName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Branch / Commit:", fontSize = 12.sp, color = TextMuted)
                                Text("${ciWorkflowState.branchName} (${ciWorkflowState.latestCommitSha})", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = CyberCyan)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Workflow Status:", fontSize = 12.sp, color = TextMuted)
                                Text(ciWorkflowState.status.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Build / Test Status:", fontSize = 12.sp, color = TextMuted)
                                Text("${ciWorkflowState.buildStatus} | ${ciWorkflowState.testStatus}", fontSize = 12.sp, color = TextPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Signing Status:", fontSize = 12.sp, color = TextMuted)
                                Text(ciWorkflowState.signingStatus, fontSize = 12.sp, color = NeonEmerald)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Release Target:", fontSize = 12.sp, color = TextMuted)
                                Text("v${ciWorkflowState.latestVersion} (Production APK + AAB)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                            }
                        }
                    }

                    // Operation status message
                    if (ciOperationStatus != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberCyan.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(ciOperationStatus!!, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Action Buttons (RUN BUILD, RUN RELEASE, VIEW ACTIONS, VIEW ARTIFACTS, VIEW RELEASE)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerCiBuild(githubToken) },
                            modifier = Modifier.weight(1f).testTag("ci_run_build_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("RUN BUILD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.triggerCiProductionRelease(githubToken, customReleaseNotes) },
                            modifier = Modifier.weight(1f).testTag("ci_run_release_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color(0xFF00381B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("RUN RELEASE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.sendChatMessage("DIANA, show GitHub Actions workflow overview for android-release.yml.")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurfaceVariant, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("VIEW ACTIONS", fontSize = 10.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.sendChatMessage("DIANA, show details for release artifact efraim-apk-workbench-release.apk.")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurfaceVariant, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("VIEW ARTIFACTS", fontSize = 10.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.sendChatMessage("DIANA, display latest GitHub Release notes and checksums.")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurfaceVariant, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("VIEW RELEASE", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // DIANA CI Error Center & Proposed ChangePlan (Section 26 & 27)
        if (ciErrors.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C0D12)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonError.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CI ERROR CENTER (${ciErrors.size} Diagnostic Event)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonError)
                        }

                        ciErrors.forEach { failure ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F0608),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonError.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text("[${failure.category.label}] Stage: ${failure.stageName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(failure.summary, fontSize = 11.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = { viewModel.analyzeCiFailureWithDiana(failure) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ANALYZE WITH DIANA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active DIANA ChangePlan for Approval
        if (activeChangePlan != null) {
            item {
                val plan = activeChangePlan!!
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("DIANA PROPOSED CHANGE PLAN", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(plan.title, fontSize = 11.sp, color = NeonEmerald)
                            }
                        }

                        Text(plan.description, fontSize = 12.sp, color = TextSecondary)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF050810),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Text("DIFF PREVIEW:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(plan.diffPreview, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CyberCyan)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (plan.status == ChangePlanStatus.PENDING) {
                                Button(
                                    onClick = { viewModel.approveChangePlan(plan) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color(0xFF00381B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("APPROVE PLAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (plan.status == ChangePlanStatus.APPROVED) {
                                Button(
                                    onClick = { viewModel.applyApprovedChangePlan(plan) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("APPLY FIX & RE-RUN CI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (plan.status == ChangePlanStatus.APPLIED) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NeonEmerald.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fix applied successfully. CI ready for rerun.", fontSize = 11.sp, color = NeonEmerald)
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
