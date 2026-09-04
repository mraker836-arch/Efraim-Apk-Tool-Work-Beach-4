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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ai.models.ServerConnectionState
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
    val serverState by viewModel.modelServerState.collectAsState()
    val statusMessage by viewModel.modelStatusMessage.collectAsState()
    val systemMetrics by viewModel.systemMetrics.collectAsState()
    val config = viewModel.inferenceService.getPrivateBrainConfig()

    LaunchedEffect(Unit) {
        if (viewModel.inferenceService.isConfigured()) {
            viewModel.refreshModels()
        }
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "PRIVATE BRAIN MODEL MANAGER",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Real private LLM inference models (Ollama & OpenAI-compatible)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.refreshModels() },
                    modifier = Modifier.testTag("refresh_models_button")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Models",
                        tint = CyberCyan
                    )
                }
            }
        }

        // Server Status & Connection Banner
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (serverState) {
                        ServerConnectionState.AVAILABLE -> NeonEmerald.copy(alpha = 0.5f)
                        ServerConnectionState.CHECKING -> CyberCyan.copy(alpha = 0.5f)
                        ServerConnectionState.UNCONFIGURED -> SlateOutline
                        ServerConnectionState.AUTHENTICATION_ERROR -> AmberWarning.copy(alpha = 0.5f)
                        else -> Color(0xFFFF5252).copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (serverState) {
                                    ServerConnectionState.AVAILABLE -> Icons.Default.CheckCircle
                                    ServerConnectionState.CHECKING -> Icons.Default.Refresh
                                    ServerConnectionState.UNCONFIGURED -> Icons.Default.HelpOutline
                                    ServerConnectionState.AUTHENTICATION_ERROR -> Icons.Default.Warning
                                    else -> Icons.Default.Error
                                },
                                contentDescription = null,
                                tint = when (serverState) {
                                    ServerConnectionState.AVAILABLE -> NeonEmerald
                                    ServerConnectionState.CHECKING -> CyberCyan
                                    ServerConnectionState.UNCONFIGURED -> TextMuted
                                    ServerConnectionState.AUTHENTICATION_ERROR -> AmberWarning
                                    else -> Color(0xFFFF5252)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (serverState) {
                                    ServerConnectionState.AVAILABLE -> "SERVER CONNECTED"
                                    ServerConnectionState.CHECKING -> "CHECKING SERVER..."
                                    ServerConnectionState.UNCONFIGURED -> "SERVER NOT CONFIGURED"
                                    ServerConnectionState.AUTHENTICATION_ERROR -> "AUTH ERROR"
                                    ServerConnectionState.UNAVAILABLE -> "SERVER UNAVAILABLE"
                                    ServerConnectionState.MODEL_NOT_FOUND -> "MODEL NOT FOUND"
                                    ServerConnectionState.ERROR -> "SERVER ERROR"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (serverState) {
                                    ServerConnectionState.AVAILABLE -> NeonEmerald
                                    ServerConnectionState.CHECKING -> CyberCyan
                                    ServerConnectionState.UNCONFIGURED -> TextMuted
                                    ServerConnectionState.AUTHENTICATION_ERROR -> AmberWarning
                                    else -> Color(0xFFFF5252)
                                }
                            )
                        }

                        Button(
                            onClick = { viewModel.testModelServerConnection() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SlateSurfaceVariant,
                                contentColor = CyberCyan
                            ),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Test Connection", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    if (config.baseUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Endpoint: ${config.baseUrl} (${config.serverType.name})",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }
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
                        Text(
                            "${systemMetrics.allocatedRamMb} MB / ${systemMetrics.maxRamMb} MB",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }
                    Column {
                        Text("Flash Storage Free", fontSize = 11.sp, color = TextMuted)
                        Text(
                            "${systemMetrics.availableStorageMb} MB",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    }
                    Column {
                        Text("Active Models", fontSize = 11.sp, color = TextMuted)
                        Text(
                            "${modelsList.size} Discovered",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricPurple
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DISCOVERED MODELS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                if (serverState == ServerConnectionState.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberCyan, strokeWidth = 2.dp)
                }
            }
        }

        if (modelsList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No Server Models Discovered",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (viewModel.inferenceService.isConfigured())
                                "Ensure your private Ollama or OpenAI-compatible server is running and accessible at the configured URL."
                            else
                                "Private Brain is unconfigured. Go to Settings to configure your private LLM server endpoint.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        } else {
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
                        Text(
                            model.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (model.isDefault) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AmberWarning.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "DEFAULT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberWarning,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        model.fileName,
                        fontSize = 11.sp,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace
                    )
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
                            model.isLoaded -> "ACTIVE SESSION"
                            model.isDownloaded -> "READY ON SERVER"
                            else -> "SERVER HOSTED"
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

            Text(
                model.description.ifEmpty { "Verified model available on private inference server." },
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Specs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpecBadge("Backend", model.format)
                SpecBadge("Quant", model.quantization)
                SpecBadge("Context", "${model.contextLength} tokens")
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateSurfaceVariant,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect Session")
                    }
                } else {
                    Button(
                        onClick = onLoadClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF00363B)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect / Use Model", fontWeight = FontWeight.Bold)
                    }
                }

                if (!model.isDefault) {
                    IconButton(
                        onClick = onSetDefaultClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SlateSurfaceVariant)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Set Default",
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (!model.isLoaded) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SlateSurfaceVariant)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove from View",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
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
