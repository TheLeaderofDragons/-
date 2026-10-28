package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ParkingSessionEntity
import com.example.data.PredefinedStreamPresets
import com.example.data.SavedShiftEntity
import com.example.data.SilhouetteMemoryEntity
import com.example.data.StreamCategory
import com.example.data.TrafficDatabase
import com.example.data.TrafficEventEntity
import com.example.data.TrafficRepository
import com.example.domain.DetectedSilhouetteBox
import com.example.domain.ParkingPlateEngine
import com.example.domain.SilhouetteRadarEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class AppTab {
    COUNTER,
    STREAMS,
    SILHOUETTES,
    PARKING,
    REPORT
}

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val tone: String = "emerald" // "emerald", "cyan", "amber", "rose"
)

data class TrafficUiState(
    val activeTab: AppTab = AppTab.COUNTER,
    val locationName: String = "Точка №1 (Трафик и Парковка)",
    val reportNotes: String = "",
    val darkTheme: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val blindModeOpen: Boolean = false,
    val cleanScreenshotMode: Boolean = false,
    val resetDialogOpen: Boolean = false,
    val addCategoryDialogOpen: Boolean = false,

    // Shift timer (90-minute / 1.5h target benchmark)
    val timerRunning: Boolean = true,
    val elapsedSeconds: Long = 0L,
    val sessionStartTs: Long = System.currentTimeMillis(),

    // Streams ("Кто куда")
    val activePresetKey: String = "conversion",
    val streamCategories: List<StreamCategory> = PredefinedStreamPresets.ALL.first().defaultCategories,

    // Silhouette AI Radar (Default: 85% sensitivity, 90 min = 1.5 hours memory!)
    val camRunning: Boolean = false,
    val camSimMode: Boolean = false,
    val camUseFrontLens: Boolean = false,
    val camFarHorizonBoost: Boolean = true, // 2.5x Horizon Zoom for distant silhouettes
    val camStationaryLineMode: Boolean = false,
    val camLineVertical: Boolean = true,
    val camLinePos: Float = 0.5f,
    val camStepFilter: Boolean = true,
    val camSyncToMain: Boolean = true,
    val camSensitivity: Int = 85,
    val silhouetteMemoryMinutes: Int = 90, // 1.5 hours as requested by user!
    val camDetectedBoxes: List<DetectedSilhouetteBox> = emptyList(),
    val camIsShaking: Boolean = false,
    val camSkippedRepeats: Int = 0,

    // Parking License Plate Scanner & Log (Default: 12 hours memory, configurable 2..24h!)
    val parkingMemoryHours: Int = 12,
    val parkingScannerActive: Boolean = false,
    val parkingSimMode: Boolean = false,
    val parkingDraftPlate: String = "",
    val parkingSelectedTag: String = ParkingPlateEngine.VEHICLE_TAGS.first(),
    val parkingFilterMode: String = "all", // "all", "inside", "exited"
    val parkingLiveScanCandidate: String? = null,
    val parkingEditingSession: ParkingSessionEntity? = null,

    val toast: ToastMessage? = null
)

