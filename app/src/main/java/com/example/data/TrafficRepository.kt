package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

data class StreamCategory(
    val id: String,
    val name: String,
    val colorKey: String, // "emerald", "sky", "amber", "rose", "violet", "slate"
    val count: Int = 0,
    val role: String = "normal" // "pass", "look", "target", "bonus", "in", "out", "normal"
)

data class StreamPreset(
    val key: String,
    val shortTitle: String,
    val bannerTitle: String,
    val metricType: String, // "conversion", "inside", "topShare"
    val defaultCategories: List<StreamCategory>
)

object PredefinedStreamPresets {
    val ALL = listOf(
        StreamPreset(
            key = "conversion",
            shortTitle = "Мимо / Зашли",
            bannerTitle = "Конверсия локации (Зашли / Все)",
            metricType = "conversion",
            defaultCategories = listOf(
                StreamCategory("pass", "Прошли мимо", "slate", 0, "pass"),
                StreamCategory("look", "Остановились у витрины", "amber", 0, "look"),
                StreamCategory("enter", "Зашли внутрь", "emerald", 0, "target"),
                StreamCategory("buy", "Вышли с покупкой", "sky", 0, "bonus")
            )
        ),
        StreamPreset(
            key = "inout",
            shortTitle = "Вход / Выход",
            bannerTitle = "Баланс входа и выхода (Сейчас внутри)",
            metricType = "inside",
            defaultCategories = listOf(
                StreamCategory("in", "Вошли внутрь", "emerald", 0, "in"),
                StreamCategory("out", "Вышли наружу", "rose", 0, "out"),
                StreamCategory("bypass", "Прошли мимо входа", "slate", 0, "pass")
            )
        ),
        StreamPreset(
            key = "directions",
            shortTitle = "Слева / Справа",
            bannerTitle = "Направления движения потока",
            metricType = "topShare",
            defaultCategories = listOf(
                StreamCategory("l2r", "Слева направо (к центру)", "sky", 0),
                StreamCategory("r2l", "Справа налево (от центра)", "amber", 0),
                StreamCategory("cross", "Через переход к точке", "emerald", 0),
                StreamCategory("turn", "Свернули во двор / ТЦ", "violet", 0)
            )
        ),
        StreamPreset(
            key = "audience",
            shortTitle = "Аудитория",
            bannerTitle = "Структура аудитории (Пол / Семьи)",
            metricType = "topShare",
            defaultCategories = listOf(
                StreamCategory("women", "Женщины", "rose", 0),
                StreamCategory("men", "Мужчины", "sky", 0),
                StreamCategory("kids", "С детьми / Семьи", "emerald", 0),
                StreamCategory("youth", "Молодёжь / Студенты", "violet", 0)
            )
        )
    )

    fun getByKey(key: String): StreamPreset =
        ALL.find { it.key == key } ?: ALL.first()
}

