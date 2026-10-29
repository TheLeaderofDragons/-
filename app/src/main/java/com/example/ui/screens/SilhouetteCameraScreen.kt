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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.example.ui.theme.AppIcons
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SilhouetteMemoryEntity
import com.example.domain.DetectedSilhouetteBox
import com.example.domain.ParkingPlateEngine
import com.example.ui.TrafficUiState
import com.example.ui.components.CameraFeedPreview
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanParking
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseDanger

@Composable
fun SilhouetteCameraScreen(
    uiState: TrafficUiState,
    silhouettesMemory: List<SilhouetteMemoryEntity>,
    onStartRealCamera: () -> Unit,
    onStartSimulation: () -> Unit,
    onStopCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onRealFrame: (IntArray, IntArray, IntArray) -> Unit,
    onSetFarHorizonBoost: (Boolean) -> Unit,
    onSetStationaryLineMode: (Boolean) -> Unit,
    onSetLineVertical: (Boolean) -> Unit,
    onSetLinePos: (Float) -> Unit,
    onSetStepFilter: (Boolean) -> Unit,
    onSetSyncToMain: (Boolean) -> Unit,
    onSetSensitivity: (Int) -> Unit,
    onSetMemoryMinutes: (Int) -> Unit,
    onClearMemoryBank: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Radar Control Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = AppIcons.Radar,
                            contentDescription = null,
                            tint = EmeraldBright
                        )
                        Column {
                            Text(
                                text = "ИИ-Радар силуэтов (Вдали и вблизи)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Зум горизонта 2.5x для дальних фигур • Память Re-ID ${uiState.silhouetteMemoryMinutes} мин",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val statusLabel = when {
                        !uiState.camRunning -> "Выкл"
                        uiState.camSimMode -> "Демо 2.5x"
                        else -> "Камера 2.5x"
                    }
                    Surface(
                        color = if (uiState.camRunning) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.camRunning) EmeraldBright else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStartRealCamera,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color(0xFF022C22)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_start_silhouette_cam")
                    ) {
                        Icon(AppIcons.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Камера",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onStartSimulation,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanParking,
                            contentColor = Color(0xFF042F2E)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_start_silhouette_sim")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Демо-улица",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onFlipCamera,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = AppIcons.Cameraswitch,
                            contentDescription = "Повернуть камеру",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (uiState.camRunning) {
                        IconButton(
                            onClick = onStopCamera,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoseDanger.copy(alpha = 0.22f))
                                .testTag("btn_stop_silhouette_cam")
                        ) {
                            Icon(
                                imageVector = AppIcons.Stop,
                                contentDescription = "Остановить радар",
                                tint = RoseDanger
                            )
                        }
                    }
                }
            }
        }

        // Video / Radar Viewport (16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9.5f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF060A12))
        ) {
            if (!uiState.camRunning) {
                // Idle Placeholder explaining upgraded far-field sensitivity
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Дальнобойный захват силуэтов (через дорогу и в сумерках)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Повышенная чувствительность (от 3.2% высоты экрана) + 2.5x зум полосы горизонта. Силуэты хранятся в памяти 1.5 часа (90 мин), исключая повторный счёт одних и тех же людей.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onStartRealCamera,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Включить камеру", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onStartSimulation,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Запустить демо-улицу", color = CyanParking, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                if (!uiState.camSimMode) {
                    CameraFeedPreview(
                        useFrontLens = uiState.camUseFrontLens,
                        onRgbAndLumaFrame = { r, g, b, _ ->
                            onRealFrame(r, g, b)
                        },
                        onFallbackToSimulation = onStartSimulation,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Radar HUD Canvas Overlay (draws twilight street in sim mode + 2.5x horizon zoom zone + tracked silhouette boxes)
                SilhouetteRadarHudCanvas(
                    simMode = uiState.camSimMode,
                    farHorizonBoost = uiState.camFarHorizonBoost,
                    stationaryLineMode = uiState.camStationaryLineMode,
                    lineVertical = uiState.camLineVertical,
                    linePos = uiState.camLinePos,
                    stepFilter = uiState.camStepFilter,
                    boxes = uiState.camDetectedBoxes,
                    modifier = Modifier.fillMaxSize()
                )

                // Top HUD status pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xCC0B0F17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (uiState.camIsShaking) {
                                "✋ Стабилизация поворота руки..."
                            } else if (uiState.camFarHorizonBoost) {
                                "🔭 Дальний радар 2.5x активен (захват от 3.2%)"
                            } else {
                                "🚶 Стандартный обзор силуэтов"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.camIsShaking) AmberAlert else EmeraldBright,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        color = Color(0xCC0B0F17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanParking.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "В базе (1.5ч): ${silhouettesMemory.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = CyanParking,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 3 Camera Counters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryStatCard(
                title = "УНИКАЛЬНЫХ",
                value = "${silhouettesMemory.size}",
                subtitle = "за ${uiState.silhouetteMemoryMinutes} мин (+1)",
                valueColor = EmeraldBright,
                modifier = Modifier.weight(1f)
            )
            TelemetryStatCard(
                title = "В КАДРЕ",
                value = "${uiState.camDetectedBoxes.size}",
                subtitle = "силуэтов сейчас",
                valueColor = CyanParking,
                modifier = Modifier.weight(1f)
            )
            TelemetryStatCard(
                title = "ПОВТОРОВ",
                value = "${uiState.camSkippedRepeats}",
                subtitle = "отсечено Re-ID",
                valueColor = AmberAlert,
                modifier = Modifier.weight(1f)
            )
        }

        // Re-ID Memory Gallery Strip (90-Min Memory Bank)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ПАМЯТЬ СИЛУЭТОВ RE-ID (${uiState.silhouetteMemoryMinutes} МИН / 1.5 ЧАСА)",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldBright
                        )
                        Text(
                            text = "Хранит тон одежды (верх/низ) и пропорции фигуры, чтобы не считать повторно",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onClearMemoryBank,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_clear_silhouette_memory")
                    ) {
                        Icon(AppIcons.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистить", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (silhouettesMemory.isEmpty()) {
                    Text(
                        text = "Память пока пуста — включите камеру или демо-улицу для захвата дальних и ближних силуэтов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        silhouettesMemory.forEach { item ->
                            SilhouetteMemoryBadgeCard(item = item)
                        }
                    }
                }
            }
        }

        // Sensitivity & 90-Minute Memory Tuning Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "НАСТРОЙКА «ЧУЙКИ» И ПАМЯТИ (1.5 ЧАСА)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Sensitivity Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Чувствительность к силуэтам вдали",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${uiState.camSensitivity}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFamily,
                            color = EmeraldBright
                        )
                    }
                    Slider(
                        value = uiState.camSensitivity.toFloat(),
                        onValueChange = { onSetSensitivity(it.toInt()) },
                        valueRange = 35f..98f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldBright,
                            activeTrackColor = EmeraldPrimary
                        )
                    )
                    Text(
                        text = "На 80–95% радар распознаёт даже небольшие силуэты людей на другой стороне улицы",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Memory Duration (Default 90 minutes = 1.5 hours + quick presets)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Время памяти уникальных людей",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val hrsText = if (uiState.silhouetteMemoryMinutes == 90) {
                            "90 мин (1.5 часа)"
                        } else {
                            "${uiState.silhouetteMemoryMinutes} мин"
                        }
                        Text(
                            text = hrsText,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFamily,
                            color = CyanParking
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            45 to "45 мин",
                            90 to "1.5 часа (90 мин)",
                            120 to "2 часа (120 мин)",
                            180 to "3 часа (180 мин)"
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = uiState.silhouetteMemoryMinutes == mins,
                                onClick = { onSetMemoryMinutes(mins) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanParking.copy(alpha = 0.22f),
                                    selectedLabelColor = CyanParking
                                )
                            )
                        }
                    }

                    Slider(
                        value = uiState.silhouetteMemoryMinutes.toFloat(),
                        onValueChange = { onSetMemoryMinutes(it.toInt()) },
                        valueRange = 15f..180f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanParking,
                            activeTrackColor = CyanParking
                        )
                    )
                }

                // Toggles: 2.5x Far Horizon Boost, Step Filter, Sync to Main
                SwitchRowItem(
                    title = "🔭 Дальний радар 2.5x (захват силуэтов на горизонте)",
                    subtitle = "Прицельно усиливает контраст центральной полосы дороги для мелких фигур",
                    checked = uiState.camFarHorizonBoost,
                    onCheckedChange = onSetFarHorizonBoost
                )

                SwitchRowItem(
                    title = "🛡 Фильтр своих шагов и поворота руки",
                    subtitle = "Блокирует ложные срабатывания при ходьбе и повороте телефона",
                    checked = uiState.camStepFilter,
                    onCheckedChange = onSetStepFilter
                )

                SwitchRowItem(
                    title = "➕ Автоматически плюсовать в главный счётчик смены",
                    subtitle = "Каждый новый уникальный силуэт добавляет +1 в общий трафик",
                    checked = uiState.camSyncToMain,
                    onCheckedChange = onSetSyncToMain
                )

                SwitchRowItem(
                    title = "📏 Режим стационарной линии (для установки на штатив)",
                    subtitle = "Считать только при пересечении контрольной линии",
                    checked = uiState.camStationaryLineMode,
                    onCheckedChange = onSetStationaryLineMode
                )

                if (uiState.camStationaryLineMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = uiState.camLineVertical,
                            onClick = { onSetLineVertical(true) },
                            label = { Text("Вертикальная линия") }
                        )
                        FilterChip(
                            selected = !uiState.camLineVertical,
                            onClick = { onSetLineVertical(false) },
                            label = { Text("Горизонтальная линия") }
                        )
                    }
                    Slider(
                        value = uiState.camLinePos,
                        onValueChange = onSetLinePos,
                        valueRange = 0.2f..0.8f
                    )
                }
            }
        }
    }
}

