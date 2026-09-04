package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val relatedApkPackage: String? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val sender: String, // "user", "diana", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val tokensGenerated: Int? = null,
    val tokensPerSecond: Float? = null,
    val isLocal: Boolean = true
)

@Entity(tableName = "apk_projects")
data class ApkProjectEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val sha256: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val isRebuilt: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "build_history")
data class BuildHistoryEntity(
    @PrimaryKey val id: String,
    val projectName: String,
    val buildType: String, // "BUILD" or "REBUILD"
    val isSuccess: Boolean,
    val durationMs: Long,
    val outputApkPath: String?,
    val outputSha256: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val logSummary: String
)

@Entity(tableName = "apk_scans")
data class ApkScanEntity(
    @PrimaryKey val scanId: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val md5: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String,
    val manifestSummary: String,
    val dexCount: Int,
    val abiSummary: String,
    val certificateSummary: String,
    val securityFindingCount: Int,
    val errorMessage: String? = null
)