class TrafficRepository(
    private val dao: TrafficDao,
    context: Context
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("traffic_counter_prefs", Context.MODE_PRIVATE)

    val eventsFlow: Flow<List<TrafficEventEntity>> = dao.observeEvents()
    val silhouettesFlow: Flow<List<SilhouetteMemoryEntity>> = dao.observeSilhouettes()
    val parkingSessionsFlow: Flow<List<ParkingSessionEntity>> = dao.observeParkingSessions()
    val savedShiftsFlow: Flow<List<SavedShiftEntity>> = dao.observeSavedShifts()

    fun getLocationName(): String =
        prefs.getString("location_name", "Точка №1 (Трафик и Парковка)") ?: "Точка №1 (Трафик и Парковка)"

    fun setLocationName(name: String) {
        prefs.edit().putString("location_name", name).apply()
    }

    fun getElapsedSeconds(): Long = prefs.getLong("elapsed_seconds", 0L)
    fun setElapsedSeconds(sec: Long) {
        prefs.edit().putLong("elapsed_seconds", sec).apply()
    }

    fun getSessionStartTs(): Long {
        val saved = prefs.getLong("session_start_ts", 0L)
        if (saved == 0L) {
            val now = System.currentTimeMillis()
            prefs.edit().putLong("session_start_ts", now).apply()
            return now
        }
        return saved
    }

    fun resetSessionStartTs() {
        prefs.edit().putLong("session_start_ts", System.currentTimeMillis()).apply()
    }

    fun isSoundEnabled(): Boolean = prefs.getBoolean("sound_enabled", true)
    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun isVibrateEnabled(): Boolean = prefs.getBoolean("vibrate_enabled", true)
    fun setVibrateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("vibrate_enabled", enabled).apply()
    }

    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)
    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean("dark_theme", dark).apply()
    }

    // Silhouette sensitivity: default 85% for strong far-field sensitivity
    fun getSilhouetteSensitivity(): Int = prefs.getInt("silhouette_sensitivity", 85)
    fun setSilhouetteSensitivity(value: Int) {
        prefs.edit().putInt("silhouette_sensitivity", value.coerceIn(35, 98)).apply()
    }

    // Pedestrian Re-ID Memory: default 90 minutes (1.5 hours) as requested!
    fun getSilhouetteMemoryMinutes(): Int = prefs.getInt("silhouette_memory_minutes", 90)
    fun setSilhouetteMemoryMinutes(minutes: Int) {
        prefs.edit().putInt("silhouette_memory_minutes", minutes.coerceIn(15, 180)).apply()
    }

    // Parking License Plate Memory: default 12 hours (configurable 2..24 hours) as requested!
    fun getParkingMemoryHours(): Int = prefs.getInt("parking_memory_hours", 12)
    fun setParkingMemoryHours(hours: Int) {
        prefs.edit().putInt("parking_memory_hours", hours.coerceIn(2, 24)).apply()
    }

    fun getActivePresetKey(): String = prefs.getString("active_preset_key", "conversion") ?: "conversion"
    fun setActivePresetKey(key: String) {
        prefs.edit().putString("active_preset_key", key).apply()
    }

    fun getCameraSkippedRepeats(): Int = prefs.getInt("camera_skipped_repeats", 0)
    fun setCameraSkippedRepeats(count: Int) {
        prefs.edit().putInt("camera_skipped_repeats", max(0, count)).apply()
    }

    fun getCustomCategoriesJson(): String? = prefs.getString("categories_serialized", null)
    fun setCustomCategoriesJson(serialized: String) {
        prefs.edit().putString("categories_serialized", serialized).apply()
    }

    suspend fun insertEvent(event: TrafficEventEntity): Long = dao.insertEvent(event)
    suspend fun getLastEvent(): TrafficEventEntity? = dao.getLastEvent()
    suspend fun deleteEventById(id: Long) = dao.deleteEventById(id)
    suspend fun clearAllEvents() = dao.clearAllEvents()

    suspend fun getAllSilhouettesSnapshot(): List<SilhouetteMemoryEntity> = dao.getAllSilhouettesSnapshot()
    suspend fun upsertSilhouette(entity: SilhouetteMemoryEntity) = dao.upsertSilhouette(entity)
    suspend fun pruneExpiredSilhouettes(cutoffMillis: Long) = dao.pruneExpiredSilhouettes(cutoffMillis)
    suspend fun clearAllSilhouettes() = dao.clearAllSilhouettes()

    suspend fun findActiveParkingByPlate(plate: String, cutoffMillis: Long): ParkingSessionEntity? =
        dao.findActiveParkingByPlate(plate, cutoffMillis)

    suspend fun getParkingById(id: Long): ParkingSessionEntity? = dao.getParkingById(id)
    suspend fun insertParkingSession(session: ParkingSessionEntity): Long = dao.insertParkingSession(session)
    suspend fun updateParkingSession(session: ParkingSessionEntity) = dao.updateParkingSession(session)
    suspend fun deleteParkingById(id: Long) = dao.deleteParkingById(id)
    suspend fun clearAllParkingSessions() = dao.clearAllParkingSessions()

    suspend fun insertSavedShift(shift: SavedShiftEntity) = dao.insertSavedShift(shift)
    suspend fun deleteSavedShiftById(id: Long) = dao.deleteSavedShiftById(id)
}
