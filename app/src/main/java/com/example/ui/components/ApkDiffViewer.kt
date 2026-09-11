package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.diff.ApkDiffEngine
import com.example.apk.diff.ApkDiffReport
import com.example.apk.diff.DiffChangeType
import com.example.apk.diff.DiffEntry
import com.example.apk.model.ApkScanResult
import com.example.ui.theme.*

/**
 * Visual diffing tool comparing two APKs for structural, bytecode,
 * manifest, permission, and native library differences.
 */
@Composable
fun ApkDiffViewer(
    apk1: ApkScanResult?,
    apk2: ApkScanResult?
) {
    if (apk1 == null || apk2 == null) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select two APK scan records from Scan History to compute side-by-side diff",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
        return
    }

    val report = remember(apk1, apk2) {
        ApkDiffEngine.compare(apk1, apk2)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "APK STRUCTURAL & SIZE DIFF REPORT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // File comparison header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("APK A (Base)", fontSize = 10.sp, color = TextMuted)
                    Text(report.apk1Name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("APK B (Target)", fontSize = 10.sp, color = TextMuted)
                    Text(report.apk2Name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Size delta banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SlateSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Size Delta:", fontSize = 12.sp, color = TextSecondary)
                    val deltaColor = if (report.sizeDeltaBytes > 0) CrimsonError else if (report.sizeDeltaBytes < 0) NeonEmerald else TextPrimary
                    Text(
                        text = report.sizeDeltaFormatted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Manifest property diffs
            Text("MANIFEST ATTRIBUTES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            DiffRow("Version Name", report.versionNameDiff)
            DiffRow("Version Code", report.versionCodeDiff)
            DiffRow("Min SDK", report.minSdkDiff)
            DiffRow("Target SDK", report.targetSdkDiff)
            DiffRow("DEX Files Count", report.dexCountDiff)

            Spacer(modifier = Modifier.height(10.dp))

            // Permissions diff summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Permission Changes:", fontSize = 11.sp, color = TextSecondary)
                Text(
                    text = "+${report.totalAddedPermissions} added, -${report.totalRemovedPermissions} removed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (report.totalAddedPermissions > 0) AmberWarning else NeonEmerald
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Native libs diff summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Native Libs Changes:", fontSize = 11.sp, color = TextSecondary)
                Text(
                    text = "+${report.totalAddedNativeLibs} added, -${report.totalRemovedNativeLibs} removed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun <T> DiffRow(label: String, entry: DiffEntry<T>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextMuted)
        val isModified = entry.changeType == DiffChangeType.MODIFIED
        val textColor = if (isModified) AmberWarning else TextPrimary
        Text(
            text = if (isModified) "${entry.oldValue} -> ${entry.newValue}" else "${entry.oldValue ?: "N/A"}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            fontWeight = if (isModified) FontWeight.Bold else FontWeight.Normal
        )
    }
}
