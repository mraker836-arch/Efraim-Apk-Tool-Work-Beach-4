package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.models.GgufModelInfo
import com.example.ui.WorkbenchViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanDark
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ModelsScreen(viewModel: WorkbenchViewModel) {
    val modelsList by viewModel.modelsList.collectAsState()
    val systemMetrics by viewModel.systemMetrics.collectAsState()

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
                Icon(Icons.Default.Memory, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("ON-DEVICE GGUF MODEL MANAGER", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Manage quantized local neural weights for offline DIANA inference", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }

        // Memory telemetry summary
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("JVM RAM In Use", fontSize = 11.sp, color = TextMuted)
                        Text("${systemMetrics.allocatedRamMb} MB / ${systemMetrics.maxRamMb} MB", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                    }
                    Column {
                        Text("Flash Storage Free", fontSize = 11.sp, color = TextMuted)
                        Text("${systemMetrics.availableStorageMb} MB", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                    }
                    Column {
                        Text("Quantization", fontSize = 11.sp, color = TextMuted)
                        Text("Q4_K_M (4-bit)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ElectricPurple)
                    }
                }
            }
        }

        item {
            Text("MODEL CATALOG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
        }

        items(modelsList) { model ->
            GgufModelCard(
                model = model,
                onLoadClick = { viewModel.loadModel(model.id) },
                onUnloadClick = { viewModel.unloadModel(model.id) },
                onDownloadClick = { viewModel.downloadModel(model.id) },
                onDeleteClick = { viewModel.deleteModel(model.id) },
                onSetDefaultClick = { viewModel.setDefaultModel(model.id) }
            )
        }
    }
}

@Composable
fun GgufModelCard(
    model: GgufModelInfo,
    onLoadClick: () -> Unit,
    onUnloadClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSetDefaultClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_card_${model.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (model.isLoaded) CyberCyan else SlateOutline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (model.isDefault) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AmberWarning.copy(alpha = 0.2f)
                            ) {
                                Text("DEFAULT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AmberWarning, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(model.fileName, fontSize = 11.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        model.isLoaded -> NeonEmerald.copy(alpha = 0.2f)
                        model.isDownloaded -> CyberCyan.copy(alpha = 0.15f)
                        else -> SlateSurfaceVariant
                    }
                ) {
                    Text(
                        text = when {
                            model.isLoaded -> "LOADED IN RAM"
                            model.isDownloaded -> "DOWNLOADED"
                            else -> "AVAILABLE"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            model.isLoaded -> NeonEmerald
                            model.isDownloaded -> CyberCyan
                            else -> TextMuted
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(model.description, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)

            Spacer(modifier = Modifier.height(10.dp))

            // Specs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpecBadge("Size", model.sizeDisplay)
                SpecBadge("Quant", model.quantization)
                SpecBadge("Context", "${model.contextLength} tokens")
                SpecBadge("RAM Req", "~${model.ramRequirementMb} MB")
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.isLoaded) {
                    Button(
                        onClick = onUnloadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateSurfaceVariant, contentColor = TextPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Unload RAM")
                    }
                } else if (model.isDownloaded) {
                    Button(
                        onClick = onLoadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Load to RAM", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onDownloadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyanDark, contentColor = CyberCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download Model")
                    }
                }

                if (model.isDownloaded && !model.isDefault) {
                    IconButton(
                        onClick = onSetDefaultClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SlateSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Set Default", tint = AmberWarning, modifier = Modifier.size(18.dp))
                    }
                }

                if (model.isDownloaded && !model.isLoaded) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SlateSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SpecBadge(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SlateSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(label, fontSize = 8.sp, color = TextMuted)
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}
