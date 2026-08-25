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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.inference.AiMode
import com.example.ui.WorkbenchViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(viewModel: WorkbenchViewModel) {
    var selectedMode by remember { mutableStateOf(viewModel.getAiMode()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("EFRAIM WORKBENCH SETTINGS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Inference configuration, security policies, and cryptographic storage", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }

        // AI Engine Mode Selection
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI INFERENCE ENGINE MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Configure how DIANA routes intelligence queries for static analysis and rebuild planning.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == AiMode.AUTO,
                            onClick = {
                                selectedMode = AiMode.AUTO
                                viewModel.setAiMode(AiMode.AUTO)
                            },
                            label = { Text("Auto (Local + Fallback)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = Color(0xFF00363B)
                            ),
                            modifier = Modifier.testTag("settings_mode_auto")
                        )

                        FilterChip(
                            selected = selectedMode == AiMode.OFFLINE,
                            onClick = {
                                selectedMode = AiMode.OFFLINE
                                viewModel.setAiMode(AiMode.OFFLINE)
                            },
                            label = { Text("100% Offline (GGUF)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonEmerald,
                                selectedLabelColor = Color(0xFF00381B)
                            ),
                            modifier = Modifier.testTag("settings_mode_offline")
                        )

                        FilterChip(
                            selected = selectedMode == AiMode.CLOUD,
                            onClick = {
                                selectedMode = AiMode.CLOUD
                                viewModel.setAiMode(AiMode.CLOUD)
                            },
                            label = { Text("Cloud (Gemini)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricPurple,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("settings_mode_cloud")
                        )
                    }
                }
            }
        }

        // Security & Safety Policy Statement
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AUTHORIZATION & PRIVACY POLICY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald, letterSpacing = 1.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Ownership: This application is intended for APKs that the user owns or is authorized to modify.\n" +
                               "• Safety Guardrails: Excludes credential theft, malware injection, stealth persistence, DRM/license bypass, security bypass, or anti-analysis evasion.\n" +
                               "• Static Sandboxing: Imported APKs are parsed in isolated sandbox memory without dynamic execution.\n" +
                               "• Cryptographic Privacy: Private signing keys are stored exclusively in local PKCS#12 Android storage and never uploaded to cloud AI.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // About Application
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ABOUT EFRAIM WORKBENCH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("EFRAIM APK WORKBENCH TOOL M", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Version 1.0.0 (API 36 Ready)", fontSize = 12.sp, color = CyberCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Architecture: Zero-dependency Kotlin AXML/DEX static analysis engine, pure ASN.1 DER PKCS#7 JAR v1 signature builder, 4-byte ZipAlign boundary memory mapper, and on-device DIANA intelligence layer.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Developer & Creator Credit
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("developer_credit_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "DEVELOPER & CREATOR CREDIT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Build and Create By:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Allandoni A. ONG",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Software Engineer",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeonEmerald
                    )
                }
            }
        }
    }
}
