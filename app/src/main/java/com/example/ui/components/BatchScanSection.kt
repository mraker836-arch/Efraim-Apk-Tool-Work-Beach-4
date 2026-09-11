package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.batch.BatchItem
import com.example.apk.batch.BatchItemStatus
import com.example.apk.batch.BatchScanManager
import com.example.apk.batch.BatchSummary
import com.example.ui.theme.*
import java.util.Locale

/**
 * Batch Scanning and Queue Execution component.
 * Supports multi-file selection, status badges, progress feedback,
 * and batch summary generation.
 */
@Composable
fun BatchScanSection(
    batchManager: BatchScanManager,
    onSelectMultipleApksClick: () -> Unit
) {
    val queue by batchManager.queue.collectAsState()
    val isProcessing by batchManager.isProcessing.collectAsState()
    val summary by batchManager.currentBatchSummary.collectAsState()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BATCH SCANNING & AUTOMATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${queue.size} APKs in queue",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onSelectMultipleApksClick,
                        enabled = !isProcessing,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add APKs", fontSize = 11.sp, color = TextPrimary)
                    }

                    if (queue.isNotEmpty() && !isProcessing) {
                        Button(
                            onClick = {
                                kotlinx.coroutines.GlobalScope.let {
                                    // Managed via coroutine in caller or ViewModel
                                }
                            },
                            enabled = false, // driven by parent or viewmodel trigger
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
                        ) {
                            Text("Run Batch", fontSize = 11.sp, color = Color.Black)
                        }
                    }
                }
            }

            if (isProcessing) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = CyberCyan,
                    trackColor = SlateSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Analyzing batch sequential queue...",
                    fontSize = 10.sp,
                    color = CyberCyan
                )
            }

            // Summary card if available
            summary?.let { sum ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlateSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "BATCH COMPLETION SUMMARY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Completed: ${sum.completedCount} / ${sum.totalFiles}", fontSize = 11.sp, color = TextPrimary)
                            Text("Failed: ${sum.failedCount}", fontSize = 11.sp, color = if (sum.failedCount > 0) CrimsonError else TextMuted)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Findings: ${sum.totalFindings}", fontSize = 11.sp, color = TextSecondary)
                            Text("High Risk: ${sum.highRiskCount}", fontSize = 11.sp, color = if (sum.highRiskCount > 0) CrimsonError else NeonEmerald)
                        }
                    }
                }
            }

            // Items list
            if (queue.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    queue.forEach { item ->
                        BatchItemRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchItemRow(item: BatchItem) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SlateSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                if (item.errorMessage != null) {
                    Text(
                        text = item.errorMessage,
                        fontSize = 9.sp,
                        color = CrimsonError,
                        maxLines = 1
                    )
                }
            }

            val (badgeText, badgeColor) = when (item.status) {
                BatchItemStatus.PENDING -> "PENDING" to TextMuted
                BatchItemStatus.SCANNING -> "SCANNING" to CyberCyan
                BatchItemStatus.COMPLETED -> "COMPLETED" to NeonEmerald
                BatchItemStatus.FAILED -> "FAILED" to CrimsonError
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
