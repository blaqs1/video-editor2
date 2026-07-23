package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val displayName: String = "",
    val language: String,
    val status: String,
    val localUri: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val enText: String,
    val deText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "overlay_assets")
data class OverlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val displayName: String = "",
    val localPath: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "slide_assets")
data class SlideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slideText: String,
    val bordeauxColor: String = "#823334",
    val duration: Int = 5,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "timeline_events")
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: Long,
    val assetType: String, // "overlay" or "slide"
    val assetIdOrName: String, // filename or slide text
    val startTime: Float,
    val endTime: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val transitionType: String = "none", // "none", "fade", "dissolve", "wipe", "circle", "slide"
    val transitionDuration: Float = 0.5f
)

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY timestamp DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Query("UPDATE videos SET status = :status WHERE id = :id")
    suspend fun updateVideoStatus(id: Long, status: String)

    @Query("UPDATE videos SET status = :status WHERE filename = :filename")
    suspend fun updateVideoStatusByFilename(filename: String, status: String)

    @Query("UPDATE videos SET displayName = :displayName WHERE id = :id")
    suspend fun updateVideoDisplayName(id: Long, displayName: String)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideo(id: Long)

    @Query("DELETE FROM videos")
    suspend fun clearVideos()
}

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY timestamp DESC")
    fun getAllScripts(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE videoId = :videoId ORDER BY timestamp DESC")
    fun getScriptsForVideo(videoId: Long): Flow<List<ScriptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity): Long

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteScript(id: Long)
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config WHERE `key` = :key")
    suspend fun getConfig(key: String): ConfigEntity?

    @Query("SELECT * FROM config WHERE `key` = :key")
    fun getConfigFlow(key: String): Flow<ConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: ConfigEntity)
}

@Dao
interface OverlayDao {
    @Query("SELECT * FROM overlay_assets ORDER BY timestamp DESC")
    fun getAllOverlays(): Flow<List<OverlayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverlay(overlay: OverlayEntity): Long

    @Query("DELETE FROM overlay_assets WHERE id = :id")
    suspend fun deleteOverlay(id: Long)

    @Query("UPDATE overlay_assets SET displayName = :displayName WHERE id = :id")
    suspend fun updateOverlayDisplayName(id: Long, displayName: String)

    @Query("DELETE FROM overlay_assets")
    suspend fun clearOverlays()
}

@Dao
interface SlideDao {
    @Query("SELECT * FROM slide_assets ORDER BY timestamp DESC")
    fun getAllSlides(): Flow<List<SlideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlide(slide: SlideEntity): Long

    @Query("DELETE FROM slide_assets WHERE id = :id")
    suspend fun deleteSlide(id: Long)
}

@Dao
interface TimelineEventDao {
    @Query("SELECT * FROM timeline_events WHERE videoId = :videoId ORDER BY startTime ASC")
    fun getEventsForVideo(videoId: Long): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<TimelineEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TimelineEventEntity): Long

    @Query("DELETE FROM timeline_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("UPDATE timeline_events SET transitionType = :transitionType, transitionDuration = :duration WHERE id = :id")
    suspend fun updateEventTransition(id: Long, transitionType: String, duration: Float)

    @Query("DELETE FROM timeline_events WHERE videoId = :videoId")
    suspend fun clearEventsForVideo(videoId: Long)
}

@Entity(tableName = "project_sessions")
data class ProjectSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionName: String,
    val videoFilename: String,
    val audioFilename: String = "",
    val syncLanguage: String = "German / Deutsch",
    val syncActionCue: String = "Click on action button",
    val aiEngine: String = "auto",
    val syncStatusLabel: String = "DESYNCHRONIZED (-0.8s Drift)",
    val categoryTag: String = "Draft", // "Draft", "In Sync", "Review", "Exported", "Production"
    val notes: String = "",
    val timelineEventsCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

@Dao
interface ProjectSessionDao {
    @Query("SELECT * FROM project_sessions ORDER BY lastModified DESC")
    fun getAllSessions(): Flow<List<ProjectSessionEntity>>

    @Query("SELECT * FROM project_sessions WHERE categoryTag = :tag ORDER BY lastModified DESC")
    fun getSessionsByTag(tag: String): Flow<List<ProjectSessionEntity>>

    @Query("SELECT * FROM project_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ProjectSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ProjectSessionEntity): Long

    @Query("DELETE FROM project_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM project_sessions")
    suspend fun clearAllSessions()
}

@Database(entities = [VideoEntity::class, ScriptEntity::class, ConfigEntity::class, OverlayEntity::class, SlideEntity::class, TimelineEventEntity::class, ProjectSessionEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun scriptDao(): ScriptDao
    abstract fun configDao(): ConfigDao
    abstract fun overlayDao(): OverlayDao
    abstract fun slideDao(): SlideDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun projectSessionDao(): ProjectSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studio_minimal_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
