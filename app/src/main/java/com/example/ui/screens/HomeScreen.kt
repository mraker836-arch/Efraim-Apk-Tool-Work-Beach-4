package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.model.APKInfo
import com.example.ui.AppTab
import com.example.ui.WorkbenchViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanDark
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
fun HomeScreen(
    viewModel: WorkbenchViewModel,
    onNavigateToTab: (AppTab) -> Unit,
    onImportApkClick: () -> Unit
) {
    val currentApk by viewModel.currentApk.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val systemMetrics by viewModel.systemMetrics.collectAsState()
    val dianaReport by viewModel.dianaReport.collectAsState()
    val isModelLoaded = viewModel.inferenceService.isLocalModelLoaded()
    val loadedModel = viewModel.inferenceService.getLoadedModelName()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_banner_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(CyberCyanDark.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CyberCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "EFRAIM APK WORKBENCH TOOL M",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = TextPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "LOCAL-FIRST APK BUILD & REBUILD ENGINE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CyberCyan
                                    )
                                }
                            }

                            // AI Engine Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isModelLoaded) NeonEmerald.copy(alpha = 0.15f) else ElectricPurple.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isModelLoaded) NeonEmerald.copy(alpha = 0.5f) else ElectricPurple.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isModelLoaded) NeonEmerald else ElectricPurple)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isModelLoaded) "GGUF AI Active" else "Auto AI Mode",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isModelLoaded) NeonEmerald else ElectricPurple
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Professional Android APK static inspection, sandboxed rebuilding, cryptographic v1 signing, and on-device DIANA intelligence.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick telemetry row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricChip(
                                label = "JVM RAM",
                                value = "${systemMetrics.allocatedRamMb} / ${systemMetrics.maxRamMb} MB",
                                color = CyberCyan
                            )
                            MetricChip(
                                label = "Storage Free",
                                value = "${systemMetrics.availableStorageMb} MB",
                                color = NeonEmerald
                            )
                            MetricChip(
                                label = "Sandbox",
                                value = "Isolated",
                                color = ElectricPurple
                            )
                        }
                    }
                }
            }
        }

        // Active APK Card
        item {
            Text(
                text = "ACTIVE APK WORKSPACE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (isScanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CyberCyan)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Extracting and analyzing APK binaries...", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            } else if (currentApk != null) {
                val apk = currentApk!!
                ActiveApkCard(
                    apk = apk,
                    securityScore = dianaReport?.overallSecurityScore ?: 85,
                    onInspectClick = {
                        viewModel.selectTab(AppTab.APK_LAB)
                    },
                    onRebuildClick = {
                        viewModel.selectTab(AppTab.APK_LAB)
                        viewModel.selectApkLabSubTab(com.example.ui.ApkLabSubTab.REBUILD)
                    }
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No APK currently loaded",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Import any Android APK file or generate a sample APK package.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onImportApkClick,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B)),
                                modifier = Modifier.testTag("home_import_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import APK", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.loadSampleApk() },
                                modifier = Modifier.testTag("home_sample_button")
                            ) {
                                Text("Load Sample APK")
                            }
                        }
                    }
                }
            }
        }

        // Quick Action Grid
        item {
            Text(
                text = "PRODUCTION OBSERVABILITY & HEALTH",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            val healthReport by viewModel.systemHealth.collectAsState()
            val incidentsList by viewModel.incidents.collectAsState()
            val errorsList by viewModel.diagnosticErrors.collectAsState()
            val releases by viewModel.releaseHistory.collectAsState()
            val latestRelease = releases.firstOrNull()
            val criticalCount = errorsList.count { it.severity.name == "CRITICAL" }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("production_health_card"),
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
                                text = healthReport.overallState.symbol,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (healthReport.overallState.name) {
                                    "HEALTHY" -> NeonEmerald
                                    "DEGRADED" -> AmberWarning
                                    "UNHEALTHY" -> CrimsonError
                                    else -> TextSecondary
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SYSTEM ${healthReport.overallState.label}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (healthReport.overallState.name) {
                                    "HEALTHY" -> NeonEmerald
                                    "DEGRADED" -> AmberWarning
                                    "UNHEALTHY" -> CrimsonError
                                    else -> TextSecondary
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.runSystemHealthCheck()
                                viewModel.selectTab(AppTab.MONITOR)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Diagnostics Center", fontSize = 11.sp, color = CyberCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricChip(
                            label = "LATEST RELEASE",
                            value = "v${latestRelease?.versionName ?: "1.0"}",
                            color = CyberCyan
                        )
                        MetricChip(
                            label = "CRITICAL ERRORS",
                            value = "$criticalCount",
                            color = if (criticalCount == 0) NeonEmerald else CrimsonError
                        )
                        MetricChip(
                            label = "OPEN INCIDENTS",
                            value = "${incidentsList.count { it.status.name != "RESOLVED" }}",
                            color = if (incidentsList.any { it.status.name != "RESOLVED" }) AmberWarning else NeonEmerald
                        )
                    }
                }
            }
        }

        // Workbench Modules
        item {
            Text(
                text = "WORKBENCH MODULES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionModuleCard(
                        title = "DIANA AI",
                        subtitle = "Security & Architecture Triage",
                        icon = Icons.Default.Security,
                        color = ElectricPurple,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToTab(AppTab.DIANA) }
                    )
                    ActionModuleCard(
                        title = "APK Lab",
                        subtitle = "Inspect, Rebuild & Sign",
                        icon = Icons.Default.Build,
                        color = CyberCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToTab(AppTab.APK_LAB) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionModuleCard(
                        title = "Native Builder",
                        subtitle = "Compile New Packages",
                        icon = Icons.Default.PlayArrow,
                        color = NeonEmerald,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToTab(AppTab.BUILD) }
                    )
                    ActionModuleCard(
                        title = "Model Manager",
                        subtitle = "Manage Local GGUFs",
                        icon = Icons.Default.Info,
                        color = AmberWarning,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToTab(AppTab.MODELS) }
                    )
                }
            }
        }

        // Security Policy Notice
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Local-First Sandbox: All extraction, bytecode decoding, alignment, and signing execute purely in memory and on-device sandbox. Private keys never leave this device.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveApkCard(
    apk: APKInfo,
    securityScore: Int,
    onInspectClick: () -> Unit,
    onRebuildClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_apk_card"),
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
                    Text(
                        text = apk.appName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = apk.packageName,
                        fontSize = 12.sp,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Score Gauge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        securityScore >= 80 -> NeonEmerald.copy(alpha = 0.15f)
                        securityScore >= 50 -> AmberWarning.copy(alpha = 0.15f)
                        else -> CrimsonError.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = "Score: $securityScore/100",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            securityScore >= 80 -> NeonEmerald
                            securityScore >= 50 -> AmberWarning
                            else -> CrimsonError
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick metadata grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoTag(label = "Version", value = "v${apk.versionName}")
                InfoTag(label = "Target SDK", value = "API ${apk.targetSdk}")
                InfoTag(label = "DEX Classes", value = "${apk.totalDexClasses}")
                InfoTag(label = "Status", value = apk.signingStatus.name)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onInspectClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark, contentColor = CyberCyan)
                ) {
                    Text("Inspect APK", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onRebuildClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B))
                ) {
                    Text("Rebuild APK", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InfoTag(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SlateSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(text = label, fontSize = 9.sp, color = TextMuted)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }
}

@Composable
fun MetricChip(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SlateSurfaceVariant.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(text = label, fontSize = 9.sp, color = TextMuted)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ActionModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("action_module_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
