package com.example

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppTab
import com.example.ui.WorkbenchViewModel
import com.example.ui.screens.ApkLabScreen
import com.example.ui.screens.BuildScreen
import com.example.ui.screens.DianaScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {

    private val viewModel: WorkbenchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val currentTab by viewModel.selectedTab.collectAsState()

                // File picker launcher using Storage Access Framework (ACTION_OPEN_DOCUMENT)
                val apkPickerLauncher = rememberLauncherForActivityResult(
                     contract = ActivityResultContracts.OpenDocument()
                 ) { uri: Uri? ->
                     if (uri != null) {
                         var fileName = "imported_app.apk"
                         try {
                             contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                 val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                 if (nameIndex != -1 && cursor.moveToFirst()) {
                                     val queriedName = cursor.getString(nameIndex)
                                     if (!queriedName.isNullOrBlank()) {
                                         fileName = queriedName
                                     }
                                 }
                             }
                         } catch (e: Exception) {
                             fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "imported_app.apk"
                         }
                         viewModel.importApkFromUri(uri, fileName)
                     }
                 }

                 WorkbenchApp(
                     viewModel = viewModel,
                     currentTab = currentTab,
                     onTabSelected = { viewModel.selectTab(it) },
                     onImportApkClick = { apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) }
                 )
            }
        }
    }
}

@Composable
fun WorkbenchApp(
    viewModel: WorkbenchViewModel,
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    onImportApkClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = SlateSurfaceCard,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                val tabs = listOf(
                    AppTab.HOME to Icons.Default.Home,
                    AppTab.DIANA to Icons.Default.Psychology,
                    AppTab.APK_LAB to Icons.Default.Science,
                    AppTab.BUILD to Icons.Default.Build,
                    AppTab.MODELS to Icons.Default.Memory,
                    AppTab.MONITOR to Icons.Default.Speed,
                    AppTab.SETTINGS to Icons.Default.Settings
                )

                tabs.forEach { (tab, icon) ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onTabSelected(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = SlateSurfaceCard
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToTab = onTabSelected,
                    onImportApkClick = onImportApkClick
                )
                AppTab.DIANA -> DianaScreen(viewModel = viewModel)
                AppTab.APK_LAB -> ApkLabScreen(
                    viewModel = viewModel,
                    onImportApkClick = onImportApkClick
                )
                AppTab.BUILD -> BuildScreen(viewModel = viewModel)
                AppTab.MODELS -> ModelsScreen(viewModel = viewModel)
                AppTab.MONITOR -> MonitorScreen(viewModel = viewModel)
                AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

