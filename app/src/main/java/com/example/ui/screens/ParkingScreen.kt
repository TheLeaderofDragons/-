package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ParkingSessionEntity
import com.example.domain.ParkingPlateEngine
import com.example.ui.TrafficUiState
import com.example.ui.components.CameraFeedPreview
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanBright
import com.example.ui.theme.CyanParking
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseDanger
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ParkingScreen(
    uiState: TrafficUiState,
    parkingSessions: List<ParkingSessionEntity>,
    onUpdateDraftPlate: (String) -> Unit,
    onAppendSymbol: (String) -> Unit,
    onBackspaceDraft: () -> Unit,
    onSelectVehicleTag: (String) -> Unit,
    onSubmitPlate: (String) -> Unit,
    onCheckoutById: (Long) -> Unit,
    onStartScanner: (Boolean) -> Unit,
    onStopScanner: () -> Unit,
    onCameraLumaFrame: (IntArray) -> Unit,
    onSetMemoryHours: (Int) -> Unit,
    onSetFilterMode: (String) -> Unit,
    onOpenEditSession: (ParkingSessionEntity?) -> Unit,
    onConfirmEditSession: (ParkingSessionEntity, String, String) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onClearAllParking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCars = parkingSessions.filter { !it.isCompleted }
    val exitedCars = parkingSessions.filter { it.isCompleted }
    val avgStayMinutes = if (exitedCars.isNotEmpty()) {
        exitedCars.mapNotNull { it.durationMinutes }.average().roundToInt()
    } else 0

    // Real-time check if the currently typed plate matches an active car on the parking lot!
    val normalizedDraft = ParkingPlateEngine.normalizePlate(uiState.parkingDraftPlate)
    val matchingActiveCar = if (normalizedDraft.length >= 4) {
        activeCars.find { it.plateNumber == normalizedDraft }
    } else null

    val filteredList = when (uiState.parkingFilterMode) {
        "inside" -> activeCars
        "exited" -> exitedCars
        else -> parkingSessions
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top 4 KPI Cards for Parking Analytics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryStatCard(
                title = "ВСЕГО АВТО",
                value = "${parkingSessions.size}",
                subtitle = "за смену",
                valueColor = CyanBright,
                modifier = Modifier.weight(1f)
            )
            TelemetryStatCard(
                title = "ВНУТРИ",
                value = "${activeCars.size}",
                subtitle = "на парковке",
                valueColor = EmeraldBright,
                modifier = Modifier.weight(1f)
            )
            TelemetryStatCard(
                title = "ВЫЕХАЛИ",
                value = "${exitedCars.size}",
                subtitle = "завершено",
                valueColor = AmberAlert,
                modifier = Modifier.weight(1f)
            )
            TelemetryStatCard(
                title = "СТОЯНКА",
                value = if (avgStayMinutes > 0) "${avgStayMinutes}м" else "—",
                subtitle = "среднее время",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        // Camera ALPR Scanner & Demo Controls Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, CyanParking.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Авто-сканер госномеров (Память ${uiState.parkingMemoryHours} ч)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "1-я фиксация номера = ЗАЕЗД • Повторная фиксация = ВЫЕЗД + расчёт времени стоянки",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onStartScanner(false) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanParking,
                            contentColor = Color(0xFF042F2E)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_start_parking_cam")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Авто-сканер", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onStartScanner(true) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color(0xFF022C22)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_start_parking_sim")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Демо-парковка", fontWeight = FontWeight.Bold)
                    }

                    if (uiState.parkingScannerActive) {
                        IconButton(
                            onClick = onStopScanner,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoseDanger.copy(alpha = 0.22f))
                                .testTag("btn_stop_parking_scanner")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Остановить сканер номеров",
                                tint = RoseDanger
                            )
                        }
                    }
                }

                // Live / Simulated License Plate Scanner Viewport when active
                if (uiState.parkingScannerActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 7.5f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF060A12))
                    ) {
                        if (!uiState.parkingSimMode) {
                            CameraFeedPreview(
                                useFrontLens = false,
                                onRgbAndLumaFrame = { _, _, _, luma ->
                                    onCameraLumaFrame(luma)
                                },
                                onFallbackToSimulation = { onStartScanner(true) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Target Reticle Overlay for License Plate
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val boxW = w * 0.64f
                            val boxH = h * 0.42f
                            val left = (w - boxW) / 2f
                            val top = (h - boxH) / 2f

                            drawRoundRect(
                                color = Color(0xFF06B6D4),
                                topLeft = Offset(left, top),
                                size = Size(boxW, boxH),
                                cornerRadius = CornerRadius(16f, 16f),
                                style = Stroke(width = 4f)
                            )
                        }

                        val candidate = uiState.parkingLiveScanCandidate
                        if (candidate != null) {
                            Surface(
                                onClick = { onSubmitPlate(candidate) },
                                color = Color(0xE60B0F17),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, CyanBright),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Распознан: $candidate",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Black,
                                        color = CyanBright
                                    )
                                    Surface(
                                        color = CyanParking,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Подтвердить",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF042F2E),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fast Manual / Constructor License Plate Entry Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (matchingActiveCar != null) AmberAlert else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Быстрый ввод / проверка госномера",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (normalizedDraft.isNotEmpty()) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(2.dp, Color(0xFF0F172A))
                        ) {
                            Text(
                                text = normalizedDraft,
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Input Field + Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.parkingDraftPlate,
                        onValueChange = onUpdateDraftPlate,
                        placeholder = { Text("Например: А452МР77 или 452") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onSubmitPlate(uiState.parkingDraftPlate) }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("parking_plate_input")
                    )

                    IconButton(
                        onClick = onBackspaceDraft,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Стереть символ",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Quick ГОСТ Letters Row (А В Е К М Н О Р С Т У Х) + Digits for one-handed street entry
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ParkingPlateEngine.ALLOWED_GOST_LETTERS.forEach { letter ->
                        Surface(
                            onClick = { onAppendSymbol(letter) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "77", "99", "78").forEach { digit ->
                        Surface(
                            onClick = { onAppendSymbol(digit) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, CyanParking.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = digit,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = CyanBright,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Vehicle Category Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ParkingPlateEngine.VEHICLE_TAGS.forEach { tag ->
                        FilterChip(
                            selected = uiState.parkingSelectedTag == tag,
                            onClick = { onSelectVehicleTag(tag) },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanParking.copy(alpha = 0.22f),
                                selectedLabelColor = CyanBright
                            )
                        )
                    }
                }

                // Smart Submit Button: Automatically switches between "ЗАЕЗД" (Entry) and "ВЫЕЗД" (Exit)
                val isCheckout = matchingActiveCar != null
                Button(
                    onClick = { onSubmitPlate(uiState.parkingDraftPlate) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("btn_submit_parking_plate"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCheckout) AmberAlert else CyanParking,
                        contentColor = Color(0xFF04131A)
                    )
                ) {
                    Icon(
                        imageVector = if (isCheckout) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.DirectionsCar,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val btnLabel = if (matchingActiveCar != null) {
                        val inTime = ParkingPlateEngine.formatTimestampHHMM(matchingActiveCar.entryTimeMillis)
                        val minsNow = max(1, ((System.currentTimeMillis() - matchingActiveCar.entryTimeMillis) / 60_000.0).roundToInt())
                        "ОТМЕТИТЬ ВЫЕЗД (Заехал в $inTime • Стоянка $minsNow мин)"
                    } else {
                        "ЗАФИКСИРОВАТЬ ЗАЕЗД НА ПАРКОВКУ"
                    }
                    Text(
                        text = btnLabel,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Parking Memory Retention Window Configuration (Default 12h, 2..24h slider)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Время памяти номеров на парковке",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.parkingMemoryHours} ч",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = CyanBright
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        6 to "6 часов",
                        12 to "12 часов (По умолч.)",
                        18 to "18 часов",
                        24 to "24 часа (Сутки)"
                    ).forEach { (hrs, label) ->
                        FilterChip(
                            selected = uiState.parkingMemoryHours == hrs,
                            onClick = { onSetMemoryHours(hrs) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanParking.copy(alpha = 0.22f),
                                selectedLabelColor = CyanBright
                            )
                        )
                    }
                }

                Slider(
                    value = uiState.parkingMemoryHours.toFloat(),
                    onValueChange = { onSetMemoryHours(it.toInt()) },
                    valueRange = 2f..24f,
                    steps = 21,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanBright,
                        activeTrackColor = CyanParking
                    )
                )
            }
        }

        // Filter Tabs & Parking Session Log
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "all" to "Все (${parkingSessions.size})",
                    "inside" to "На парковке (${activeCars.size})",
                    "exited" to "Выехали (${exitedCars.size})"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = uiState.parkingFilterMode == key,
                        onClick = { onSetFilterMode(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            if (parkingSessions.isNotEmpty()) {
                TextButton(onClick = onClearAllParking) {
                    Text("Очистить", color = RoseDanger, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (filteredList.isEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = CyanParking,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Журнал парковки пока пуст",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Введите госномер выше или нажмите «Демо-парковка», чтобы увидеть автоматический учёт времени заезда и выезда",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            filteredList.forEach { session ->
                ParkingSessionCard(
                    session = session,
                    onCheckout = { onCheckoutById(session.id) },
                    onEdit = { onOpenEditSession(session) },
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }
    }

    val editing = uiState.parkingEditingSession
    if (editing != null) {
        EditParkingPlateDialog(
            session = editing,
            onDismiss = { onOpenEditSession(null) },
            onConfirm = { newPlate, newTag ->
                onConfirmEditSession(editing, newPlate, newTag)
            }
        )
    }
}

@Composable
private fun ParkingSessionCard(
    session: ParkingSessionEntity,
    onCheckout: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val entryStr = ParkingPlateEngine.formatTimestampHHMM(session.entryTimeMillis)
    val liveMinutes = max(1, ((now - session.entryTimeMillis) / 60_000.0).roundToInt())

    val borderColor = if (session.isCompleted) {
        AmberAlert.copy(alpha = 0.45f)
    } else {
        EmeraldPrimary.copy(alpha = 0.55f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High-contrast License Plate Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(2.dp, Color(0xFF0F172A))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.plateNumber,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = session.vehicleTag,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (session.source == "camera") "📷 Авто-сканер" else "⌨️ Ручной ввод",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Pill
                Surface(
                    color = if (session.isCompleted) AmberAlert.copy(alpha = 0.18f) else EmeraldPrimary.copy(alpha = 0.18f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (session.isCompleted) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (session.isCompleted) AmberAlert else EmeraldBright,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (session.isCompleted) {
                                "Стоянка: ${ParkingPlateEngine.formatDurationMinutes(session.durationMinutes ?: 1)}"
                            } else {
                                "На парковке: ${ParkingPlateEngine.formatDurationMinutes(liveMinutes)}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (session.isCompleted) AmberAlert else EmeraldBright,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Entry & Exit Timeline Row + Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Заехало авто в $entryStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmeraldBright,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (session.isCompleted && session.exitTimeMillis != null) {
                        val exitStr = ParkingPlateEngine.formatTimestampHHMM(session.exitTimeMillis)
                        val durStr = ParkingPlateEngine.formatDurationMinutes(session.durationMinutes ?: 1)
                        Text(
                            text = "Уехало в $exitStr (через $durStr)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmberAlert,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Ожидание выезда (при повторном скане/вводе закрывается авто)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!session.isCompleted) {
                        Button(
                            onClick = onCheckout,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberAlert,
                                contentColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.testTag("btn_checkout_car_${session.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Выезд", fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Исправить номер",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить запись",
                            tint = RoseDanger
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditParkingPlateDialog(
    session: ParkingSessionEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var plate by remember(session) { mutableStateOf(session.plateNumber) }
    var tag by remember(session) { mutableStateOf(session.vehicleTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Правка номера автомобиля", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text("Госномер") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ParkingPlateEngine.VEHICLE_TAGS.forEach { t ->
                        FilterChip(
                            selected = tag == t,
                            onClick = { tag = t },
                            label = { Text(t) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(plate, tag) }) {
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
