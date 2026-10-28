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
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ParkingSessionEntity
import com.example.data.SavedShiftEntity
import com.example.data.SilhouetteMemoryEntity
import com.example.data.TrafficEventEntity
import com.example.domain.ParkingPlateEngine
import com.example.ui.TrafficUiState
import com.example.ui.TrafficViewModel
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanBright
import com.example.ui.theme.CyanParking
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseDanger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ReportScreen(
    uiState: TrafficUiState,
    events: List<TrafficEventEntity>,
    silhouettesMemory: List<SilhouetteMemoryEntity>,
    parkingSessions: List<ParkingSessionEntity>,
    savedShifts: List<SavedShiftEntity>,
    onUpdateNotes: (String) -> Unit,
    onEnterCleanScreenshot: () -> Unit,
    onSharePngCard: (android.content.Context) -> Unit,
    onCopyReportText: (android.content.Context) -> Unit,
    onShareCsv: (android.content.Context) -> Unit,
    onSaveShiftToArchive: () -> Unit,
    onDeleteSavedShift: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalPedestrians = TrafficViewModel.computeTotalPedestrians(events, uiState.streamCategories)
    val rateHour = TrafficViewModel.computeHourlyRate(totalPedestrians, uiState.elapsedSeconds)
    val effMinutes = max(uiState.elapsedSeconds, 60L) / 60.0
    val rateMinute = if (totalPedestrians > 0) {
        String.format(Locale.US, "%.1f", totalPedestrians / effMinutes)
    } else "0.0"

    val exitedCars = parkingSessions.filter { it.isCompleted }
    val avgStayMinutes = if (exitedCars.isNotEmpty()) {
        exitedCars.mapNotNull { it.durationMinutes }.average().roundToInt()
    } else 0

    val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
    val startStr = ParkingPlateEngine.formatTimestampHHMM(uiState.sessionStartTs)
    val nowStr = ParkingPlateEngine.formatTimestampHHMM(System.currentTimeMillis())

    // Build 5-minute traffic buckets for chart
    val buckets = linkedMapOf<String, Int>()
    if (events.isEmpty()) {
        buckets[nowStr] = 0
    } else {
        val cal = java.util.Calendar.getInstance()
        events.forEach { ev ->
            cal.timeInMillis = ev.timestamp
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val m5 = (cal.get(java.util.Calendar.MINUTE) / 5) * 5
            val key = String.format(Locale.getDefault(), "%02d:%02d", h, m5)
            buckets[key] = max(0, (buckets[key] ?: 0) + ev.delta)
        }
    }
    val peakEntry = buckets.entries.maxByOrNull { it.value }
    val peakText = if (peakEntry != null && peakEntry.value > 0) {
        "${peakEntry.key} (${peakEntry.value} чел.)"
    } else "—"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Export & Screenshot Actions Toolbar (hidden in clean screenshot mode)
        if (!uiState.cleanScreenshotMode) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Готовый отчёт для заказчика / рабочего чата:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onEnterCleanScreenshot,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanParking,
                                contentColor = Color(0xFF042F2E)
                            ),
                            modifier = Modifier.testTag("btn_clean_screenshot_mode")
                        ) {
                            Icon(AppIcons.ScreenshotMonitor, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Чистый экран под скрин", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSharePngCard(context) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color(0xFF022C22)
                            ),
                            modifier = Modifier.testTag("btn_share_png_report")
                        ) {
                            Icon(AppIcons.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Карточка PNG", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onCopyReportText(context) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_copy_report_text")
                        ) {
                            Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Скопировать текст")
                        }

                        OutlinedButton(
                            onClick = { onShareCsv(context) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_export_csv")
                        ) {
                            Icon(AppIcons.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CSV (Excel)")
                        }
                    }
                }
            }
        }

        // MAIN REPORT SUMMARY CARD (Designed for Screenshot)
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(2.dp, EmeraldPrimary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.16f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "СВОДКА ЗАМЕРА ПРОХОДИМОСТИ И ПАРКОВКИ",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldBright,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.locationName.ifBlank { "Точка замера" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Дата: $dateStr • Замер: $startStr – $nowStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Длительность",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = TrafficViewModel.formatDurationSeconds(uiState.elapsedSeconds),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // 4 Primary KPIs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryStatCard(
                        title = "ВСЕГО ЛЮДЕЙ",
                        value = "$totalPedestrians",
                        subtitle = "за смену",
                        valueColor = EmeraldBright,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryStatCard(
                        title = "В ЧАС",
                        value = "$rateHour",
                        subtitle = "чел / час",
                        valueColor = CyanBright,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryStatCard(
                        title = "В МИНУТУ",
                        value = rateMinute,
                        subtitle = "чел / мин",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryStatCard(
                        title = "АВТО ПАРК.",
                        value = "${parkingSessions.size}",
                        subtitle = if (avgStayMinutes > 0) "ср. ${avgStayMinutes}м" else "выезд: ${exitedCars.size}",
                        valueColor = AmberAlert,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Streams & Silhouette Re-ID Breakdown
                Text(
                    text = "ПЕШЕХОДНЫЕ ПОТОКИ И ИИ-ДЕТЕКТОР (ПАМЯТЬ ${uiState.silhouetteMemoryMinutes} МИН)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val streamsSum = uiState.streamCategories.sumOf { max(0, it.count) }
                if (streamsSum > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.streamCategories.forEach { cat ->
                            val pct = ((cat.count.toFloat() / streamsSum) * 100f).roundToInt()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${cat.count} чел.",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = JetBrainsMonoFamily
                                        )
                                        Text(
                                            text = "$pct%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = EmeraldBright
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Уникальный пешеходный поток (Силуэты + Кликер)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "$totalPedestrians чел. (отсечено повторов: ${uiState.camSkippedRepeats})",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = JetBrainsMonoFamily,
                                color = EmeraldBright
                            )
                        }
                    }
                }

                // Parking Summary Section in Report Card
                if (parkingSessions.isNotEmpty()) {
                    Text(
                        text = "АНАЛИЗ ВРЕМЕНИ СТОЯНКИ НА ПАРКОВКЕ (ПАМЯТЬ ${uiState.parkingMemoryHours} Ч)",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanBright
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        parkingSessions.take(6).forEach { p ->
                            val inStr = ParkingPlateEngine.formatTimestampHHMM(p.entryTimeMillis)
                            val statusText = if (p.isCompleted && p.exitTimeMillis != null) {
                                val outStr = ParkingPlateEngine.formatTimestampHHMM(p.exitTimeMillis)
                                val dur = ParkingPlateEngine.formatDurationMinutes(p.durationMinutes ?: 1)
                                "заезд $inStr → выезд $outStr ($dur)"
                            } else {
                                "заезд $inStr • сейчас на парковке"
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${p.plateNumber} (${p.vehicleTag})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                        color = if (p.isCompleted) AmberAlert else CyanBright
                                    )
                                }
                            }
                        }
                    }
                }

                // 5-Minute Traffic Dynamics Bar Chart
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ДИНАМИКА ТРАФИКА ПО ВРЕМЕНИ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Пик: $peakText",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldBright
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TrafficBarChartCanvas(
                            buckets = buckets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                }

                // Notes input for Screenshot
                OutlinedTextField(
                    value = uiState.reportNotes,
                    onValueChange = onUpdateNotes,
                    placeholder = { Text("Заметка для скриншота (погода, обеденный замер 1.5 ч, акция)...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_notes_input")
                )
            }
        }

        // Saved Shifts Archive (hidden in clean screenshot mode)
        if (!uiState.cleanScreenshotMode) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Архив сохранённых замеров",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Хранится автономно в памяти телефона",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onSaveShiftToArchive,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color(0xFF022C22)
                            ),
                            modifier = Modifier.testTag("btn_save_shift_archive")
                        ) {
                            Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Сохранить замер", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (savedShifts.isEmpty()) {
                        Text(
                            text = "В архиве пока нет записей — нажмите «Сохранить замер», чтобы зафиксировать итоги 1.5-часовой смены",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        savedShifts.forEach { shift ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = shift.locationName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${shift.totalPedestrians} чел.",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontFamily = JetBrainsMonoFamily,
                                                color = EmeraldBright
                                            )
                                            if (shift.parkingTotalVehicles > 0) {
                                                Text(
                                                    text = "• ${shift.parkingTotalVehicles} авто",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    color = CyanBright
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${shift.dateStr} • Длит: ${shift.durationFormatted} • Темп: ${shift.hourlyRate} чел/ч",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (shift.notes.isNotBlank()) {
                                            Text(
                                                text = "Заметка: ${shift.notes}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                                color = AmberAlert
                                            )
                                        }
                                    }

                                    IconButton(onClick = { onDeleteSavedShift(shift.id) }) {
                                        Icon(
                                            imageVector = AppIcons.DeleteOutline,
                                            contentDescription = "Удалить из архива",
                                            tint = RoseDanger
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficBarChartCanvas(
    buckets: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val entries = buckets.entries.toList().takeLast(12)
    val maxVal = max(1, entries.maxOfOrNull { it.value } ?: 1)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val count = max(1, entries.size)
        val slotW = w / count
        val barW = (slotW * 0.62f).coerceAtMost(64f)

        entries.forEachIndexed { index, entry ->
            val ratio = entry.value.toFloat() / maxVal.toFloat()
            val barH = max(8f, ratio * (h - 24f))
            val left = index * slotW + (slotW - barW) / 2f
            val top = h - barH

            drawRoundRect(
                color = Color(0xFF10B981),
                topLeft = Offset(left, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(10f, 10f)
            )
        }
    }
}
