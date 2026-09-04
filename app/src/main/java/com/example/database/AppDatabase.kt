package com.example.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
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
}

@Database(
    entities = [
        ConversationEntity::class,
        ChatMessageEntity::class,
        ApkProjectEntity::class,
        BuildHistoryEntity::class,
        ApkScanEntity::class
    ],
    version = 2,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apk_workbench_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
