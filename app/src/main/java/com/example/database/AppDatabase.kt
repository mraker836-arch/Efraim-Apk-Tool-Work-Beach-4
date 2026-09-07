package com.example.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conv: ConversationEntity)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getMessagesForConversation(convId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessageEntity)

    @Query("DELETE FROM conversations WHERE id = :convId")
    suspend fun deleteConversation(convId: String)

    @Query("DELETE FROM chat_messages WHERE conversationId = :convId")
    suspend fun deleteMessagesForConversation(convId: String)
}

@Dao
interface ApkProjectDao {
    @Query("SELECT * FROM apk_projects ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<ApkProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ApkProjectEntity)

    @Query("DELETE FROM apk_projects WHERE id = :id")
    suspend fun deleteProject(id: String)
}

@Dao
interface BuildHistoryDao {
    @Query("SELECT * FROM build_history ORDER BY timestamp DESC")
    fun getAllBuildHistory(): Flow<List<BuildHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildHistory(history: BuildHistoryEntity)
}

@Dao
interface ApkScanDao {
    @Query("SELECT * FROM apk_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ApkScanEntity>>

    @Query("SELECT * FROM apk_scans WHERE scanId = :scanId")
    suspend fun getScanById(scanId: String): ApkScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ApkScanEntity)

    @Query("DELETE FROM apk_scans WHERE scanId = :scanId")
    suspend fun deleteScan(scanId: String)

    @Query("SELECT * FROM apk_scans WHERE status = 'COMPLETED' ORDER BY timestamp DESC")
    fun getCompletedScans(): Flow<List<ApkScanEntity>>

    @Query("SELECT * FROM apk_scans WHERE status = 'FAILED' ORDER BY timestamp DESC")
    fun getFailedScans(): Flow<List<ApkScanEntity>>

    @Query("SELECT * FROM apk_scans WHERE securityFindingCount > 0 ORDER BY timestamp DESC")
    fun getSecurityIssueScans(): Flow<List<ApkScanEntity>>

    @Query("SELECT * FROM apk_scans ORDER BY timestamp DESC LIMIT 20")
    fun getRecentScans(): Flow<List<ApkScanEntity>>

    @Query("SELECT * FROM apk_scans WHERE fileName LIKE '%' || :query || '%' OR sha256 LIKE '%' || :query || '%' OR packageName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchScans(query: String): Flow<List<ApkScanEntity>>

    @Query("SELECT COUNT(*) FROM apk_scans")
    fun getTotalScansCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM apk_scans WHERE status = 'COMPLETED'")
    fun getCompletedScansCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM apk_scans WHERE status = 'FAILED'")
    fun getFailedScansCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT sha256) FROM apk_scans")
    fun getUniqueApksCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(securityFindingCount), 0) FROM apk_scans")
    fun getTotalSecurityFindingsCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(dexCount), 0) FROM apk_scans")
    fun getTotalDexCount(): Flow<Int>
}

@Database(
    entities = [
        ConversationEntity::class,
        ChatMessageEntity::class,
        ApkProjectEntity::class,
        BuildHistoryEntity::class,
        ApkScanEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun apkProjectDao(): ApkProjectDao
    abstract fun buildHistoryDao(): BuildHistoryDao
    abstract fun apkScanDao(): ApkScanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE apk_scans ADD COLUMN permissionCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE apk_scans ADD COLUMN certificateStatus TEXT NOT NULL DEFAULT 'SIGNATURE_UNAVAILABLE'")
                db.execSQL("ALTER TABLE apk_scans ADD COLUMN packageName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE apk_scans ADD COLUMN uriString TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE apk_scans ADD COLUMN rawJson TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apk_workbench_db"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
