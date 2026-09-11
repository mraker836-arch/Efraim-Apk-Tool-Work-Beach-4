package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Inspector for APK compiled String Pool / resources.arsc table strings.
 * Includes search filtering, string count metrics, and clipboard copy.
 */
@Composable
fun ResourceTableInspector(
    resourceStrings: List<String>,
    totalStringPoolCount: Int = resourceStrings.size
) {
    var searchQuery by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    var copyStatus by remember { mutableStateOf<String?>(null) }

    val filteredStrings = remember(resourceStrings, searchQuery) {
        if (searchQuery.isBlank()) resourceStrings
        else resourceStrings.filter { it.contains(searchQuery, ignoreCase = true) }
    }

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
                        text = "RESOURCE STRING POOL (resources.arsc)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${resourceStrings.size} indexed strings (Total pool: $totalStringPoolCount)",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
                if (resourceStrings.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val text = filteredStrings.take(100).joinToString("\n")
                            clipboardManager.setText(AnnotatedString(text))
                            copyStatus = "Copied strings!"
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy strings", tint = CyberCyan, modifier = Modifier.size(16.dp))
                    }
                }
            }

            copyStatus?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(it, color = NeonEmerald, fontSize = 10.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter resource strings...", fontSize = 11.sp, color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredStrings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (resourceStrings.isEmpty()) "No resource strings decoded or resources.arsc not present" else "No matching strings found",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredStrings.take(80).forEachIndexed { idx, str ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SlateSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#$idx",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    text = str,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
