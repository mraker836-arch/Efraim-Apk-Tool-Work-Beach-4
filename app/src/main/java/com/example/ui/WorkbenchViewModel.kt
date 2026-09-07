package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.firstOrNull
import com.example.ai.diana.DianaAnalysisReport
import com.example.ai.diana.DianaApkAnalyzer
import com.example.ai.inference.AiMode
import com.example.ai.inference.InferenceConfig
import com.example.ai.inference.InferenceMetrics
import com.example.ai.inference.InferenceService
import com.example.ai.models.GgufModelInfo
import com.example.ai.models.ModelManager
import com.example.ai.models.ServerConnectionState
import com.example.apk.builder.ApkBuildManager
import com.example.apk.builder.ProjectBuildConfig
import com.example.apk.model.APKInfo
import com.example.apk.model.AbiOption
import com.example.apk.model.BuildLog
import com.example.apk.model.BuildPlan
import com.example.apk.model.BuildResult
import com.example.apk.model.BuildStatus
import com.example.apk.model.BuildType
import com.example.apk.model.CustomAssetItem
import com.example.apk.model.RebuildConfig
import com.example.apk.rebuilder.ApkRebuildPipeline
import com.example.apk.scanner.ApkScannerService
import com.example.apk.signing.KeyEntryInfo
import com.example.apk.signing.SignatureVerificationResult
import com.example.apk.signing.SigningManager
import com.example.core.SystemMetrics
import com.example.core.SystemMonitor
import com.example.database.ApkProjectEntity
import com.example.database.ApkScanEntity
import com.example.database.AppDatabase
import com.example.database.BuildHistoryEntity
import com.example.database.ChatMessageEntity
import com.example.database.ConversationEntity
import com.example.apk.model.ScanFilter
import com.example.apk.model.DashboardStatistics
import com.example.apk.export.ScanReportExporter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import com.example.diagnostics.diana.DianaDiagnosticReport
import com.example.diagnostics.engine.AuditLogEntry
import com.example.diagnostics.engine.DiagnosticsEngine
import com.example.diagnostics.engine.ErrorTrendingStats
import com.example.diagnostics.model.AlertNotification
import com.example.diagnostics.model.DiagnosticErrorRecord
import com.example.diagnostics.model.ErrorCategory
import com.example.diagnostics.model.ErrorSeverity
import com.example.diagnostics.model.ErrorStatus
import com.example.diagnostics.model.Incident
import com.example.diagnostics.model.IncidentStatus
import com.example.diagnostics.model.ObservabilitySettings
import com.example.diagnostics.model.PerformanceCategory
import com.example.diagnostics.model.PerformanceMetricRecord
import com.example.diagnostics.model.ReleaseHealthSummary
import com.example.diagnostics.model.SystemHealthReport
import com.example.diagnostics.model.UserRole
import com.example.release.diana.DianaCiAnalyzer
import com.example.release.model.ChangePlan
import com.example.release.model.ChangePlanStatus
import com.example.release.model.CiErrorCategory
import com.example.release.model.CiFailureRecord
import com.example.release.model.WorkflowRunStatus
import com.example.security.diana.DianaSecurityReport
import com.example.security.engine.SecurityEngine
import com.example.security.model.FindingStatus
import com.example.security.model.GateResult
import com.example.security.model.ScanHistoryEntry
import com.example.security.model.SecurityFinding
import com.example.security.model.SecurityRegressionReport
import com.example.security.model.SecurityReport
import com.example.security.model.SecurityScore
import com.example.release.service.CiWorkflowState
import com.example.release.service.GitHubDeploymentProvider
import com.example.release.service.ReleaseEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppTab(val title: String, val iconName: String) {
    HOME("Home", "home"),
    DIANA("Diana AI", "psychology"),
    APK_LAB("APK Lab", "science"),
    BUILD("Build", "build"),
    MODELS("Models", "memory"),
    MONITOR("Monitor", "speed"),
    SETTINGS("Settings", "settings")
}

enum class ApkLabSubTab {
    UPLOAD,
    OVERVIEW,
    INSPECT,
    SECURITY_FINDINGS,
    DIANA_ANALYSIS,
    REBUILD,
    SIGN_VERIFY,
    EXPORT,
    HISTORY
}

typealias ChatMessage = com.example.ai.inference.ChatMessage

class WorkbenchViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)

    val scannerService = ApkScannerService(context)
    val signingManager = SigningManager(context)
    val rebuildPipeline = ApkRebuildPipeline(context, scannerService, signingManager)
    val buildManager = ApkBuildManager(context, signingManager)
    val dianaAnalyzer = DianaApkAnalyzer()
    val inferenceService = InferenceService(context)
    val modelManager = ModelManager(context, inferenceService)

    // Phase 9C: Release Engine & GitHub CI Pipeline
    val releaseEngine = ReleaseEngine(context, signingManager)
    val gitHubDeploymentProvider = GitHubDeploymentProvider()
    val dianaCiAnalyzer = DianaCiAnalyzer()

    // Phase 10: Production Monitoring, Diagnostics & Release Observability
    val diagnosticsEngine = DiagnosticsEngine(context, appVersion = "1.0", buildNumber = "1")
    val systemHealth: StateFlow<SystemHealthReport> = diagnosticsEngine.healthEngine.healthReport
    val diagnosticErrors: StateFlow<List<DiagnosticErrorRecord>> = diagnosticsEngine.errorCenter.errors
    val incidents: StateFlow<List<Incident>> = diagnosticsEngine.incidentManager.incidents
    val releaseHistory: StateFlow<List<ReleaseHealthSummary>> = diagnosticsEngine.releaseHistory
    val notifications: StateFlow<List<AlertNotification>> = diagnosticsEngine.notificationService.notifications
    val performanceMetrics: StateFlow<List<PerformanceMetricRecord>> = diagnosticsEngine.performanceMonitor.metrics
    val observabilitySettings: StateFlow<ObservabilitySettings> = diagnosticsEngine.settings
    val currentUserRole: StateFlow<UserRole> = diagnosticsEngine.currentUserRole
    val auditLogs: StateFlow<List<AuditLogEntry>> = diagnosticsEngine.auditLogService.logs

    // Phase 11: Security, APK Integrity & Supply-Chain Security
    val securityEngine = SecurityEngine(context, signingManager, inferenceService, diagnosticsEngine.auditLogService)
    val securityReport: StateFlow<SecurityReport?> = securityEngine.currentReport
    val securityScore: StateFlow<SecurityScore> = securityEngine.securityScore
    val securityFindings: StateFlow<List<SecurityFinding>> = securityEngine.allFindings
    val securityGates: StateFlow<List<GateResult>> = securityEngine.releaseGates
    val isSecurityScanning: StateFlow<Boolean> = securityEngine.isScanning
    val securityScanHistory: StateFlow<List<ScanHistoryEntry>> = securityEngine.scanHistory
    val securityRegression: StateFlow<SecurityRegressionReport?> = securityEngine.latestRegression
    val dianaSecurityReport: StateFlow<DianaSecurityReport?> = securityEngine.dianaSecurityReport

    private val _selectedDiagnosticReport = MutableStateFlow<DianaDiagnosticReport?>(null)
    val selectedDiagnosticReport: StateFlow<DianaDiagnosticReport?> = _selectedDiagnosticReport.asStateFlow()

    val ciWorkflowState: StateFlow<CiWorkflowState> = releaseEngine.ciWorkflowState
    val ciErrors: StateFlow<List<CiFailureRecord>> = releaseEngine.ciErrors

    private val _activeChangePlan = MutableStateFlow<ChangePlan?>(null)
    val activeChangePlan: StateFlow<ChangePlan?> = _activeChangePlan.asStateFlow()

    private val _ciOperationStatus = MutableStateFlow<String?>(null)
    val ciOperationStatus: StateFlow<String?> = _ciOperationStatus.asStateFlow()

    // Navigation
    private val _selectedTab = MutableStateFlow(AppTab.HOME)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _apkLabSubTab = MutableStateFlow(ApkLabSubTab.OVERVIEW)
    val apkLabSubTab: StateFlow<ApkLabSubTab> = _apkLabSubTab.asStateFlow()

    // Current APK State
    private val _currentApk = MutableStateFlow<APKInfo?>(null)
    val currentApk: StateFlow<APKInfo?> = _currentApk.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    // Real APK Scan Pipeline State
    val apkScanPipeline = scannerService.pipeline
    val scanPipelineStatus = apkScanPipeline.scanStatus
    val scanPipelineMessage = apkScanPipeline.statusMessage
    val currentScanResult = apkScanPipeline.currentResult

    // File 5: Real Scan History, Search, Filters & Persistence Management
    val allScans: StateFlow<List<ApkScanEntity>> = database.apkScanDao().getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanFilter = MutableStateFlow(ScanFilter.ALL)
    val scanFilter: StateFlow<ScanFilter> = _scanFilter.asStateFlow()

    private val _scanSearchQuery = MutableStateFlow("")
    val scanSearchQuery: StateFlow<String> = _scanSearchQuery.asStateFlow()

    val filteredScans: StateFlow<List<ApkScanEntity>> = combine(
        allScans,
        _scanFilter,
        _scanSearchQuery
    ) { scans, filter, query ->
        var list = when (filter) {
            ScanFilter.ALL -> scans
            ScanFilter.COMPLETED -> scans.filter { it.status.equals("COMPLETED", ignoreCase = true) }
            ScanFilter.FAILED -> scans.filter { it.status.equals("FAILED", ignoreCase = true) }
            ScanFilter.SECURITY_ISSUES -> scans.filter { it.securityFindingCount > 0 }
            ScanFilter.RECENT -> scans.take(20)
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.fileName.lowercase().contains(q) ||
                it.sha256.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardStats: StateFlow<DashboardStatistics> = allScans.map { scans ->
        if (scans.isEmpty()) {
            DashboardStatistics(isEmpty = true)
        } else {
            val total = scans.size
            val successful = scans.count { it.status.equals("COMPLETED", ignoreCase = true) }
            val failed = scans.count { it.status.equals("FAILED", ignoreCase = true) }
            val uniqueApks = scans.map { it.sha256 }.filter { it.isNotBlank() }.distinct().count()
            val totalFindings = scans.sumOf { it.securityFindingCount }
            val highSeverity = scans.count { it.securityFindingCount >= 3 }
            val totalDex = scans.sumOf { it.dexCount }
            val totalNative = scans.count { it.abiSummary.isNotBlank() && it.abiSummary != "Pure Java / DEX" && it.abiSummary != "Unknown" }
            DashboardStatistics(
                totalScans = total,
                successfulScans = successful,
                failedScans = failed,
                apksAnalyzed = uniqueApks,
                totalSecurityFindings = totalFindings,
                highSeverityFindings = highSeverity,
                dexFilesDetected = totalDex,
                nativeLibrariesDetected = totalNative,
                isEmpty = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStatistics(isEmpty = true))

    private val _selectedHistoryScan = MutableStateFlow<ApkScanEntity?>(null)
    val selectedHistoryScan: StateFlow<ApkScanEntity?> = _selectedHistoryScan.asStateFlow()

    private val _exportNotificationMessage = MutableStateFlow<String?>(null)
    val exportNotificationMessage: StateFlow<String?> = _exportNotificationMessage.asStateFlow()

    private val _stagedUri = MutableStateFlow<Uri?>(null)
    val stagedUri: StateFlow<Uri?> = _stagedUri.asStateFlow()

    private val _stagedFileName = MutableStateFlow<String?>(null)
    val stagedFileName: StateFlow<String?> = _stagedFileName.asStateFlow()

    private val _stagedFileSize = MutableStateFlow<Long?>(null)
    val stagedFileSize: StateFlow<Long?> = _stagedFileSize.asStateFlow()

    private var activeScanJob: kotlinx.coroutines.Job? = null

    // Diana Analysis Report
    private val _dianaReport = MutableStateFlow<DianaAnalysisReport?>(null)
    val dianaReport: StateFlow<DianaAnalysisReport?> = _dianaReport.asStateFlow()

    // Chat with Diana
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "diana",
                text = "Hello! I am **DIANA AI** (Developer Intelligence and APK Navigation Assistant) for **EFRAIM APK WORKBENCH TOOL M**. Import or select an APK in **APK Lab** to inspect its manifest, DEX bytecodes, native libraries, permissions, and execute authorized rebuild & v1 signing pipelines."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isGeneratingAi = MutableStateFlow(false)
    val isGeneratingAi: StateFlow<Boolean> = _isGeneratingAi.asStateFlow()

    private val _lastInferenceMetrics = MutableStateFlow<InferenceMetrics?>(null)
    val lastInferenceMetrics: StateFlow<InferenceMetrics?> = _lastInferenceMetrics.asStateFlow()

    // Rebuild Workspace State
    private val _rebuildPlan = MutableStateFlow<BuildPlan?>(null)
    val rebuildPlan: StateFlow<BuildPlan?> = _rebuildPlan.asStateFlow()

    private val _rebuildResult = MutableStateFlow<BuildResult?>(null)
    val rebuildResult: StateFlow<BuildResult?> = _rebuildResult.asStateFlow()

    val rebuildStatus: StateFlow<BuildStatus> = rebuildPipeline.currentStatus
    val rebuildLogs: StateFlow<List<BuildLog>> = rebuildPipeline.logs

    // Signing & Keys State
    private val _keysList = MutableStateFlow<List<KeyEntryInfo>>(emptyList())
    val keysList: StateFlow<List<KeyEntryInfo>> = _keysList.asStateFlow()

    private val _selectedKeyAlias = MutableStateFlow("default_release_key")
    val selectedKeyAlias: StateFlow<String> = _selectedKeyAlias.asStateFlow()

    private val _verificationResult = MutableStateFlow<SignatureVerificationResult?>(null)
    val verificationResult: StateFlow<SignatureVerificationResult?> = _verificationResult.asStateFlow()

    // Project Builder State
    val buildStatus: StateFlow<BuildStatus> = buildManager.buildStatus
    val buildLogs: StateFlow<List<BuildLog>> = buildManager.buildLogs
    private val _projectBuildResult = MutableStateFlow<BuildResult?>(null)
    val projectBuildResult: StateFlow<BuildResult?> = _projectBuildResult.asStateFlow()

    // Private Brain Models & Server State
    val modelsList: StateFlow<List<GgufModelInfo>> = modelManager.models
    val modelServerState: StateFlow<ServerConnectionState> = modelManager.serverState
    val modelStatusMessage: StateFlow<String> = modelManager.statusMessage

    // Developer Monitor Metrics
    private val _systemMetrics = MutableStateFlow(SystemMonitor.captureMetrics(context))
    val systemMetrics: StateFlow<SystemMetrics> = _systemMetrics.asStateFlow()

    // Persistent entities
    val projectHistory: StateFlow<List<ApkProjectEntity>> = database.apkProjectDao().getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val buildHistory: StateFlow<List<BuildHistoryEntity>> = database.buildHistoryDao().getAllBuildHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var telemetryJob: Job? = null

    init {
        startTelemetryLoop()
        refreshKeys()
        viewModelScope.launch {
            try {
                val latest = database.apkProjectDao().getAllProjects().firstOrNull()?.firstOrNull()
                if (latest != null) {
                    val file = File(latest.filePath)
                    if (file.exists() && file.length() > 0) {
                        val info = scannerService.scanApk(file)
                        _currentApk.value = info
                        runDianaAnalysis(info)
                    }
                }
            } catch (_: Exception) {
                // Keep clean state if no previous valid project
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            while (isActive) {
                _systemMetrics.value = SystemMonitor.captureMetrics(context)
                delay(1500)
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun selectApkLabSubTab(subTab: ApkLabSubTab) {
        _apkLabSubTab.value = subTab
    }

    fun refreshKeys() {
        viewModelScope.launch {
            _keysList.value = signingManager.listKeys()
        }
    }

    fun selectKey(alias: String) {
        _selectedKeyAlias.value = alias
    }

    fun generateNewKey(alias: String, subject: String) {
        viewModelScope.launch {
            signingManager.generateAndSaveKey(alias, subject)
            refreshKeys()
            _selectedKeyAlias.value = alias
        }
    }

    fun stageApkForScan(uri: Uri, fileName: String, fileSize: Long = 0L) {
        _stagedUri.value = uri
        _stagedFileName.value = fileName
        _stagedFileSize.value = fileSize
        _apkLabSubTab.value = ApkLabSubTab.UPLOAD
        _selectedTab.value = AppTab.APK_LAB
    }

    fun startStagedAnalysis() {
        val uri = _stagedUri.value ?: return
        val name = _stagedFileName.value ?: "selected.apk"
        importApkFromUri(uri, name)
    }

    fun cancelActiveScan() {
        activeScanJob?.cancel()
        activeScanJob = null
        _isScanning.value = false
        _scanError.value = "Analysis cancelled by user."
    }

    fun loadSampleApk(appName: String = "DemoSampleApp") {
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            val start = System.currentTimeMillis()
            try {
                val apkFile = scannerService.generateSampleApk(appName)
                val scanResult = apkScanPipeline.scanFromFile(apkFile)
                scanResult.onSuccess { result ->
                    val info = scannerService.toApkInfo(result, apkFile)
                    _currentApk.value = info
                    _stagedFileName.value = apkFile.name
                    _stagedFileSize.value = apkFile.length()
                    runDianaAnalysis(info)
                    saveProjectRecord(info)
                    _apkLabSubTab.value = ApkLabSubTab.OVERVIEW
                    val dur = System.currentTimeMillis() - start
                    diagnosticsEngine.trackApkOperation(
                        operationType = "IMPORT",
                        durationMs = dur,
                        success = true,
                        artifactSizeBytes = info.fileSize,
                        module = "ApkScanPipeline"
                    )
                }.onFailure { e ->
                    _scanError.value = e.message ?: "Failed to scan sample APK"
                }
            } catch (e: Exception) {
                _scanError.value = e.message
                val dur = System.currentTimeMillis() - start
                diagnosticsEngine.trackApkOperation(
                    operationType = "IMPORT",
                    durationMs = dur,
                    success = false,
                    module = "ApkScanPipeline",
                    errorMessage = e.message
                )
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun importApkFromUri(uri: Uri, fileName: String) {
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            _stagedUri.value = uri
            _stagedFileName.value = fileName
            _selectedTab.value = AppTab.APK_LAB
            val start = System.currentTimeMillis()
            try {
                val scanResult = apkScanPipeline.scanFromUri(context, uri, fileName)
                scanResult.onSuccess { result ->
                    val apkFile = java.io.File(context.cacheDir, "apk_scans/${result.scanId}.apk")
                    val info = scannerService.toApkInfo(result, apkFile)
                    _currentApk.value = info
                    _stagedFileSize.value = result.fileInfo.fileSize
                    saveProjectRecord(info)
                    runDianaAnalysis(info)
                    _apkLabSubTab.value = ApkLabSubTab.OVERVIEW
                    val dur = System.currentTimeMillis() - start
                    diagnosticsEngine.trackApkOperation(
                        operationType = "IMPORT",
                        durationMs = dur,
                        success = true,
                        artifactSizeBytes = info.fileSize,
                        module = "ApkScanPipeline"
                    )
                }.onFailure { e ->
                    _scanError.value = e.message ?: "Failed to analyze APK archive"
                    val dur = System.currentTimeMillis() - start
                    diagnosticsEngine.trackApkOperation(
                        operationType = "IMPORT",
                        durationMs = dur,
                        success = false,
                        module = "ApkScanPipeline",
                        errorMessage = e.message
                    )
                }
            } catch (e: Exception) {
                _scanError.value = e.message ?: "Unexpected error during APK scanning"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun selectScanFilter(filter: ScanFilter) {
        _scanFilter.value = filter
    }

    fun setScanSearchQuery(query: String) {
        _scanSearchQuery.value = query
    }

    fun selectHistoryScan(scan: ApkScanEntity) {
        _selectedHistoryScan.value = scan
        val scanResult = ScanReportExporter.fromEntity(scan)
        apkScanPipeline.setCurrentResult(scanResult)
        val apkFile = if (scan.uriString != null && scan.uriString.startsWith("file:")) {
            try { java.io.File(java.net.URI.create(scan.uriString)) } catch (_: Exception) { java.io.File(context.cacheDir, "apk_scans/${scan.scanId}.apk") }
        } else {
            java.io.File(context.cacheDir, "apk_scans/${scan.scanId}.apk")
        }
        val info = scannerService.toApkInfo(scanResult, apkFile)
        _currentApk.value = info
        _stagedFileName.value = scan.fileName
        _stagedFileSize.value = scan.fileSize
        runDianaAnalysis(info)
    }

    fun clearSelectedHistoryScan() {
        _selectedHistoryScan.value = null
    }

    fun deleteScanRecord(scanId: String) {
        viewModelScope.launch {
            try {
                database.apkScanDao().deleteScan(scanId)
                if (_selectedHistoryScan.value?.scanId == scanId) {
                    _selectedHistoryScan.value = null
                }
                if (currentScanResult.value?.scanId == scanId) {
                    apkScanPipeline.setCurrentResult(null)
                    _currentApk.value = null
                }
                // Only delete internal temporary cache files created by the application
                try {
                    val tempCache = java.io.File(context.cacheDir, "apk_scans/$scanId.apk")
                    if (tempCache.exists()) tempCache.delete()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                _scanError.value = "Failed to delete scan record: ${e.message}"
            }
        }
    }

    fun reanalyzeScan(scan: ApkScanEntity) {
        val uriStr = scan.uriString
        if (uriStr.isNullOrBlank()) {
            _scanError.value = "Original APK is no longer accessible. Please import the APK again."
            return
        }
        try {
            val uri = Uri.parse(uriStr)
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                _scanError.value = "Original APK is no longer accessible. Please import the APK again."
                return
            }
            inputStream.close()
            importApkFromUri(uri, scan.fileName)
        } catch (e: Exception) {
            _scanError.value = "Original APK is no longer accessible. Please import the APK again."
        }
    }

    fun exportScanReport(destinationUri: Uri, isJson: Boolean) {
        val currentScan = currentScanResult.value
            ?: _selectedHistoryScan.value?.let { ScanReportExporter.fromEntity(it) }

        if (currentScan == null) {
            _exportNotificationMessage.value = "No scan available to export."
            return
        }

        viewModelScope.launch {
            val content = if (isJson) {
                ScanReportExporter.generateJsonReport(currentScan)
            } else {
                ScanReportExporter.generatePlainTextReport(currentScan)
            }
            val result = ScanReportExporter.writeReportToUri(context, destinationUri, content)
            result.onSuccess {
                _exportNotificationMessage.value = "Report exported successfully (${if (isJson) "JSON" else "TXT"})."
            }.onFailure { e ->
                _exportNotificationMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun clearExportNotification() {
        _exportNotificationMessage.value = null
    }

    fun runDianaAnalysis(apkInfo: APKInfo? = _currentApk.value) {
        if (apkInfo == null) return
        viewModelScope.launch {
            val report = dianaAnalyzer.analyze(apkInfo)
            _dianaReport.value = report
        }
    }

    private suspend fun saveProjectRecord(info: APKInfo) {
        val entity = ApkProjectEntity(
            id = info.id,
            fileName = info.fileName,
            filePath = info.filePath,
            fileSize = info.fileSize,
            sha256 = info.sha256,
            packageName = info.packageName,
            appName = info.appName,
            versionName = info.versionName,
            versionCode = info.versionCode,
            minSdk = info.minSdk,
            targetSdk = info.targetSdk,
            isRebuilt = false,
            timestamp = System.currentTimeMillis()
        )
        database.apkProjectDao().insertProject(entity)
    }

    fun generateRebuildPlan(config: RebuildConfig) {
        val apk = _currentApk.value ?: return
        val plan = rebuildPipeline.createBuildPlan(apk, config)
        _rebuildPlan.value = plan
    }

    fun executeRebuild(config: RebuildConfig) {
        val apk = _currentApk.value ?: return
        val plan = _rebuildPlan.value ?: rebuildPipeline.createBuildPlan(apk, config)
        _rebuildPlan.value = plan
        _rebuildResult.value = null

        viewModelScope.launch {
            val inputApkFile = File(apk.filePath)
            val result = rebuildPipeline.executeRebuild(inputApkFile, config, plan)
            _rebuildResult.value = result

            if (result.isSuccess && result.outputApkFile != null) {
                // Re-scan new rebuilt APK and update active
                val rebuiltInfo = scannerService.scanApk(result.outputApkFile)
                _currentApk.value = rebuiltInfo
                runDianaAnalysis(rebuiltInfo)

                // Save to history
                database.buildHistoryDao().insertBuildHistory(
                    BuildHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        projectName = rebuiltInfo.appName,
                        buildType = "REBUILD",
                        isSuccess = true,
                        durationMs = result.durationMs,
                        outputApkPath = result.outputApkFile.absolutePath,
                        outputSha256 = result.outputSha256,
                        logSummary = "Rebuild completed in ${result.durationMs}ms with SHA-256: ${result.outputSha256}"
                    )
                )
            }
        }
    }

    fun executeProjectBuild(config: ProjectBuildConfig) {
        _projectBuildResult.value = null
        viewModelScope.launch {
            val result = buildManager.executeBuild(config)
            _projectBuildResult.value = result

            if (result.isSuccess && result.outputApkFile != null) {
                val builtInfo = scannerService.scanApk(result.outputApkFile)
                _currentApk.value = builtInfo
                runDianaAnalysis(builtInfo)

                database.buildHistoryDao().insertBuildHistory(
                    BuildHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        projectName = config.projectName,
                        buildType = "BUILD_${config.buildType}",
                        isSuccess = true,
                        durationMs = result.durationMs,
                        outputApkPath = result.outputApkFile.absolutePath,
                        outputSha256 = result.outputSha256,
                        logSummary = "Native build completed in ${result.durationMs}ms"
                    )
                )
            }
        }
    }

    fun cancelProjectBuild() {
        buildManager.cancelBuild()
    }

    fun signCurrentApk(keyAlias: String) {
        val apk = _currentApk.value ?: return
        viewModelScope.launch {
            val inputFile = File(apk.filePath)
            val outputFile = File(context.filesDir, "signed_${apk.fileName}")
            signingManager.signApk(inputFile, outputFile, keyAlias)
            val updatedInfo = scannerService.scanApk(outputFile)
            _currentApk.value = updatedInfo
            verifyCurrentApk()
        }
    }

    fun verifyCurrentApk() {
        val apk = _currentApk.value ?: return
        viewModelScope.launch {
            val file = File(apk.filePath)
            val res = signingManager.verifyApk(file)
            _verificationResult.value = res
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank() || _isGeneratingAi.value) return

        val userMsg = ChatMessage(sender = "user", text = text)
        _chatMessages.value = _chatMessages.value + userMsg
        _isGeneratingAi.value = true

        viewModelScope.launch {
            val apkContext = _currentApk.value?.let { apk ->
                "Current APK Context:\n" +
                "- App Name: ${apk.appName}\n" +
                "- Package: ${apk.packageName}\n" +
                "- Version: ${apk.versionName} (${apk.versionCode})\n" +
                "- MinSdk/TargetSdk: ${apk.minSdk} / ${apk.targetSdk}\n" +
                "- Permissions: ${apk.permissions.map { it.simpleName }}\n" +
                "- Signing Status: ${apk.signingStatus}\n"
            } ?: "No APK currently loaded."

            val fullPrompt = "$apkContext\nUser Query: $text"

            val botMsgId = UUID.randomUUID().toString()
            var accumulatedText = ""
            var currentMetrics: InferenceMetrics? = null

            // Add initial bot response bubble
            val initialBotMessage = ChatMessage(id = botMsgId, sender = "diana", text = "Thinking...")
            _chatMessages.value = _chatMessages.value + initialBotMessage

            try {
                inferenceService.generateStream(
                    prompt = fullPrompt,
                    config = InferenceConfig(),
                    onMetricsUpdated = { m ->
                        currentMetrics = m
                        _lastInferenceMetrics.value = m
                    }
                ).collect { chunk ->
                    if (accumulatedText.isEmpty()) {
                        accumulatedText = chunk
                    } else {
                        accumulatedText += chunk
                    }

                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == botMsgId) {
                            msg.copy(text = accumulatedText, metrics = currentMetrics)
                        } else {
                            msg
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Private Brain communication error"
                accumulatedText = "[Error: $errorMsg]"
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == botMsgId) {
                        msg.copy(text = accumulatedText, metrics = currentMetrics)
                    } else {
                        msg
                    }
                }
            } finally {
                _isGeneratingAi.value = false
            }

            // Save conversation to Room
            database.conversationDao().insertMessage(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = "default_conv",
                    sender = "user",
                    text = text
                )
            )
            database.conversationDao().insertMessage(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = "default_conv",
                    sender = "diana",
                    text = accumulatedText,
                    modelUsed = currentMetrics?.modelName,
                    tokensGenerated = currentMetrics?.tokensGenerated,
                    tokensPerSecond = currentMetrics?.tokensPerSecond,
                    isLocal = currentMetrics?.isLocal ?: true
                )
            )
        }
    }

    fun setAiMode(mode: AiMode) {
        inferenceService.setMode(mode)
    }

    fun getAiMode(): AiMode = inferenceService.getMode()

    fun refreshModels() {
        viewModelScope.launch {
            modelManager.refreshModels()
        }
    }

    fun testModelServerConnection() {
        viewModelScope.launch {
            modelManager.testConnection()
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            modelManager.loadModel(modelId)
        }
    }

    fun unloadModel(modelId: String) {
        viewModelScope.launch {
            modelManager.unloadModel(modelId)
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            modelManager.downloadModel(modelId)
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelManager.deleteModel(modelId)
        }
    }

    fun setDefaultModel(modelId: String) {
        viewModelScope.launch {
            modelManager.setDefaultModel(modelId)
        }
    }

    fun cleanupWorkspaces() {
        viewModelScope.launch {
            val baseWs = File(context.filesDir, "workspaces")
            com.example.core.SecurityManager.cleanupDirectory(baseWs)
            _systemMetrics.value = SystemMonitor.captureMetrics(context)
        }
    }

    // Phase 9C CI / Release Actions
    fun triggerCiBuild(token: String = "") {
        viewModelScope.launch {
            _ciOperationStatus.value = "Dispatching GitHub Actions Build Workflow..."
            val state = ciWorkflowState.value
            val parts = state.repositoryName.split("/")
            val owner = parts.getOrNull(0) ?: "efraim-user"
            val repo = parts.getOrNull(1) ?: "efraim-apk-workbench"

            val res = gitHubDeploymentProvider.triggerBuild(owner, repo, state.branchName, token)
            if (res.isSuccess) {
                releaseEngine.updateWorkflowState(
                    state.copy(
                        status = WorkflowRunStatus.BUILDING,
                        buildStatus = "DISPATCHED (CI In-Progress)"
                    )
                )
                _ciOperationStatus.value = "CI Build Workflow Dispatched Successfully."
            } else {
                _ciOperationStatus.value = "GITHUB CI READY — REAL REPOSITORY CONNECTION REQUIRED."
                val err = CiFailureRecord(
                    category = CiErrorCategory.CONFIGURATION_ERROR,
                    stageName = "CI Build Trigger",
                    summary = res.exceptionOrNull()?.message ?: "GitHub Authentication Required",
                    logSnippet = "workflow_dispatch failed: Token not configured or repository connection pending."
                )
                releaseEngine.recordCiError(err)
            }
        }
    }

    fun triggerCiProductionRelease(token: String = "", customNotes: String = "") {
        viewModelScope.launch {
            _ciOperationStatus.value = "Dispatching Production Release Workflow..."
            val state = ciWorkflowState.value
            val parts = state.repositoryName.split("/")
            val owner = parts.getOrNull(0) ?: "efraim-user"
            val repo = parts.getOrNull(1) ?: "efraim-apk-workbench"

            val res = gitHubDeploymentProvider.triggerProductionRelease(owner, repo, state.branchName, customNotes, token)
            if (res.isSuccess) {
                releaseEngine.updateWorkflowState(
                    state.copy(
                        status = WorkflowRunStatus.SIGNING,
                        releaseStatus = "PRODUCTION DISPATCHED"
                    )
                )
                _ciOperationStatus.value = "Production Release Dispatched Successfully."
            } else {
                _ciOperationStatus.value = "GITHUB CI READY — REAL REPOSITORY CONNECTION REQUIRED."
                val err = CiFailureRecord(
                    category = CiErrorCategory.RELEASE_ERROR,
                    stageName = "CI Release Trigger",
                    summary = res.exceptionOrNull()?.message ?: "GitHub Authentication Required for Release",
                    logSnippet = "workflow_dispatch release trigger failed: Token required."
                )
                releaseEngine.recordCiError(err)
            }
        }
    }

    fun analyzeCiFailureWithDiana(failure: CiFailureRecord) {
        viewModelScope.launch {
            val analysis = dianaCiAnalyzer.analyzeCiFailure(failure)
            _activeChangePlan.value = analysis.proposedChangePlan
            sendChatMessage("DIANA, analyze CI error in stage [${failure.stageName}]: ${failure.summary}")
        }
    }

    fun approveChangePlan(plan: ChangePlan) {
        _activeChangePlan.value = plan.copy(status = ChangePlanStatus.APPROVED)
    }

    fun applyApprovedChangePlan(plan: ChangePlan) {
        _activeChangePlan.value = plan.copy(status = ChangePlanStatus.APPLIED)
        releaseEngine.clearCiErrors()
    }

    // Phase 10: Production Diagnostics & Health Actions
    fun runSystemHealthCheck() {
        viewModelScope.launch {
            val isDianaLoaded = inferenceService.isLocalModelLoaded()
            val ciState = ciWorkflowState.value.status.name
            val hasKeystore = signingManager.listKeys().isNotEmpty()
            diagnosticsEngine.healthEngine.runFullHealthCheck(
                isDianaLoaded = isDianaLoaded,
                ciStatus = ciState,
                hasKeystore = hasKeystore
            )
        }
    }

    fun reportDiagnosticError(
        category: ErrorCategory,
        severity: ErrorSeverity,
        message: String,
        module: String,
        stackTrace: String = ""
    ) {
        diagnosticsEngine.reportError(category, severity, message, module, stackTrace)
    }

    fun updateErrorStatus(errorId: String, newStatus: ErrorStatus, notes: String = "") {
        diagnosticsEngine.errorCenter.updateStatus(errorId, newStatus, notes)
    }

    fun createIncident(
        title: String,
        severity: ErrorSeverity,
        affectedModule: String,
        description: String = ""
    ) {
        diagnosticsEngine.incidentManager.createIncident(
            title = title,
            severity = severity,
            affectedModule = affectedModule,
            affectedRelease = "1.0",
            initialDescription = description
        )
    }

    fun updateIncidentStatus(
        incidentId: String,
        newStatus: IncidentStatus,
        rootCause: String = "",
        resolution: String = ""
    ) {
        diagnosticsEngine.incidentManager.updateIncidentStatus(
            incidentId = incidentId,
            newStatus = newStatus,
            rootCause = rootCause,
            resolution = resolution,
            author = currentUserRole.value.name
        )
    }

    fun analyzeErrorWithDiana(errorRecord: DiagnosticErrorRecord) {
        val report = diagnosticsEngine.dianaDiagnosticsAnalyzer.analyzeError(errorRecord)
        _selectedDiagnosticReport.value = report
        if (report.suggestedChangePlan != null) {
            _activeChangePlan.value = report.suggestedChangePlan
        }
    }

    fun analyzeIncidentWithDiana(incident: Incident) {
        val report = diagnosticsEngine.dianaDiagnosticsAnalyzer.analyzeIncident(incident)
        _selectedDiagnosticReport.value = report
        if (report.suggestedChangePlan != null) {
            _activeChangePlan.value = report.suggestedChangePlan
        }
    }

    fun dismissSelectedDiagnosticReport() {
        _selectedDiagnosticReport.value = null
    }

    fun exportDiagnosticReportJson(): String {
        return diagnosticsEngine.exportReportAsJson()
    }

    fun exportDiagnosticReportTxt(): String {
        return diagnosticsEngine.exportReportAsText()
    }

    fun updateObservabilitySettings(newSettings: ObservabilitySettings) {
        diagnosticsEngine.updateSettings(newSettings)
    }

    fun setUserRole(role: UserRole) {
        diagnosticsEngine.setUserRole(role)
    }

    fun clearLocalDiagnostics() {
        diagnosticsEngine.clearLocalDiagnosticHistory()
    }

    // Phase 11: Security Operations
    fun runSecurityScan(targetApkFile: File? = null, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val apk = targetApkFile ?: _currentApk.value?.filePath?.let { File(it) }
            securityEngine.runFullSecurityScan(
                targetApkFile = apk,
                apkInfo = _currentApk.value,
                projectDir = context.filesDir.parentFile,
                currentUserRole = currentUserRole.value,
                forceRefresh = forceRefresh
            )
        }
    }

    fun updateSecurityFindingStatus(
        findingId: String,
        newStatus: FindingStatus,
        reason: String,
        userName: String = "User"
    ): Result<SecurityFinding> {
        return securityEngine.updateFindingStatus(
            findingId = findingId,
            newStatus = newStatus,
            reason = reason,
            userRole = currentUserRole.value,
            userName = userName
        )
    }

    fun exportSecurityReportJson(): String {
        return securityEngine.exportReportAsJson()
    }

    fun exportSecurityReportTxt(): String {
        return securityEngine.exportReportAsText()
    }
}
