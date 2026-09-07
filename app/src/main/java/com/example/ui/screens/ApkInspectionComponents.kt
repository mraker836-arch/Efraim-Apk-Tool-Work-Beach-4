package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.apk.inspection.SecurityScoreBreakdown
import com.example.apk.model.RiskLevel
import com.example.security.model.FindingCategory
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import com.example.ui.theme.*

val NeonSuccess = NeonEmerald
val NeonWarning = AmberWarning
val NeonDanger = CrimsonError

fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Composable
fun InspectionIdentityRow(
    label: String,
    value: String,
    context: Context,
    copyLabel: String? = null,
    isMonospace: Boolean = false,
    badgeColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextMuted,
            modifier = Modifier.weight(0.42f)
        )
        Row(
            modifier = Modifier.weight(0.58f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (badgeColor != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = value,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                Text(
                    text = value,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            if (copyLabel != null && value != "Unavailable") {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { copyToClipboard(context, copyLabel, value) },
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("copy_${copyLabel.lowercase().replace(" ", "_")}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $copyLabel",
                        tint = CyberCyan,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SecuritySummaryCard(scoreBreakdown: SecurityScoreBreakdown) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = BorderStroke(1.dp, if (scoreBreakdown.isClean) NeonSuccess else CyberCyan),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_summary_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SECURITY SCORE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = scoreBreakdown.riskSummary,
                        fontSize = 11.sp,
                        color = if (scoreBreakdown.isClean) NeonSuccess else TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                scoreBreakdown.score >= 90 -> NeonSuccess.copy(alpha = 0.15f)
                                scoreBreakdown.score >= 60 -> NeonWarning.copy(alpha = 0.15f)
                                else -> NeonDanger.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${scoreBreakdown.score}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            scoreBreakdown.score >= 90 -> NeonSuccess
                            scoreBreakdown.score >= 60 -> NeonWarning
                            else -> NeonDanger
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (scoreBreakdown.isClean) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeonSuccess.copy(alpha = 0.1f),
                    border = BorderStroke(0.5.dp, NeonSuccess.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Clean State",
                            tint = NeonSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No security vulnerabilities or risky configurations flagged.",
                            fontSize = 11.sp,
                            color = NeonSuccess
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SeverityCountBadge("CRITICAL", scoreBreakdown.criticalCount, NeonDanger, Modifier.weight(1f))
                    SeverityCountBadge("HIGH", scoreBreakdown.highCount, Color(0xFFFF7043), Modifier.weight(1f))
                    SeverityCountBadge("MEDIUM", scoreBreakdown.mediumCount, NeonWarning, Modifier.weight(1f))
                    SeverityCountBadge("LOW", scoreBreakdown.lowCount, Color(0xFF42A5F5), Modifier.weight(1f))
                    SeverityCountBadge("INFO", scoreBreakdown.infoCount, TextMuted, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SeverityCountBadge(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (count > 0) color.copy(alpha = 0.12f) else SlateSurfaceVariant,
        border = BorderStroke(0.5.dp, if (count > 0) color.copy(alpha = 0.4f) else SlateOutline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) color else TextMuted
            )
            Text(
                text = label,
                fontSize = 8.sp,
                color = if (count > 0) color else TextMuted,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun FindingDetailDialog(
    finding: SecurityFinding,
    context: Context,
    onDismiss: () -> Unit
) {
    val sevColor = when (finding.severity) {
        SecuritySeverity.CRITICAL -> NeonDanger
        SecuritySeverity.HIGH -> Color(0xFFFF7043)
        SecuritySeverity.MEDIUM -> NeonWarning
        SecuritySeverity.LOW -> Color(0xFF42A5F5)
        SecuritySeverity.INFO -> TextMuted
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = BorderStroke(1.dp, sevColor.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("finding_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sevColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, sevColor)
                    ) {
                        Text(
                            text = "[${finding.severity.name}] ${mapFindingCategory(finding.category)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = sevColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = finding.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (finding.affectedFile.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Target: ${finding.affectedFile}",
                        fontSize = 11.sp,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "WHY IT MATTERS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = finding.description,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ACTUAL PARSED EVIDENCE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlateSurfaceVariant,
                    border = BorderStroke(0.5.dp, SlateOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = finding.evidence,
                            fontSize = 11.sp,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { copyToClipboard(context, "Evidence", finding.evidence) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Evidence",
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "RECOMMENDED REMEDIATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = finding.recommendation,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark, contentColor = CyberCyan)
                ) {
                    Text("Dismiss Finding")
                }
            }
        }
    }
}

fun mapFindingCategory(category: FindingCategory): String {
    return when (category) {
        FindingCategory.APK_PERMISSIONS -> "PERMISSIONS"
        FindingCategory.EXPORTED_COMPONENTS -> "MANIFEST"
        FindingCategory.NETWORK_SECURITY -> "NETWORK"
        FindingCategory.WEBVIEW_SECURITY -> "CONFIGURATION"
        FindingCategory.CRYPTOGRAPHY -> "CERTIFICATE"
        FindingCategory.SECRETS -> "STORAGE"
        FindingCategory.DEPENDENCIES -> "NATIVE"
        FindingCategory.SUPPLY_CHAIN -> "DEX"
        FindingCategory.GITHUB_ACTIONS -> "CONFIGURATION"
        FindingCategory.SIGNING_INTEGRITY -> "SIGNATURE"
        FindingCategory.ARTIFACT_TAMPER -> "SIGNATURE"
        FindingCategory.SOURCE_INTEGRITY -> "DEX"
    }
}