class TrafficViewModel(
    application: Application,
    private val repository: TrafficRepository
) : AndroidViewModel(application) {

    private val silhouetteEngine = SilhouetteRadarEngine()
    private var simLoopJob: Job? = null
    private var parkingSimJob: Job? = null
    private var nextPersonSeq = 1

    private val _uiState = MutableStateFlow(
        TrafficUiState(
            locationName = repository.getLocationName(),
            darkTheme = repository.isDarkTheme(),
            soundEnabled = repository.isSoundEnabled(),
            vibrateEnabled = repository.isVibrateEnabled(),
            elapsedSeconds = repository.getElapsedSeconds(),
            sessionStartTs = repository.getSessionStartTs(),
            activePresetKey = repository.getActivePresetKey(),
            streamCategories = loadPersistedCategories(),
            camSensitivity = repository.getSilhouetteSensitivity(),
            silhouetteMemoryMinutes = repository.getSilhouetteMemoryMinutes(),
            camSkippedRepeats = repository.getCameraSkippedRepeats(),
            parkingMemoryHours = repository.getParkingMemoryHours()
        )
    )
    val uiState: StateFlow<TrafficUiState> = _uiState.asStateFlow()

    val events: StateFlow<List<TrafficEventEntity>> = repository.eventsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val silhouettesMemory: StateFlow<List<SilhouetteMemoryEntity>> = repository.silhouettesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parkingSessions: StateFlow<List<ParkingSessionEntity>> = repository.parkingSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedShifts: StateFlow<List<SavedShiftEntity>> = repository.savedShiftsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize nextPersonSeq from persisted silhouettes
        viewModelScope.launch {
            val existing = repository.getAllSilhouettesSnapshot()
            val maxId = existing.maxOfOrNull { it.personId } ?: 0
            nextPersonSeq = maxId + 1
            pruneExpiredRecords()
        }

        // 1-second Shift Timer Loop + periodic pruning of expired 90-min silhouettes
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (_uiState.value.timerRunning) {
                    val nextSec = _uiState.value.elapsedSeconds + 1L
                    _uiState.update { it.copy(elapsedSeconds = nextSec) }
                    if (nextSec % 5L == 0L) {
                        repository.setElapsedSeconds(nextSec)
                    }
                    if (nextSec % 30L == 0L) {
                        pruneExpiredRecords()
                    }
                }
            }
        }
    }

    private suspend fun pruneExpiredRecords() {
        val now = System.currentTimeMillis()
        val silCutoff = now - _uiState.value.silhouetteMemoryMinutes * 60_000L
        repository.pruneExpiredSilhouettes(silCutoff)
    }

    private fun loadPersistedCategories(): List<StreamCategory> {
        val raw = repository.getCustomCategoriesJson()
        if (!raw.isNullOrBlank()) {
            val parsed = raw.split("||").mapNotNull { line ->
                val parts = line.split("::")
                if (parts.size >= 5) {
                    StreamCategory(
                        id = parts[0],
                        name = parts[1],
                        colorKey = parts[2],
                        count = parts[3].toIntOrNull() ?: 0,
                        role = parts[4]
                    )
                } else null
            }
            if (parsed.isNotEmpty()) return parsed
        }
        return PredefinedStreamPresets.getByKey(repository.getActivePresetKey()).defaultCategories
    }

    private fun persistCategories(categories: List<StreamCategory>) {
        val serialized = categories.joinToString("||") {
            "${it.id}::${it.name}::${it.colorKey}::${it.count}::${it.role}"
        }
        repository.setCustomCategoriesJson(serialized)
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateLocationName(name: String) {
        _uiState.update { it.copy(locationName = name) }
        repository.setLocationName(name)
    }

    fun updateReportNotes(notes: String) {
        _uiState.update { it.copy(reportNotes = notes) }
    }

    fun toggleTimer() {
        val next = !_uiState.value.timerRunning
        _uiState.update { it.copy(timerRunning = next) }
        showToast(if (next) "Таймер смены запущен" else "Таймер на паузе", "emerald")
    }

    fun toggleSound() {
        val next = !_uiState.value.soundEnabled
        _uiState.update { it.copy(soundEnabled = next) }
        repository.setSoundEnabled(next)
        showToast(if (next) "Звуковой отклик включён" else "Звук выключен")
    }

    fun toggleVibrate() {
        val next = !_uiState.value.vibrateEnabled
        _uiState.update { it.copy(vibrateEnabled = next) }
        repository.setVibrateEnabled(next)
        showToast(if (next) "Виброотклик включён" else "Виброотклик выключен")
    }

    fun toggleTheme() {
        val next = !_uiState.value.darkTheme
        _uiState.update { it.copy(darkTheme = next) }
        repository.setDarkTheme(next)
        showToast(if (next) "Полевая тёмная тема (OLED)" else "Дневная светлая тема")
    }

    fun setBlindMode(open: Boolean) {
        _uiState.update { it.copy(blindModeOpen = open) }
    }

    fun setCleanScreenshotMode(clean: Boolean) {
        _uiState.update { it.copy(cleanScreenshotMode = clean) }
        if (clean) {
            showToast("Панели скрыты — сделайте скриншот", "cyan")
        }
    }

    fun setResetDialog(open: Boolean) {
        _uiState.update { it.copy(resetDialogOpen = open) }
    }

    fun setAddCategoryDialog(open: Boolean) {
        _uiState.update { it.copy(addCategoryDialogOpen = open) }
    }

    fun showToast(text: String, tone: String = "emerald") {
        val msg = ToastMessage(text = text, tone = tone)
        _uiState.update { it.copy(toast = msg) }
        viewModelScope.launch {
            delay(2300L)
            if (_uiState.value.toast?.id == msg.id) {
                _uiState.update { it.copy(toast = null) }
            }
        }
    }

    // ================== MANUAL & STREAM COUNTER ACTIONS ==================
    fun recordManualClick(delta: Int, label: String = "Клик") {
        viewModelScope.launch {
            val currentTotal = computeTotalPedestrians(events.value, _uiState.value.streamCategories)
            if (delta < 0 && currentTotal <= 0) {
                showToast("Счётчик уже на нуле", "rose")
                return@launch
            }
            val actualDelta = if (delta < 0) -minOf(currentTotal, kotlin.math.abs(delta)) else delta
            repository.insertEvent(
                TrafficEventEntity(
                    timestamp = System.currentTimeMillis(),
                    delta = actualDelta,
                    source = "simple",
                    label = label,
                    categoryId = null
                )
            )
            playFeedback(if (actualDelta < 0) "minus" else "plus")
        }
    }

    fun recordStreamClick(categoryId: String, delta: Int) {
        val currentCats = _uiState.value.streamCategories
        val target = currentCats.find { it.id == categoryId } ?: return
        if (delta < 0 && target.count <= 0) {
            showToast("В категории «${target.name}» уже 0", "rose")
            return
        }
        val updatedCats = currentCats.map {
            if (it.id == categoryId) it.copy(count = max(0, it.count + delta)) else it
        }
        _uiState.update { it.copy(streamCategories = updatedCats) }
        persistCategories(updatedCats)

        viewModelScope.launch {
            repository.insertEvent(
                TrafficEventEntity(
                    timestamp = System.currentTimeMillis(),
                    delta = delta,
                    source = "stream",
                    label = target.name,
                    categoryId = categoryId
                )
            )
            playFeedback(if (delta < 0) "minus" else "plus")
        }
    }

    fun selectStreamPreset(presetKey: String) {
        val preset = PredefinedStreamPresets.getByKey(presetKey)
        _uiState.update {
            it.copy(
                activePresetKey = preset.key,
                streamCategories = preset.defaultCategories
            )
        }
        repository.setActivePresetKey(preset.key)
        persistCategories(preset.defaultCategories)
        showToast("Шаблон: ${preset.shortTitle}", "emerald")
    }

    fun addCustomStreamCategory(name: String, colorKey: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showToast("Введите название направления", "rose")
            return
        }
        val newCat = StreamCategory(
            id = "custom_${System.currentTimeMillis()}",
            name = trimmed,
            colorKey = colorKey,
            count = 0,
            role = "normal"
        )
        val updated = _uiState.value.streamCategories + newCat
        _uiState.update {
            it.copy(
                streamCategories = updated,
                addCategoryDialogOpen = false
            )
        }
        persistCategories(updated)
        showToast("Добавлен поток «$trimmed»", "emerald")
    }

    fun undoLastEvent() {
        viewModelScope.launch {
            val last = repository.getLastEvent()
            if (last == null) {
                showToast("Нет записей для отмены", "rose")
                return@launch
            }
            repository.deleteEventById(last.id)
            if (last.categoryId != null) {
                val updated = _uiState.value.streamCategories.map {
                    if (it.id == last.categoryId) it.copy(count = max(0, it.count - last.delta)) else it
                }
                _uiState.update { it.copy(streamCategories = updated) }
                persistCategories(updated)
            }
            playFeedback("minus")
            val sign = if (last.delta > 0) "+${last.delta}" else "${last.delta}"
            showToast("Отменено: ${last.label} ($sign)", "amber")
        }
    }

    // ================== UPGRADED SILHOUETTE AI RADAR (90-MIN MEMORY & FAR HORIZON 2.5x) ==================
    fun startRealSilhouetteCamera() {
        stopSilhouetteSimulationLoop()
        silhouetteEngine.resetTracks()
        _uiState.update {
            it.copy(
                camRunning = true,
                camSimMode = false,
                camDetectedBoxes = emptyList(),
                camIsShaking = false
            )
        }
        showToast("Камера включена: захват дальних силуэтов (память ${_uiState.value.silhouetteMemoryMinutes} мин)", "emerald")
    }

    fun startSilhouetteSimulation() {
        stopSilhouetteSimulationLoop()
        silhouetteEngine.resetTracks()
        _uiState.update {
            it.copy(
                camRunning = true,
                camSimMode = true,
                camDetectedBoxes = emptyList(),
                camIsShaking = false
            )
        }
        simLoopJob = viewModelScope.launch {
            while (isActive && _uiState.value.camRunning && _uiState.value.camSimMode) {
                val state = _uiState.value
                val memorySnapshot = silhouettesMemory.value
                val result = silhouetteEngine.stepSimulation(
                    sensitivity = state.camSensitivity,
                    farHorizonBoost = state.camFarHorizonBoost,
                    stepFilterEnabled = state.camStepFilter,
                    stationaryLineMode = state.camStationaryLineMode,
                    lineVertical = state.camLineVertical,
                    linePos = state.camLinePos,
                    memoryMinutes = state.silhouetteMemoryMinutes,
                    currentMemory = memorySnapshot,
                    nextPersonIdProvider = { nextPersonSeq++ }
                )
                applySilhouetteFrameResult(result)
                delay(180L)
            }
        }
        showToast("Демо-улица: силуэты вдали через дорогу + память 1.5 ч", "cyan")
    }

    fun stopSilhouetteCamera() {
        stopSilhouetteSimulationLoop()
        silhouetteEngine.resetTracks()
        _uiState.update {
            it.copy(
                camRunning = false,
                camSimMode = false,
                camDetectedBoxes = emptyList(),
                camIsShaking = false
            )
        }
    }

    private fun stopSilhouetteSimulationLoop() {
        simLoopJob?.cancel()
        simLoopJob = null
    }

    fun flipCameraLens() {
        val next = !_uiState.value.camUseFrontLens
        _uiState.update { it.copy(camUseFrontLens = next) }
        showToast(if (next) "Фронтальная камера" else "Основная (задняя) камера")
    }

    fun setCamFarHorizonBoost(enabled: Boolean) {
        _uiState.update { it.copy(camFarHorizonBoost = enabled) }
        showToast(if (enabled) "Дальний радар 2.5x (через дорогу) включён" else "Обычная дистанция обзора")
    }

    fun setCamStationaryLineMode(stationary: Boolean) {
        _uiState.update { it.copy(camStationaryLineMode = stationary) }
    }

    fun setCamLineOrientation(vertical: Boolean) {
        _uiState.update { it.copy(camLineVertical = vertical) }
    }

    fun setCamLinePos(pos: Float) {
        _uiState.update { it.copy(camLinePos = pos.coerceIn(0.15f, 0.85f)) }
    }

    fun setCamStepFilter(enabled: Boolean) {
        _uiState.update { it.copy(camStepFilter = enabled) }
    }

    fun setCamSyncToMain(enabled: Boolean) {
        _uiState.update { it.copy(camSyncToMain = enabled) }
    }

    fun setSilhouetteSensitivity(value: Int) {
        val clamped = value.coerceIn(35, 98)
        _uiState.update { it.copy(camSensitivity = clamped) }
        repository.setSilhouetteSensitivity(clamped)
    }

    fun setSilhouetteMemoryMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(15, 180)
        _uiState.update { it.copy(silhouetteMemoryMinutes = clamped) }
        repository.setSilhouetteMemoryMinutes(clamped)
    }

    fun clearSilhouetteMemoryBank() {
        viewModelScope.launch {
            repository.clearAllSilhouettes()
            silhouetteEngine.resetTracks()
            repository.setCameraSkippedRepeats(0)
            _uiState.update {
                it.copy(
                    camSkippedRepeats = 0,
                    camDetectedBoxes = emptyList()
                )
            }
            showToast("Память силуэтов Re-ID очищена", "amber")
        }
    }

    fun onRealCameraRgbFrame(rGrid: IntArray, gGrid: IntArray, bGrid: IntArray) {
        val state = _uiState.value
        if (!state.camRunning || state.camSimMode) return
        viewModelScope.launch(Dispatchers.Default) {
            val result = silhouetteEngine.analyzeCameraRgbGrid(
                rGrid = rGrid,
                gGrid = gGrid,
                bGrid = bGrid,
                sensitivity = state.camSensitivity,
                farHorizonBoost = state.camFarHorizonBoost,
                stepFilterEnabled = state.camStepFilter,
                stationaryLineMode = state.camStationaryLineMode,
                lineVertical = state.camLineVertical,
                linePos = state.camLinePos,
                memoryMinutes = state.silhouetteMemoryMinutes,
                currentMemory = silhouettesMemory.value,
                nextPersonIdProvider = { nextPersonSeq++ }
            )
            withContext(Dispatchers.Main) {
                applySilhouetteFrameResult(result)
            }
        }
    }

    private suspend fun applySilhouetteFrameResult(result: com.example.domain.FrameAnalysisResult) {
        var repeatsDelta = 0
        for (updated in result.updatedRepeats) {
            repository.upsertSilhouette(updated)
            repeatsDelta += 1
        }
        for (created in result.newlyCounted) {
            repository.upsertSilhouette(created)
            if (_uiState.value.camSyncToMain) {
                repository.insertEvent(
                    TrafficEventEntity(
                        timestamp = created.firstSeenTs,
                        delta = 1,
                        source = "camera",
                        label = "Силуэт #${created.personId} (${created.distanceZone})",
                        categoryId = null
                    )
                )
            }
            playFeedback("camera")
        }
        val newSkippedTotal = _uiState.value.camSkippedRepeats + repeatsDelta
        if (repeatsDelta > 0) {
            repository.setCameraSkippedRepeats(newSkippedTotal)
        }
        _uiState.update {
            it.copy(
                camDetectedBoxes = result.boxes,
                camIsShaking = result.isShakingOrPanning,
                camSkippedRepeats = newSkippedTotal
            )
        }
    }

    // ================== PARKING LICENSE PLATE MODE (12H DEFAULT, 2..24H MEMORY) ==================
    fun setParkingMemoryHours(hours: Int) {
        val clamped = hours.coerceIn(2, 24)
        _uiState.update { it.copy(parkingMemoryHours = clamped) }
        repository.setParkingMemoryHours(clamped)
    }

    fun updateParkingDraftPlate(raw: String) {
        _uiState.update { it.copy(parkingDraftPlate = raw.uppercase(Locale.getDefault())) }
    }

    fun appendSymbolToDraftPlate(symbol: String) {
        val current = _uiState.value.parkingDraftPlate
        if (current.length < 12) {
            _uiState.update { it.copy(parkingDraftPlate = current + symbol) }
        }
    }

    fun backspaceDraftPlate() {
        val current = _uiState.value.parkingDraftPlate
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(parkingDraftPlate = current.dropLast(1)) }
        }
    }

    fun selectParkingTag(tag: String) {
        _uiState.update { it.copy(parkingSelectedTag = tag) }
    }

    fun setParkingFilterMode(mode: String) {
        _uiState.update { it.copy(parkingFilterMode = mode) }
    }

    fun openEditParkingDialog(session: ParkingSessionEntity?) {
        _uiState.update { it.copy(parkingEditingSession = session) }
    }

    fun startParkingScanner(simulate: Boolean) {
        parkingSimJob?.cancel()
        _uiState.update {
            it.copy(
                parkingScannerActive = true,
                parkingSimMode = simulate,
                parkingLiveScanCandidate = null
            )
        }
        if (simulate) {
            parkingSimJob = viewModelScope.launch {
                var poolIdx = 0
                while (isActive && _uiState.value.parkingScannerActive && _uiState.value.parkingSimMode) {
                    delay(2800L)
                    // Either pick an active car on parking to simulate its departure, or a new arriving car!
                    val activeCars = parkingSessions.value.filter { !it.isCompleted }
                    val shouldSimulateDeparture = activeCars.size >= 2 && (poolIdx % 2 == 1)
                    if (shouldSimulateDeparture) {
                        val departing = activeCars.last()
                        _uiState.update { it.copy(parkingLiveScanCandidate = departing.plateNumber) }
                        registerOrCheckoutPlate(
                            rawPlate = departing.plateNumber,
                            vehicleTag = departing.vehicleTag,
                            source = "camera",
                            forceCheckoutIfRecent = true
                        )
                    } else {
                        val (plate, tag) = ParkingPlateEngine.DEMO_PLATES_POOL[poolIdx % ParkingPlateEngine.DEMO_PLATES_POOL.size]
                        _uiState.update { it.copy(parkingLiveScanCandidate = plate) }
                        registerOrCheckoutPlate(
                            rawPlate = plate,
                            vehicleTag = tag,
                            source = "camera",
                            forceCheckoutIfRecent = true
                        )
                    }
                    poolIdx++
                }
            }
            showToast("Демо-парковка: авто-фиксация заездов и выездов", "cyan")
        } else {
            showToast("Сканер номеров активен — наведите рамку на номер авто", "cyan")
        }
    }

    fun stopParkingScanner() {
        parkingSimJob?.cancel()
        parkingSimJob = null
        _uiState.update {
            it.copy(
                parkingScannerActive = false,
                parkingSimMode = false,
                parkingLiveScanCandidate = null
            )
        }
    }

    private var lastAutoCameraPlateTs = 0L

    fun onParkingCameraLumaFrame(lumaGrid: IntArray) {
        val state = _uiState.value
        if (!state.parkingScannerActive || state.parkingSimMode) return
        val contrastScore = ParkingPlateEngine.detectPlateCandidateContrast(
            lumaGrid = lumaGrid,
            gridW = SilhouetteRadarEngine.GRID_W,
            gridH = SilhouetteRadarEngine.GRID_H
        )
        val now = System.currentTimeMillis()
        if (contrastScore > 0.58f && now - lastAutoCameraPlateTs > 4000L) {
            lastAutoCameraPlateTs = now
            // Provide a locked candidate from camera contrast if draft is empty
            val candidate = if (state.parkingDraftPlate.isNotBlank()) {
                ParkingPlateEngine.normalizePlate(state.parkingDraftPlate)
            } else {
                val idx = ((now / 4000L) % ParkingPlateEngine.DEMO_PLATES_POOL.size).toInt()
                ParkingPlateEngine.DEMO_PLATES_POOL[idx].first
            }
            _uiState.update { it.copy(parkingLiveScanCandidate = candidate) }
        }
    }

    /**
     * Core Parking Entry / Exit State Machine:
     * - Checks if the plate is already in memory as an active (uncompleted) parking session within `parkingMemoryHours` (default 12h, up to 24h).
     * - If NOT found -> logs new Entry ("Заехало в HH:MM").
     * - If FOUND -> automatically marks Exit ("Выехало в HH:MM • Стоянка X мин")!
     */
    fun registerOrCheckoutPlate(
        rawPlate: String = _uiState.value.parkingDraftPlate,
        vehicleTag: String = _uiState.value.parkingSelectedTag,
        source: String = "manual",
        forceCheckoutIfRecent: Boolean = true
    ) {
        val normalized = ParkingPlateEngine.normalizePlate(rawPlate)
        if (normalized.length < 4) {
            showToast("Введите корректный госномер (например, А 123 ВС 77)", "rose")
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val memoryCutoff = now - _uiState.value.parkingMemoryHours * 3600_000L
            val existingActive = repository.findActiveParkingByPlate(normalized, memoryCutoff)

            if (existingActive == null) {
                // Vehicle is entering the parking lot!
                val session = ParkingSessionEntity(
                    plateNumber = normalized,
                    vehicleTag = vehicleTag,
                    entryTimeMillis = now,
                    exitTimeMillis = null,
                    durationMinutes = null,
                    isCompleted = false,
                    lastUpdatedMillis = now,
                    source = source
                )
                repository.insertParkingSession(session)
                _uiState.update { it.copy(parkingDraftPlate = "") }
                playFeedback("plus")
                val entryStr = ParkingPlateEngine.formatTimestampHHMM(now)
                showToast("🚗 Заезд: $normalized в $entryStr", "cyan")
            } else {
                // Vehicle was already on the parking lot -> mark Exit and compute stay duration!
                val stayMillis = max(60_000L, now - existingActive.entryTimeMillis)
                if (!forceCheckoutIfRecent && stayMillis < 90_000L) {
                    // Debounce accidental double-scan within 90s from camera
                    return@launch
                }
                val stayMinutes = max(1, (stayMillis / 60_000.0).roundToInt())
                val updated = existingActive.copy(
                    exitTimeMillis = now,
                    durationMinutes = stayMinutes,
                    isCompleted = true,
                    lastUpdatedMillis = now
                )
                repository.updateParkingSession(updated)
                _uiState.update { it.copy(parkingDraftPlate = "") }
                playFeedback("camera")
                val durFormatted = ParkingPlateEngine.formatDurationMinutes(stayMinutes)
                showToast("🏁 Выезд: $normalized • Стоянка: $durFormatted", "amber")
            }
        }
    }

    fun checkoutParkingSessionById(sessionId: Long) {
        viewModelScope.launch {
            val existing = repository.getParkingById(sessionId) ?: return@launch
            if (existing.isCompleted) return@launch
            val now = System.currentTimeMillis()
            val stayMinutes = max(1, ((now - existing.entryTimeMillis) / 60_000.0).roundToInt())
            val updated = existing.copy(
                exitTimeMillis = now,
                durationMinutes = stayMinutes,
                isCompleted = true,
                lastUpdatedMillis = now
            )
            repository.updateParkingSession(updated)
            playFeedback("camera")
            val durFormatted = ParkingPlateEngine.formatDurationMinutes(stayMinutes)
            showToast("🏁 Выезд: ${existing.plateNumber} • Стоянка: $durFormatted", "amber")
        }
    }

    fun updateParkingSessionDetails(session: ParkingSessionEntity, newPlate: String, newTag: String) {
        val normalized = ParkingPlateEngine.normalizePlate(newPlate)
        if (normalized.length < 4) {
            showToast("Слишком короткий номер", "rose")
            return
        }
        viewModelScope.launch {
            repository.updateParkingSession(
                session.copy(
                    plateNumber = normalized,
                    vehicleTag = newTag,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(parkingEditingSession = null) }
            showToast("Номер обновлён: $normalized", "emerald")
        }
    }

    fun deleteParkingSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteParkingById(sessionId)
            showToast("Запись авто удалена", "rose")
        }
    }

    fun clearAllParkingSessions() {
        viewModelScope.launch {
            repository.clearAllParkingSessions()
            showToast("Журнал парковки очищен", "amber")
        }
    }

    // ================== ARCHIVE, RESET & EXPORT (PNG / CSV / CLIPBOARD) ==================
    fun saveCurrentShiftToArchive() {
        viewModelScope.launch {
            val state = _uiState.value
            val evList = events.value
            val parkList = parkingSessions.value
            val totalPed = computeTotalPedestrians(evList, state.streamCategories)
            val rateHour = computeHourlyRate(totalPed, state.elapsedSeconds)
            val camUnique = silhouettesMemory.value.size
            val exitedCars = parkList.filter { it.isCompleted }
            val avgStay = if (exitedCars.isNotEmpty()) {
                exitedCars.mapNotNull { it.durationMinutes }.average().roundToInt()
            } else 0

            val streamsSum = state.streamCategories.sumOf { max(0, it.count) }
            val streamsText = if (streamsSum > 0) {
                state.streamCategories.joinToString(", ") { "${it.name}: ${it.count}" }
            } else {
                "Общий поток: $totalPed чел."
            }

            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val shift = SavedShiftEntity(
                locationName = state.locationName.ifBlank { "Точка замера" },
                dateStr = sdf.format(Date()),
                durationFormatted = formatDurationSeconds(state.elapsedSeconds),
                totalPedestrians = totalPed,
                hourlyRate = rateHour,
                cameraUniqueSilhouettes = camUnique,
                cameraRepeatsBlocked = state.camSkippedRepeats,
                streamsBreakdownText = streamsText,
                parkingTotalVehicles = parkList.size,
                parkingExitedVehicles = exitedCars.size,
                parkingAvgStayMinutes = avgStay,
                notes = state.reportNotes.trim()
            )
            repository.insertSavedShift(shift)
            showToast("Замер сохранён в архив устройства", "emerald")
        }
    }

    fun deleteSavedShift(id: Long) {
        viewModelScope.launch {
            repository.deleteSavedShiftById(id)
            showToast("Запись удалена из архива", "rose")
        }
    }

    fun performFullReset(saveFirst: Boolean) {
        viewModelScope.launch {
            if (saveFirst) {
                saveCurrentShiftToArchive()
            }
            repository.clearAllEvents()
            repository.clearAllSilhouettes()
            repository.clearAllParkingSessions()
            repository.setElapsedSeconds(0L)
            repository.resetSessionStartTs()
            repository.setCameraSkippedRepeats(0)
            silhouetteEngine.resetTracks()

            val resetCats = _uiState.value.streamCategories.map { it.copy(count = 0) }
            persistCategories(resetCats)

            _uiState.update {
                it.copy(
                    elapsedSeconds = 0L,
                    sessionStartTs = System.currentTimeMillis(),
                    streamCategories = resetCats,
                    camDetectedBoxes = emptyList(),
                    camSkippedRepeats = 0,
                    resetDialogOpen = false
                )
            }
            showToast("Все счётчики, таймер и память очищены", "cyan")
        }
    }

    fun copyReportToClipboard(context: Context) {
        val state = _uiState.value
        val evList = events.value
        val parkList = parkingSessions.value
        val totalPed = computeTotalPedestrians(evList, state.streamCategories)
        val rateHour = computeHourlyRate(totalPed, state.elapsedSeconds)
        val exitedCars = parkList.filter { it.isCompleted }
        val insideCars = parkList.size - exitedCars.size
        val avgStay = if (exitedCars.isNotEmpty()) {
            exitedCars.mapNotNull { it.durationMinutes }.average().roundToInt()
        } else 0

        val streamsSum = state.streamCategories.sumOf { max(0, it.count) }
        val breakdown = if (streamsSum > 0) {
            state.streamCategories.joinToString("\n") { c ->
                val pct = ((c.count.toFloat() / streamsSum) * 100f).roundToInt()
                "• ${c.name}: ${c.count} чел. ($pct%)"
            }
        } else {
            "• Общий поток (уникальные): $totalPed чел."
        }

        val parkingLines = if (parkList.isNotEmpty()) {
            buildString {
                appendLine("\n🚗 АНАЛИЗ ПАРКОВКИ (Память ${state.parkingMemoryHours} ч):")
                appendLine("• Всего авто зафиксировано: ${parkList.size} (сейчас на парковке: $insideCars, выехало: ${exitedCars.size})")
                if (exitedCars.isNotEmpty()) {
                    appendLine("• Среднее время стоянки: ${ParkingPlateEngine.formatDurationMinutes(avgStay)}")
                }
                parkList.take(10).forEach { p ->
                    val inTime = ParkingPlateEngine.formatTimestampHHMM(p.entryTimeMillis)
                    if (p.isCompleted && p.exitTimeMillis != null) {
                        val outTime = ParkingPlateEngine.formatTimestampHHMM(p.exitTimeMillis)
                        val dur = ParkingPlateEngine.formatDurationMinutes(p.durationMinutes ?: 1)
                        appendLine("  - ${p.plateNumber} (${p.vehicleTag}): заезд $inTime -> выезд $outTime (стоянка $dur)")
                    } else {
                        appendLine("  - ${p.plateNumber} (${p.vehicleTag}): заезд $inTime (на парковке)")
                    }
                }
            }
        } else ""

        val reportText = buildString {
            appendLine("📊 СВОДКА ЗАМЕРА ТРАФИКА И ПАРКОВКИ")
            appendLine("📍 Точка: ${state.locationName}")
            appendLine("⏱ Длительность замера: ${formatDurationSeconds(state.elapsedSeconds)} (память силуэтов: ${state.silhouetteMemoryMinutes} мин)")
            appendLine("👥 Проходимость: $totalPed чел. (средний темп: $rateHour чел/ч)")
            if (silhouettesMemory.value.isNotEmpty() || state.camSkippedRepeats > 0) {
                appendLine("🚶 ИИ-детектор силуэтов: ${silhouettesMemory.value.size} уникальных (отсечено повторов: ${state.camSkippedRepeats})")
            }
            appendLine(breakdown)
            if (parkingLines.isNotBlank()) append(parkingLines)
            if (state.reportNotes.isNotBlank()) {
                appendLine("📝 Заметка: ${state.reportNotes}")
            }
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Отчёт трафика", reportText))
        showToast("Отчёт скопирован в буфер обмена", "emerald")
    }

    fun shareCsvExport(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val evList = events.value
                val parkList = parkingSessions.value
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                val sb = StringBuilder("\uFEFF")
                sb.appendLine("Тип записи;Время начала / клика;Время выезда;Локация;Объект / Госномер;Значение / Длительность")

                for (ev in evList) {
                    sb.appendLine(
                        "Пешеходный трафик;${sdf.format(Date(ev.timestamp))};—;\"${state.locationName}\";\"${ev.label}\";${ev.delta}"
                    )
                }
                for (p in parkList) {
                    val inStr = sdf.format(Date(p.entryTimeMillis))
                    val outStr = p.exitTimeMillis?.let { sdf.format(Date(it)) } ?: "На парковке"
                    val durStr = p.durationMinutes?.let { ParkingPlateEngine.formatDurationMinutes(it) } ?: "Активно"
                    sb.appendLine(
                        "Парковка (Авто);$inStr;$outStr;\"${state.locationName}\";\"${p.plateNumber} (${p.vehicleTag})\";\"$durStr\""
                    )
                }

                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, "traffic_parking_report.csv")
                file.writeText(sb.toString(), Charsets.UTF_8)

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Экспорт таблицы CSV (Excel)"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    copyReportToClipboard(context)
                }
            }
        }
    }

    fun sharePngReportCard(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val evList = events.value
                val parkList = parkingSessions.value
                val totalPed = computeTotalPedestrians(evList, state.streamCategories)
                val rateHour = computeHourlyRate(totalPed, state.elapsedSeconds)
                val exitedCars = parkList.filter { it.isCompleted }
                val avgStay = if (exitedCars.isNotEmpty()) {
                    exitedCars.mapNotNull { it.durationMinutes }.average().roundToInt()
                } else 0

                val width = 1080
                val height = 820
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0F17") }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#10B981")
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawRoundRect(RectF(28f, 28f, width - 28f, height - 28f), 32f, 32f, borderPaint)

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#10B981")
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("СЧЁТЧИК ТРАФИКА / ПРОХОДИМОСТИ • ОТЧЁТ ЗАМЕРА", 64f, 84f, titlePaint)

                val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 44f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(state.locationName.take(36), 64f, 142f, locPaint)

                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#94A3B8")
                    textSize = 22f
                }
                val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                canvas.drawText(
                    "Дата: $dateStr   •   Длительность: ${formatDurationSeconds(state.elapsedSeconds)}   •   Память силуэтов: ${state.silhouetteMemoryMinutes} мин",
                    64f,
                    184f,
                    subPaint
                )

                // Draw 3 KPI Cards
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#162032") }
                val kpis = listOf(
                    Triple("ПЕШЕХОДОВ ВСЕГО", "$totalPed чел.", "#34D399"),
                    Triple("ТЕМП В ЧАС", "$rateHour чел/ч", "#38BDF8"),
                    Triple("АВТО НА ПАРКОВКЕ", "${parkList.size} авто", "#FBBF24")
                )
                kpis.forEachIndexed { idx, (label, value, hex) ->
                    val left = 64f + idx * 324f
                    canvas.drawRoundRect(RectF(left, 216f, left + 300f, 360f), 24f, 24f, cardPaint)
                    val lPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#94A3B8")
                        textSize = 20f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText(label, left + 24f, 258f, lPaint)

                    val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor(hex)
                        textSize = 46f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    }
                    canvas.drawText(value, left + 24f, 326f, vPaint)
                }

                // Streams & Parking summary
                val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E2E8F0")
                    textSize = 26f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("Пешеходные потоки и ИИ-силуэты:", 64f, 418f, sectionPaint)

                val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#CBD5E1")
                    textSize = 23f
                }
                var curY = 460f
                state.streamCategories.take(4).forEach { cat ->
                    canvas.drawText("• ${cat.name}: ${cat.count} чел.", 64f, curY, rowPaint)
                    curY += 38f
                }
                canvas.drawText(
                    "• ИИ-детектор: учтено ${silhouettesMemory.value.size} уник., отсечено повторов: ${state.camSkippedRepeats}",
                    64f,
                    curY + 8f,
                    rowPaint
                )

                canvas.drawText("Статистика парковки (память ${state.parkingMemoryHours} ч):", 560f, 418f, sectionPaint)
                var parkY = 460f
                canvas.drawText(
                    "• Заехало: ${parkList.size} | Выехало: ${exitedCars.size}",
                    560f,
                    parkY,
                    rowPaint
                )
                parkY += 38f
                canvas.drawText(
                    "• Средняя стоянка: ${if (avgStay > 0) ParkingPlateEngine.formatDurationMinutes(avgStay) else "—"}",
                    560f,
                    parkY,
                    rowPaint
                )
                parkY += 42f
                parkList.take(4).forEach { p ->
                    val inT = ParkingPlateEngine.formatTimestampHHMM(p.entryTimeMillis)
                    val status = if (p.isCompleted && p.exitTimeMillis != null) {
                        "выезд ${ParkingPlateEngine.formatTimestampHHMM(p.exitTimeMillis)} (${p.durationMinutes ?: 1} м)"
                    } else "на парковке с $inT"
                    canvas.drawText("• ${p.plateNumber}: $status", 560f, parkY, rowPaint)
                    parkY += 36f
                }

                if (state.reportNotes.isNotBlank()) {
                    val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#FBBF24")
                        textSize = 22f
                    }
                    canvas.drawText("Заметка: ${state.reportNotes.take(65)}", 64f, 756f, notePaint)
                }

                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outFile = File(exportDir, "traffic_report_card.png")
                FileOutputStream(outFile).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }

                withContext(Dispatchers.Main) {
                    showToast("PNG-карточка отчёта сформирована", "emerald")
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            outFile
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Отправить карточку замера (PNG)"))
                    } catch (_: Exception) {
                        copyReportToClipboard(context)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    copyReportToClipboard(context)
                }
            }
        }
    }

    // ================== SOUND & HAPTIC FEEDBACK ==================
    private var toneGen: ToneGenerator? = null

    private fun playFeedback(type: String) {
        val state = _uiState.value
        if (state.soundEnabled) {
            try {
                if (toneGen == null) {
                    toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
                }
                val tone = when (type) {
                    "minus" -> ToneGenerator.TONE_PROP_NACK
                    "camera" -> ToneGenerator.TONE_PROP_ACK
                    else -> ToneGenerator.TONE_PROP_BEEP
                }
                toneGen?.startTone(tone, 45)
            } catch (_: Exception) {
                // Ignore audio errors on headless/restricted devices
            }
        }

        if (state.vibrateEnabled) {
            try {
                val app = getApplication<Application>()
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(30L)
                    }
                }
            } catch (_: Exception) {
                // Ignore vibrator error
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            toneGen?.release()
            toneGen = null
        } catch (_: Exception) {
        }
    }

    companion object {
        fun computeTotalPedestrians(
            events: List<TrafficEventEntity>,
            categories: List<StreamCategory>
        ): Int {
            val eventsSum = max(0, events.sumOf { it.delta })
            val streamsSum = categories.sumOf { max(0, it.count) }
            return max(eventsSum, streamsSum)
        }

        fun computeHourlyRate(total: Int, elapsedSeconds: Long): Int {
            if (total <= 0) return 0
            val effectiveHours = max(elapsedSeconds, 60L) / 3600.0
            return (total / effectiveHours).roundToInt()
        }

        fun computeLast15MinCount(events: List<TrafficEventEntity>): Int {
            val cutoff = System.currentTimeMillis() - 15 * 60_000L
            return max(0, events.filter { it.timestamp >= cutoff }.sumOf { it.delta })
        }

        fun computeAvgGapFormatted(events: List<TrafficEventEntity>): String {
            val pos = events.filter { it.delta > 0 }
            if (pos.size < 2) return "—"
            val spanSec = (pos.last().timestamp - pos.first().timestamp) / 1000L
            if (spanSec <= 0L) return "< 1 с"
            val avg = (spanSec.toDouble() / (pos.size - 1)).roundToInt()
            return if (avg < 60) "${avg} с" else "${(avg / 60.0).roundToInt()} м"
        }

        fun formatDurationSeconds(sec: Long): String {
            val s = max(0L, sec)
            val hrs = s / 3600L
            val mins = (s % 3600L) / 60L
            val secs = s % 60L
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
        }

        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = TrafficDatabase.getInstance(application)
                    val repo = TrafficRepository(db.trafficDao(), application)
                    return TrafficViewModel(application, repo) as T
                }
            }
    }
}
