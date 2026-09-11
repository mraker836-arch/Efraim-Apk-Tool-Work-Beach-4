package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.model.ManifestInfo
import com.example.ui.theme.*

/**
 * Manifest viewer with XML syntax highlighting, in-manifest text search,
 * tag filtering, and copy-to-clipboard functionality.
 */
@Composable
fun ManifestXmlViewer(
    manifest: ManifestInfo?,
    rawXml: String?
) {
    var searchQuery by remember { mutableStateOf("") }
    var showRawXml by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var copyStatus by remember { mutableStateOf<String?>(null) }

    val xmlContent = rawXml ?: manifest?.rawXmlText ?: "<!-- AndroidManifest.xml not available -->"

    val highlightedText = remember(xmlContent, searchQuery) {
        buildHighlightedXml(xmlContent, searchQuery)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECODED ANDROIDMANIFEST.XML",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(xmlContent))
                            copyStatus = "Copied XML!"
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy XML", tint = CyberCyan, modifier = Modifier.size(16.dp))
                    }
                    Button(
                        onClick = { showRawXml = !showRawXml },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (showRawXml) CyberCyan else SlateSurfaceVariant)
                    ) {
                        Text(
                            text = if (showRawXml) "Collapse" else "View XML",
                            fontSize = 10.sp,
                            color = if (showRawXml) Color(0xFF00363B) else TextPrimary
                        )
                    }
                }
            }

            copyStatus?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, color = NeonEmerald, fontSize = 10.sp)
            }

            AnimatedVisibility(visible = showRawXml) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search XML elements, permissions, attributes...", fontSize = 11.sp, color = TextMuted) },
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .background(Color(0xFF0A0F1D), RoundedCornerShape(8.dp))
                            .border(1.dp, SlateOutline, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = highlightedText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds syntax-colored XML string with search term highlighting.
 */
fun buildHighlightedXml(xml: String, query: String): AnnotatedString {
    val lines = xml.lines()
    return buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            var idx = 0
            val trimmed = line.trimStart()

            when {
                trimmed.startsWith("<!--") -> {
                    // Comment
                    pushStyle(SpanStyle(color = Color(0xFF6A9955)))
                    append(line)
                    pop()
                }
                trimmed.startsWith("<") -> {
                    // Tag line: color tags, attributes, and strings
                    parseXmlLine(line, this)
                }
                else -> {
                    pushStyle(SpanStyle(color = Color(0xFFD4D4D4)))
                    append(line)
                    pop()
                }
            }

            if (i < lines.size - 1) {
                append("\n")
            }
        }

        // Apply search query highlight overlay if query exists
        if (query.isNotBlank()) {
            val textStr = toAnnotatedString().text
            var searchIdx = textStr.indexOf(query, ignoreCase = true)
            while (searchIdx != -1) {
                addStyle(
                    SpanStyle(
                        background = Color(0xFFFFD54F),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    ),
                    searchIdx,
                    searchIdx + query.length
                )
                searchIdx = textStr.indexOf(query, searchIdx + query.length, ignoreCase = true)
            }
        }
    }
}

private fun parseXmlLine(line: String, builder: AnnotatedString.Builder) {
    var pos = 0
    while (pos < line.length) {
        val c = line[pos]
        when (c) {
            '<' -> {
                builder.pushStyle(SpanStyle(color = Color(0xFF808080)))
                builder.append('<')
                builder.pop()
                pos++
                if (pos < line.length && line[pos] == '/') {
                    builder.pushStyle(SpanStyle(color = Color(0xFF808080)))
                    builder.append('/')
                    builder.pop()
                    pos++
                }
                // Tag name
                val tagStart = pos
                while (pos < line.length && !line[pos].isWhitespace() && line[pos] != '>' && line[pos] != '/') {
                    pos++
                }
                builder.pushStyle(SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.SemiBold))
                builder.append(line.substring(tagStart, pos))
                builder.pop()
            }
            '>', '/' -> {
                builder.pushStyle(SpanStyle(color = Color(0xFF808080)))
                builder.append(c)
                builder.pop()
                pos++
            }
            '"' -> {
                // Attribute value string
                val strStart = pos
                pos++
                while (pos < line.length && line[pos] != '"') {
                    pos++
                }
                if (pos < line.length) pos++ // include closing quote
                builder.pushStyle(SpanStyle(color = Color(0xFFCE9178)))
                builder.append(line.substring(strStart, pos))
                builder.pop()
            }
            '=' -> {
                builder.pushStyle(SpanStyle(color = Color(0xFFD4D4D4)))
                builder.append('=')
                builder.pop()
                pos++
            }
            else -> {
                if (line[pos].isWhitespace()) {
                    builder.append(c)
                    pos++
                } else {
                    // Attribute name
                    val attrStart = pos
                    while (pos < line.length && !line[pos].isWhitespace() && line[pos] != '=' && line[pos] != '>' && line[pos] != '/') {
                        pos++
                    }
                    builder.pushStyle(SpanStyle(color = Color(0xFF9CDCFE)))
                    builder.append(line.substring(attrStart, pos))
                    builder.pop()
                }
            }
        }
    }
}
