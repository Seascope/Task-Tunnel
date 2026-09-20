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

@Database(entities = [AttentionEventEntity::class], version = 1, exportSchema = false)
abstract class AttentionDatabase : RoomDatabase() {
    abstract fun attentionEventDao(): AttentionEventDao

    companion object {
        const val DATABASE_NAME = "attention-history.db"

        @Volatile private var instance: AttentionDatabase? = null

        fun getInstance(context: Context): AttentionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AttentionDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
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
    relatedApps = relatedApps.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name },
)

private fun AttentionEventEntity.toDomain() = AttentionEvent(
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
    relatedApps = relatedApps.orEmpty().split(',').filter(String::isNotBlank).map(AttentionApp::valueOf),
)
