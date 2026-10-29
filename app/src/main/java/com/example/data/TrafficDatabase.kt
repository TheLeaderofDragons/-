package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "traffic_events")
data class TrafficEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val delta: Int,
    val source: String, // "simple", "stream", "camera"
    val label: String,
    val categoryId: String? = null
)

@Entity(tableName = "silhouette_memory")
data class SilhouetteMemoryEntity(
    @PrimaryKey val personId: Int,
    val descriptorCsv: String,
    val upperHex: String,
    val lowerHex: String,
    val aspectRatio: Float,
    val distanceZone: String, // "Вдали (Горизонт 2.5x)" or "Средний/Ближний план"
    val firstSeenTs: Long,
    val lastSeenTs: Long,
    val repeatHits: Int
)

@Entity(tableName = "parking_sessions")
data class ParkingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plateNumber: String,
    val vehicleTag: String, // e.g., "Легковой", "Кроссовер", "Такси / Доставка"
    val entryTimeMillis: Long,
    val exitTimeMillis: Long? = null,
    val durationMinutes: Int? = null,
    val isCompleted: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val source: String = "manual" // "camera" or "manual"
)

@Entity(tableName = "saved_shifts")
data class SavedShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationName: String,
    val dateStr: String,
    val durationFormatted: String,
    val totalPedestrians: Int,
    val hourlyRate: Int,
    val cameraUniqueSilhouettes: Int,
    val cameraRepeatsBlocked: Int,
    val streamsBreakdownText: String,
    val parkingTotalVehicles: Int,
    val parkingExitedVehicles: Int,
    val parkingAvgStayMinutes: Int,
    val notes: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface TrafficDao {
    @Query("SELECT * FROM traffic_events ORDER BY timestamp ASC")
    fun observeEvents(): Flow<List<TrafficEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TrafficEventEntity): Long

    @Query("SELECT * FROM traffic_events ORDER BY id DESC LIMIT 1")
    suspend fun getLastEvent(): TrafficEventEntity?

    @Query("DELETE FROM traffic_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM traffic_events")
    suspend fun clearAllEvents()

    // Silhouette Re-ID Memory (90 min default)
    @Query("SELECT * FROM silhouette_memory ORDER BY lastSeenTs DESC")
    fun observeSilhouettes(): Flow<List<SilhouetteMemoryEntity>>

    @Query("SELECT * FROM silhouette_memory")
    suspend fun getAllSilhouettesSnapshot(): List<SilhouetteMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSilhouette(entity: SilhouetteMemoryEntity)

    @Query("DELETE FROM silhouette_memory WHERE lastSeenTs < :cutoffMillis")
    suspend fun pruneExpiredSilhouettes(cutoffMillis: Long)

    @Query("DELETE FROM silhouette_memory")
    suspend fun clearAllSilhouettes()

    // Parking License Plate Sessions (12 hours default, 2..24h configurable)
    @Query("SELECT * FROM parking_sessions ORDER BY lastUpdatedMillis DESC")
    fun observeParkingSessions(): Flow<List<ParkingSessionEntity>>

    @Query("SELECT * FROM parking_sessions WHERE plateNumber = :plate AND isCompleted = 0 AND entryTimeMillis >= :cutoffMillis ORDER BY entryTimeMillis DESC LIMIT 1")
    suspend fun findActiveParkingByPlate(plate: String, cutoffMillis: Long): ParkingSessionEntity?

    @Query("SELECT * FROM parking_sessions WHERE id = :id LIMIT 1")
    suspend fun getParkingById(id: Long): ParkingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParkingSession(session: ParkingSessionEntity): Long

    @Update
    suspend fun updateParkingSession(session: ParkingSessionEntity)

    @Query("DELETE FROM parking_sessions WHERE id = :id")
    suspend fun deleteParkingById(id: Long)

    @Query("DELETE FROM parking_sessions WHERE entryTimeMillis < :cutoffMillis AND isCompleted = 1")
    suspend fun pruneExpiredParking(cutoffMillis: Long)

    @Query("DELETE FROM parking_sessions")
    suspend fun clearAllParkingSessions()

    // Saved Shifts Archive
    @Query("SELECT * FROM saved_shifts ORDER BY createdAt DESC")
    fun observeSavedShifts(): Flow<List<SavedShiftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedShift(shift: SavedShiftEntity)

    @Query("DELETE FROM saved_shifts WHERE id = :id")
    suspend fun deleteSavedShiftById(id: Long)
}

@Database(
    entities = [
        TrafficEventEntity::class,
        SilhouetteMemoryEntity::class,
        ParkingSessionEntity::class,
        SavedShiftEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TrafficDatabase : RoomDatabase() {
    abstract fun trafficDao(): TrafficDao

    companion object {
        @Volatile
        private var INSTANCE: TrafficDatabase? = null

        fun getInstance(context: Context): TrafficDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrafficDatabase::class.java,
                    "traffic_counter_field_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
