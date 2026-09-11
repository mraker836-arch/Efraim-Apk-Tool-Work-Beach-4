package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.model.DexInfo
import com.example.ui.theme.*

/**
 * Method and Class browser for DEX bytecode files.
 * Displays count metrics (method IDs, class defs, string IDs, proto IDs, field IDs)
 * and provides class name searching and listing.
 */
@Composable
fun DexMethodClassBrowser(
    dexList: List<DexInfo>
) {
    var selectedDexIndex by remember { mutableStateOf(0) }
    var classSearchQuery by remember { mutableStateOf("") }
    var showClassesList by remember { mutableStateOf(false) }

    if (dexList.isEmpty()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No DEX files found in current APK", color = TextMuted, fontSize = 12.sp)
            }
        }
        return
    }

    val currentDex = dexList.getOrNull(selectedDexIndex) ?: dexList.first()

    val filteredClasses = remember(currentDex, classSearchQuery) {
        if (classSearchQuery.isBlank()) currentDex.classNames
        else currentDex.classNames.filter { it.contains(classSearchQuery, ignoreCase = true) }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "DEX METHOD & CLASS ANALYZER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Multi-dex selector
            if (dexList.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dexList.forEachIndexed { idx, d ->
                        val isSelected = selectedDexIndex == idx
                        Button(
                            onClick = { selectedDexIndex = idx },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) CyberCyan else SlateSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = d.fileName,
                                fontSize = 10.sp,
                                color = if (isSelected) Color(0xFF00363B) else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Summary metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniCard(
                    title = "CLASS DEFS",
                    value = currentDex.classDefsCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "METHODS",
                    value = currentDex.methodIdsEstimate.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "STRINGS",
                    value = currentDex.stringIdsCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "FIELDS",
                    value = currentDex.fieldIdsCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // DEX Header info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DEX Version: ${currentDex.version}", fontSize = 11.sp, color = TextSecondary)
                Text("Adler32: 0x${currentDex.adler32Checksum}", fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Class list expandable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Class Definitions (${currentDex.classNames.size} indexed)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Button(
                    onClick = { showClassesList = !showClassesList },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (showClassesList) CyberCyan else SlateSurfaceVariant)
                ) {
                    Text(
                        text = if (showClassesList) "Hide Classes" else "Browse Classes",
                        fontSize = 10.sp,
                        color = if (showClassesList) Color(0xFF00363B) else TextPrimary
                    )
                }
            }

            AnimatedVisibility(visible = showClassesList) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = classSearchQuery,
                        onValueChange = { classSearchQuery = it },
                        placeholder = { Text("Search class names...", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (classSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { classSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredClasses.isEmpty()) {
                        Text(
                            text = if (currentDex.classNames.isEmpty()) "No classes indexed for this DEX" else "No matching classes",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filteredClasses.take(50).forEach { cls ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SlateSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = cls,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SlateSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SlateOutline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberCyan, fontFamily = FontFamily.Monospace)
        }
    }
}
