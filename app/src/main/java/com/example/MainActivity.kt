package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppTab
import com.example.ui.TrafficUiState
import com.example.ui.TrafficViewModel
import com.example.ui.screens.BlindModeOverlay
import com.example.ui.screens.CounterScreen
import com.example.ui.screens.ParkingScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.SilhouetteCameraScreen
import com.example.ui.screens.StreamsScreen
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanBright
import com.example.ui.theme.CyanParking
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TrafficCounterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = LocalContext.current.applicationContext as Application
            val viewModel: TrafficViewModel = viewModel(
                factory = TrafficViewModel.provideFactory(app)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            TrafficCounterTheme(darkTheme = uiState.darkTheme) {
                TrafficCounterApp(viewModel = viewModel, uiState = uiState)
            }
        }
    }
}

@Composable
fun TrafficCounterApp(
    viewModel: TrafficViewModel,
    uiState: TrafficUiState
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val silhouettesMemory by viewModel.silhouettesMemory.collectAsStateWithLifecycle()
    val parkingSessions by viewModel.parkingSessions.collectAsStateWithLifecycle()
    val savedShifts by viewModel.savedShifts.collectAsStateWithLifecycle()

    val totalPedestrians = TrafficViewModel.computeTotalPedestrians(events, uiState.streamCategories)
    val rateHour = TrafficViewModel.computeHourlyRate(totalPedestrians, uiState.elapsedSeconds)
    val activeCarsCount = parkingSessions.count { !it.isCompleted }

    var showLocationDialog by remember { mutableStateOf(false) }

    // BackHandler for secondary modes and non-home tabs
    BackHandler(
        enabled = uiState.blindModeOpen || uiState.cleanScreenshotMode || uiState.activeTab != AppTab.COUNTER
    ) {
        when {
            uiState.blindModeOpen -> viewModel.setBlindMode(false)
            uiState.cleanScreenshotMode -> viewModel.setCleanScreenshotMode(false)
            uiState.activeTab != AppTab.COUNTER -> viewModel.selectTab(AppTab.COUNTER)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (!uiState.cleanScreenshotMode) {
                    TopTelemetryHeader(
                        uiState = uiState,
                        totalPedestrians = totalPedestrians,
                        rateHour = rateHour,
                        activeCarsCount = activeCarsCount,
                        onOpenLocationEdit = { showLocationDialog = true },
                        onToggleTimer = viewModel::toggleTimer,
                        onToggleSound = viewModel::toggleSound,
                        onToggleVibrate = viewModel::toggleVibrate,
                        onToggleTheme = viewModel::toggleTheme,
                        onOpenResetDialog = { viewModel.setResetDialog(true) }
                    )
                }
            },
            bottomBar = {
                if (!uiState.cleanScreenshotMode) {
                    BottomModeNavigationBar(
                        activeTab = uiState.activeTab,
                        silhouettesCount = silhouettesMemory.size,
                        activeParkingCount = activeCarsCount,
                        onSelectTab = viewModel::selectTab
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                when (uiState.activeTab) {
                    AppTab.COUNTER -> CounterScreen(
                        uiState = uiState,
                        events = events,
                        onManualClick = viewModel::recordManualClick,
                        onUndoLast = viewModel::undoLastEvent,
                        onOpenBlindMode = { viewModel.setBlindMode(true) }
                    )

                    AppTab.STREAMS -> StreamsScreen(
                        uiState = uiState,
                        onSelectPreset = viewModel::selectStreamPreset,
                        onStreamClick = viewModel::recordStreamClick,
                        onOpenAddCategory = { viewModel.setAddCategoryDialog(true) },
                        onDismissAddCategory = { viewModel.setAddCategoryDialog(false) },
                        onConfirmAddCategory = viewModel::addCustomStreamCategory
                    )

                    AppTab.SILHOUETTES -> SilhouetteCameraScreen(
                        uiState = uiState,
                        silhouettesMemory = silhouettesMemory,
                        onStartRealCamera = viewModel::startRealSilhouetteCamera,
                        onStartSimulation = viewModel::startSilhouetteSimulation,
                        onStopCamera = viewModel::stopSilhouetteCamera,
                        onFlipCamera = viewModel::flipCameraLens,
                        onRealFrame = viewModel::onRealCameraRgbFrame,
                        onSetFarHorizonBoost = viewModel::setCamFarHorizonBoost,
                        onSetStationaryLineMode = viewModel::setCamStationaryLineMode,
                        onSetLineVertical = viewModel::setCamLineOrientation,
                        onSetLinePos = viewModel::setCamLinePos,
                        onSetStepFilter = viewModel::setCamStepFilter,
                        onSetSyncToMain = viewModel::setCamSyncToMain,
                        onSetSensitivity = viewModel::setSilhouetteSensitivity,
                        onSetMemoryMinutes = viewModel::setSilhouetteMemoryMinutes,
                        onClearMemoryBank = viewModel::clearSilhouetteMemoryBank
                    )

                    AppTab.PARKING -> ParkingScreen(
                        uiState = uiState,
                        parkingSessions = parkingSessions,
                        onUpdateDraftPlate = viewModel::updateParkingDraftPlate,
                        onAppendSymbol = viewModel::appendSymbolToDraftPlate,
                        onBackspaceDraft = viewModel::backspaceDraftPlate,
                        onSelectVehicleTag = viewModel::selectParkingTag,
                        onSubmitPlate = { raw -> viewModel.registerOrCheckoutPlate(rawPlate = raw) },
                        onCheckoutById = viewModel::checkoutParkingSessionById,
                        onStartScanner = viewModel::startParkingScanner,
                        onStopScanner = viewModel::stopParkingScanner,
                        onCameraLumaFrame = viewModel::onParkingCameraLumaFrame,
                        onSetMemoryHours = viewModel::setParkingMemoryHours,
                        onSetFilterMode = viewModel::setParkingFilterMode,
                        onOpenEditSession = viewModel::openEditParkingDialog,
                        onConfirmEditSession = viewModel::updateParkingSessionDetails,
                        onDeleteSession = viewModel::deleteParkingSession,
                        onClearAllParking = viewModel::clearAllParkingSessions
                    )

                    AppTab.REPORT -> ReportScreen(
                        uiState = uiState,
                        events = events,
                        silhouettesMemory = silhouettesMemory,
                        parkingSessions = parkingSessions,
                        savedShifts = savedShifts,
                        onUpdateNotes = viewModel::updateReportNotes,
                        onEnterCleanScreenshot = { viewModel.setCleanScreenshotMode(true) },
                        onSharePngCard = viewModel::sharePngReportCard,
                        onCopyReportText = viewModel::copyReportToClipboard,
                        onShareCsv = viewModel::shareCsvExport,
                        onSaveShiftToArchive = viewModel::saveCurrentShiftToArchive,
                        onDeleteSavedShift = viewModel::deleteSavedShift
                    )
                }
            }
        }

        // Exit Clean Screenshot Mode Floating Pill
        if (uiState.cleanScreenshotMode) {
            Surface(
                onClick = { viewModel.setCleanScreenshotMode(false) },
                color = CyanParking,
                shape = CircleShape,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .testTag("btn_exit_clean_screenshot")
            ) {
                Text(
                    text = "✕ Вернуть панели",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF042F2E),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Full-Screen Blind Mode Overlay
        if (uiState.blindModeOpen) {
            BlindModeOverlay(
                totalCount = totalPedestrians,
                onIncrement = viewModel::recordManualClick,
                onClose = { viewModel.setBlindMode(false) }
            )
        }

        // Floating Toast Notification
        AnimatedVisibility(
            visible = uiState.toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp)
        ) {
            uiState.toast?.let { t ->
                val accent = when (t.tone) {
                    "rose" -> RoseDanger
                    "amber" -> AmberAlert
                    "cyan" -> CyanBright
                    else -> EmeraldBright
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xF00F172A),
                    border = BorderStroke(1.5.dp, accent),
                    shadowElevation = 10.dp
                ) {
                    Text(
                        text = t.text,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    if (showLocationDialog) {
        EditLocationDialog(
            currentName = uiState.locationName,
            onDismiss = { showLocationDialog = false },
            onConfirm = { newName ->
                viewModel.updateLocationName(newName)
                showLocationDialog = false
            }
        )
    }

    if (uiState.resetDialogOpen) {
        ResetShiftConfirmationDialog(
            totalPedestrians = totalPedestrians,
            totalCars = parkingSessions.size,
            onDismiss = { viewModel.setResetDialog(false) },
            onSaveAndReset = { viewModel.performFullReset(saveFirst = true) },
            onHardReset = { viewModel.performFullReset(saveFirst = false) }
        )
    }
}

@Composable
private fun TopTelemetryHeader(
    uiState: TrafficUiState,
    totalPedestrians: Int,
    rateHour: Int,
    activeCarsCount: Int,
    onOpenLocationEdit: () -> Unit,
    onToggleTimer: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleVibrate: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenResetDialog: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Brand Badge + Editable Location + Live Shift Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenLocationEdit)
                        .padding(vertical = 2.dp)
                        .testTag("header_location_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(EmeraldPrimary, CyanParking)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "СТ",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF022C22),
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = uiState.locationName.ifBlank { "Точка замера" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = AppIcons.EditLocationAlt,
                                contentDescription = "Изменить точку",
                                tint = EmeraldBright,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$totalPedestrians чел ($rateHour/ч)",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = EmeraldBright
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Авто: $activeCarsCount",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = CyanBright
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Live Timer Pill with Start/Pause
                Surface(
                    onClick = onToggleTimer,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (uiState.timerRunning) EmeraldPrimary.copy(alpha = 0.5f) else AmberAlert.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("btn_toggle_timer")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.timerRunning) EmeraldBright else AmberAlert)
                        )
                        Text(
                            text = TrafficViewModel.formatDurationSeconds(uiState.elapsedSeconds),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (uiState.timerRunning) AppIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Старт или пауза таймера",
                            tint = if (uiState.timerRunning) EmeraldBright else AmberAlert,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Row 2: Compact Quick Utility Bar (Memory Badges + Sound/Vibrate/Theme/Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = EmeraldPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🚶 Память: ${uiState.silhouetteMemoryMinutes} мин",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = EmeraldBright,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = CyanParking.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🚗 Авто: ${uiState.parkingMemoryHours} ч",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = CyanBright,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleSound,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.soundEnabled) {
                                AppIcons.VolumeUp
                            } else {
                                AppIcons.VolumeOff
                            },
                            contentDescription = "Звук клика",
                            tint = if (uiState.soundEnabled) EmeraldBright else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleVibrate,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Vibration,
                            contentDescription = "Виброотклик",
                            tint = if (uiState.vibrateEnabled) EmeraldBright else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.darkTheme) AppIcons.DarkMode else AppIcons.LightMode,
                            contentDescription = "Переключить тему",
                            tint = AmberAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Surface(
                        onClick = onOpenResetDialog,
                        color = RoseDanger.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RoseDanger.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("btn_open_reset_modal")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.RestartAlt,
                                contentDescription = null,
                                tint = RoseDanger,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Сброс",
                                style = MaterialTheme.typography.labelMedium,
                                color = RoseDanger,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomModeNavigationBar(
    activeTab: AppTab,
    silhouettesCount: Int,
    activeParkingCount: Int,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationBar(
        windowInsets = WindowInsets.navigationBars,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        val items = listOf(
            Triple(AppTab.COUNTER, "Счётчик", AppIcons.TouchApp),
            Triple(AppTab.STREAMS, "Потоки", AppIcons.CallSplit),
            Triple(AppTab.SILHOUETTES, "Силуэты", AppIcons.Radar),
            Triple(AppTab.PARKING, "Парковка", AppIcons.DirectionsCar),
            Triple(AppTab.REPORT, "Отчёт", AppIcons.Assessment)
        )

        items.forEach { (tab, label, icon) ->
            val selected = activeTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    val badgeCount = when (tab) {
                        AppTab.SILHOUETTES -> silhouettesCount
                        AppTab.PARKING -> activeParkingCount
                        else -> 0
                    }
                    if (badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = if (tab == AppTab.PARKING) CyanParking else EmeraldPrimary,
                                    contentColor = Color(0xFF022C22)
                                ) {
                                    Text("$badgeCount")
                                }
                            }
                        ) {
                            Icon(imageVector = icon, contentDescription = label)
                        }
                    } else {
                        Icon(imageVector = icon, contentDescription = label)
                    }
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (tab == AppTab.PARKING) CyanBright else EmeraldBright,
                    selectedTextColor = if (tab == AppTab.PARKING) CyanBright else EmeraldBright,
                    indicatorColor = if (tab == AppTab.PARKING) {
                        CyanParking.copy(alpha = 0.20f)
                    } else {
                        EmeraldPrimary.copy(alpha = 0.20f)
                    }
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun EditLocationDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название точки замера", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Укажите адрес, ТЦ, парковку или сторону улицы для отчёта:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.trim()) }) {
                Text("Сохранить", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun ResetShiftConfirmationDialog(
    totalPedestrians: Int,
    totalCars: Int,
    onDismiss: () -> Unit,
    onSaveAndReset: () -> Unit,
    onHardReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Завершить или обнулить замер?", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Сейчас зафиксировано $totalPedestrians чел. и $totalCars авто на парковке. Вы можете сохранить текущий замер в архив перед сбросом.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onSaveAndReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color(0xFF022C22)
                    )
                ) {
                    Text("💾 Сохранить в архив и обнулить", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onHardReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseDanger)
                ) {
                    Text("🗑 Просто обнулить без сохранения", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