@Composable
private fun SilhouetteRadarHudCanvas(
    simMode: Boolean,
    farHorizonBoost: Boolean,
    stationaryLineMode: Boolean,
    lineVertical: Boolean,
    linePos: Float,
    stepFilter: Boolean,
    boxes: List<DetectedSilhouetteBox>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        if (simMode) {
            // Draw realistic twilight street scene (sky, far sidewalk across the road, wide asphalt)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF475569)),
                    startY = 0f,
                    endY = h * 0.36f
                ),
                size = Size(w, h * 0.36f)
            )
            // Far sidewalk & greenery strip across the street
            drawRect(
                color = Color(0xFF1E3A2F),
                topLeft = Offset(0f, h * 0.32f),
                size = Size(w, h * 0.12f)
            )
            // Wide street asphalt
            drawRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(0f, h * 0.44f),
                size = Size(w, h * 0.56f)
            )
            // Dashed road divider line
            drawLine(
                color = Color(0x6694A3B8),
                start = Offset(0f, h * 0.62f),
                end = Offset(w, h * 0.62f),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 18f))
            )
        }

        // Draw 2.5x Horizon Far-Field Zoom Band (y = 14%..64%)
        if (farHorizonBoost) {
            val bandTop = h * 0.14f
            val bandHeight = h * 0.50f
            drawRect(
                color = Color(0x1406B6D4),
                topLeft = Offset(w * 0.04f, bandTop),
                size = Size(w * 0.92f, bandHeight)
            )
            drawRoundRect(
                color = Color(0x6606B6D4),
                topLeft = Offset(w * 0.04f, bandTop),
                size = Size(w * 0.92f, bandHeight),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            )
        }

        // Draw stationary tripwire line if enabled
        if (stationaryLineMode) {
            if (lineVertical) {
                val lx = w * linePos
                drawLine(
                    color = Color(0xFF10B981),
                    start = Offset(lx, 0f),
                    end = Offset(lx, h),
                    strokeWidth = 5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                )
            } else {
                val ly = h * linePos
                drawLine(
                    color = Color(0xFF10B981),
                    start = Offset(0f, ly),
                    end = Offset(w, ly),
                    strokeWidth = 5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                )
            }
        }

        // Draw step filter bottom guard zone
        if (stepFilter) {
            drawRect(
                color = Color(0x55080C14),
                topLeft = Offset(0f, h * 0.91f),
                size = Size(w, h * 0.09f)
            )
        }

        // Draw detected human silhouettes and bounding boxes
        boxes.forEach { box ->
            val rx = box.bx * w
            val ry = box.by * h
            val rw = box.bw * w
            val rh = box.bh * h

            val upperColor = parseSafeHexColor(box.upperHex, Color(0xFF334155))
            val lowerColor = parseSafeHexColor(box.lowerHex, Color(0xFF0F172A))

            if (simMode) {
                // Render stylized human silhouette figure (head + torso + legs)
                val headRadius = (rw * 0.22f).coerceAtLeast(4f)
                drawCircle(
                    color = upperColor,
                    radius = headRadius,
                    center = Offset(rx + rw * 0.5f, ry + headRadius * 1.1f)
                )
                drawRoundRect(
                    color = upperColor,
                    topLeft = Offset(rx + rw * 0.18f, ry + headRadius * 2.2f),
                    size = Size(rw * 0.64f, rh * 0.42f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRect(
                    color = lowerColor,
                    topLeft = Offset(rx + rw * 0.22f, ry + headRadius * 2.2f + rh * 0.42f),
                    size = Size(rw * 0.24f, rh * 0.36f)
                )
                drawRect(
                    color = lowerColor,
                    topLeft = Offset(rx + rw * 0.54f, ry + headRadius * 2.2f + rh * 0.42f),
                    size = Size(rw * 0.24f, rh * 0.36f)
                )
            }

            val boxColor = when {
                box.countedPersonId != null -> Color(0xFF10B981) // Emerald: Unique counted (+1)
                box.matchedKnownId != null -> Color(0xFFF59E0B)  // Amber: Already in 90-min memory
                else -> Color(0xFF38BDF8)                        // Cyan: Locking on
            }

            drawRoundRect(
                color = boxColor,
                topLeft = Offset(rx, ry),
                size = Size(rw, rh),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = if (box.isFarHorizon) 3.5f else 4.5f)
            )

            // Draw two-tone clothing fingerprint indicator above the bounding box
            val tagY = (ry - 18f).coerceAtLeast(6f)
            drawRect(
                color = upperColor,
                topLeft = Offset(rx, tagY),
                size = Size(14f, 7f)
            )
            drawRect(
                color = lowerColor,
                topLeft = Offset(rx, tagY + 7f),
                size = Size(14f, 7f)
            )
        }
    }
}

@Composable
private fun SilhouetteMemoryBadgeCard(item: SilhouetteMemoryEntity) {
    val upperColor = parseSafeHexColor(item.upperHex, Color(0xFF10B981))
    val lowerColor = parseSafeHexColor(item.lowerHex, Color(0xFF06B6D4))

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 2-tone silhouette clothing badge
            Column(
                modifier = Modifier
                    .width(24.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(upperColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(lowerColor)
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Силуэт #${item.personId}",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldBright
                    )
                    Text(
                        text = item.distanceZone,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = CyanParking
                    )
                }
                Text(
                    text = "Учтён в ${ParkingPlateEngine.formatTimestampHHMMSS(item.firstSeenTs)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (item.repeatHits > 0) {
                        "↺ Повторов отсечено: ${item.repeatHits}"
                    } else {
                        "✓ Уникальный (+1)"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = if (item.repeatHits > 0) AmberAlert else EmeraldBright
                )
            }
        }
    }
}

@Composable
fun SwitchRowItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

fun parseSafeHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
