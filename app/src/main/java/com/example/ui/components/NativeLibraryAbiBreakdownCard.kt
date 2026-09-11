package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.model.NativeLibraryInfo
import com.example.ui.theme.*
import java.util.Locale

/**
 * Breakdown of Native Libraries (.so) by Architecture (ABI),
 * showing file sizes, byte percentages, and 32-bit vs 64-bit compliance.
 */
@Composable
fun NativeLibraryAbiBreakdownCard(
    nativeLibs: List<NativeLibraryInfo>,
    abiCoverage: List<String>
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "NATIVE LIBRARY ABI BREAKDOWN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (nativeLibs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No native shared libraries (.so) found. Pure Dalvik/ART Java/Kotlin app.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                return@Column
            }

            val totalBytes = nativeLibs.sumOf { it.fileSize }
            val grouped = nativeLibs.groupBy { it.abi }

            // ABI distribution list
            grouped.forEach { (abi, libs) ->
                val abiBytes = libs.sumOf { it.fileSize }
                val percentage = if (totalBytes > 0) (abiBytes.toDouble() / totalBytes * 100.0) else 0.0

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = abi,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SlateSurfaceVariant
                            ) {
                                Text(
                                    text = if (abi.contains("64")) "64-bit" else "32-bit",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (abi.contains("64")) NeonEmerald else AmberWarning,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${formatBytes(abiBytes)} (${String.format(Locale.US, "%.1f", percentage)}%)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { (percentage / 100f).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = CyberCyan,
                        trackColor = SlateSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    libs.forEach { lib ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${lib.libraryName}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            Text(
                                text = formatBytes(lib.fileSize),
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
