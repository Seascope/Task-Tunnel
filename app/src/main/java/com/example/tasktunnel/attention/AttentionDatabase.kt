package com.example.tasktunnel.attention

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(
    tableName = "surface_usage_segments",
    indices = [Index("startedAtMillis"), Index("endedAtMillis"), Index("app"), Index("surface")],
)
data class SurfaceUsageSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val app: String,
    val surface: String?,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val tunnelTask: String?,
    val classification: String,
)

@Dao
interface SurfaceUsageSegmentDao {
    @Insert suspend fun insert(segment: SurfaceUsageSegmentEntity): Long
    @Query("SELECT * FROM surface_usage_segments ORDER BY startedAtMillis DESC LIMIT :limit") suspend fun getRecent(limit: Int): List<SurfaceUsageSegmentEntity>
    @Query("SELECT * FROM surface_usage_segments WHERE endedAtMillis > :startMillis AND startedAtMillis < :endMillis ORDER BY startedAtMillis ASC") suspend fun getForRange(startMillis: Long, endMillis: Long): List<SurfaceUsageSegmentEntity>
    @Query("SELECT * FROM surface_usage_segments ORDER BY startedAtMillis DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<SurfaceUsageSegmentEntity>>
    @Query("DELETE FROM surface_usage_segments WHERE endedAtMillis < :cutoffMillis") suspend fun deleteOlderThan(cutoffMillis: Long)
    @Query("DELETE FROM surface_usage_segments") suspend fun clear()
}

@Entity(
    tableName = "attention_events",
    indices = [Index("timestampMillis"), Index("tunnelId"), Index("driftEpisodeId")],
)
data class AttentionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val type: String,
    val subtype: String,
    val app: String?,
    val surface: String?,
    val task: String?,
    val tunnelId: String?,
    val driftEpisodeId: String?,
    val decision: String?,
    val relatedApps: String?,
)

@Dao
interface AttentionEventDao {
    @Insert
    suspend fun insert(event: AttentionEventEntity): Long

    @Query("SELECT * FROM attention_events ORDER BY timestampMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AttentionEventEntity>>

    @Query("SELECT * FROM attention_events ORDER BY timestampMillis ASC, id ASC")
    suspend fun getAll(): List<AttentionEventEntity>

    @Query(
        "SELECT COUNT(DISTINCT driftEpisodeId) FROM attention_events " +
            "WHERE subtype = :subtype AND timestampMillis >= :sinceMillis",
    )
    fun observeDistinctDriftEpisodesSince(sinceMillis: Long, subtype: String): Flow<Int>

    @Query("DELETE FROM attention_events")
    suspend fun clear()
}

@Database(entities = [AttentionEventEntity::class, SurfaceUsageSegmentEntity::class], version = 2, exportSchema = false)
abstract class AttentionDatabase : RoomDatabase() {
    abstract fun attentionEventDao(): AttentionEventDao
    abstract fun surfaceUsageSegmentDao(): SurfaceUsageSegmentDao

    companion object {
        const val DATABASE_NAME = "attention-history.db"

        @Volatile private var instance: AttentionDatabase? = null

        fun getInstance(context: Context): AttentionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AttentionDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS surface_usage_segments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, app TEXT NOT NULL, surface TEXT, startedAtMillis INTEGER NOT NULL, endedAtMillis INTEGER NOT NULL, tunnelTask TEXT, classification TEXT NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_surface_usage_segments_startedAtMillis ON surface_usage_segments(startedAtMillis)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_surface_usage_segments_endedAtMillis ON surface_usage_segments(endedAtMillis)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_surface_usage_segments_app ON surface_usage_segments(app)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_surface_usage_segments_surface ON surface_usage_segments(surface)")
            }
        }
    }
}

interface AttentionEventStore {
    fun observeRecent(limit: Int = 500): Flow<List<AttentionEvent>>
    suspend fun record(event: AttentionEvent)
    suspend fun clear()
}

class AttentionEventRepository(private val dao: AttentionEventDao) : AttentionEventStore {
    override fun observeRecent(limit: Int): Flow<List<AttentionEvent>> =
        dao.observeRecent(limit).map { rows -> rows.map(AttentionEventEntity::toDomain).reversed() }

    override suspend fun record(event: AttentionEvent) {
        dao.insert(event.toEntity())
    }

    override suspend fun clear() = dao.clear()

    fun observeDriftEpisodesSince(sinceMillis: Long): Flow<Int> =
        dao.observeDistinctDriftEpisodesSince(sinceMillis, AttentionSubtype.DRIFT_CHECK_IN.name)
}

private fun AttentionEvent.toEntity() = AttentionEventEntity(
    id = id,
    timestampMillis = timestampMillis,
    type = type.name,
    subtype = subtype.name,
    app = app?.name,
    surface = surface?.name,
    task = task?.name,
    tunnelId = tunnelId,
    driftEpisodeId = driftEpisodeId,
    decision = decision?.name,
    // Keep the existing column for schema compatibility, but persist package names so arbitrary
    // Drift apps survive history. Older rows stored AttentionApp enum names and are decoded below.
    relatedApps = (relatedPackages.ifEmpty { relatedApps.map(AttentionApp::packageName) })
        .takeIf { it.isNotEmpty() }
        ?.joinToString(","),
)

private fun AttentionEventEntity.toDomain(): AttentionEvent {
    val packages = relatedApps.orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { token ->
            // v1/v2 history stored enum names such as INSTAGRAM. New rows store real packages.
            runCatching { AttentionApp.valueOf(token).packageName }.getOrElse { token }
        }
        .distinct()

    return AttentionEvent(
        id = id,
        timestampMillis = timestampMillis,
        type = AttentionEventType.valueOf(type),
        subtype = AttentionSubtype.valueOf(subtype),
        app = app?.let(AttentionApp::valueOf),
        surface = surface?.let(DetectedSurface::valueOf),
        task = task?.let(TunnelTask::valueOf),
        tunnelId = tunnelId,
        driftEpisodeId = driftEpisodeId,
        decision = decision?.let(AttentionDecision::valueOf),
        relatedApps = packages.mapNotNull(AttentionApp::fromPackage),
        relatedPackages = packages,
    )
}
