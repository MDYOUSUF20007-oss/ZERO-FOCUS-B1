package app.amoledfocus.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions") data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val mode: String, val startMs: Long, val endMs: Long,
    val plannedMs: Long, val completed: Boolean)
@Entity(tableName = "attempts") data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val pkg: String, val atMs: Long)
data class AttemptCount(val pkg: String, val count: Int)

@Dao interface FocusDao {
    @Insert suspend fun insert(e: SessionEntity): Long
    @Insert suspend fun insert(e: AttemptEntity)
    @Query("SELECT * FROM sessions ORDER BY startMs DESC") fun sessions(): Flow<List<SessionEntity>>
    @Query("SELECT pkg, COUNT(*) AS count FROM attempts GROUP BY pkg ORDER BY count DESC") fun attemptCounts(): Flow<List<AttemptCount>>
    @Query("SELECT COUNT(*) FROM sessions WHERE completed = 1") suspend fun completedCount(): Int
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM attempts") suspend fun clearAttempts()
}

@Database(entities = [SessionEntity::class, AttemptEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): FocusDao
    companion object { @Volatile private var i: AppDb? = null
        fun get(c: Context): AppDb = i ?: synchronized(this) { i ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "focus.db").build().also { i = it } } }
}
