package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrafficEventEntity
import com.example.domain.ParkingPlateEngine
import com.example.ui.TrafficUiState
import com.example.ui.TrafficViewModel
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanParking
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseDanger

@Composable
fun CounterScreen(
    uiState: TrafficUiState,
    events: List<TrafficEventEntity>,
    onManualClick: (Int, String) -> Unit,
    onUndoLast: () -> Unit,
    onOpenBlindMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = TrafficViewModel.computeTotalPedestrians(events, uiState.streamCategories)
    val rateHour = TrafficViewModel.computeHourlyRate(total, uiState.elapsedSeconds)
    val last15Min = TrafficViewModel.computeLast15MinCount(events)
    val avgGap = TrafficViewModel.computeAvgGapFormatted(events)

    // 1.5-hour (90 minutes = 5400 seconds) shift progress indicator
    val targetSeconds = 90 * 60f
    val shiftProgress = (uiState.elapsedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
    val elapsedMinutes = (uiState.elapsedSeconds / 60L).toInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1.5-Hour Shift Progress Pill
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Прогресс 1.5-часового замера (90 мин)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$elapsedMinutes из 90 мин (${(shiftProgress * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = EmeraldBright
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { shiftProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = EmeraldPrimary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }

        // Top 3 Telemetry Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryStatCard(
                title = "ТЕМП В ЧАС",
                value = "$rateHour",
                subtitle = "чел / час",
                valueColor = EmeraldBright,
                modifier = Modifier.weight(1f)
            )
            TelemetryStatCard(
                title = "ЗА 15 МИНУТ",
                value = "$last15Min",
                subtitle = "последние 15м",
                valueColor = CyanParking,
                modifier = Modifier.weight(1f)
            )
            TelemetryStatCard(
                title = "ИНТЕРВАЛ",
                value = avgGap,
                subtitle = "между людьми",
                valueColor = AmberAlert,
                modifier = Modifier.weight(1f)
            )
        }

        // Giant Ergonomic Main +1 Pad
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "main_pad_scale"
        )

        val lastEvent = events.lastOrNull()
        val lastClickSubtitle = if (lastEvent != null) {
            val sign = if (lastEvent.delta > 0) "+${lastEvent.delta}" else "${lastEvent.delta}"
            "Последний: ${ParkingPlateEngine.formatTimestampHHMMSS(lastEvent.timestamp)} (${lastEvent.label} $sign)"
        } else {
            "Нажмите в любую точку зелёной панели (+1)"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .weight(1f, fill = false)
                .scale(scale)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF059669),
                            Color(0xFF047857),
                            Color(0xFF065F46)
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onManualClick(1, "Клик +1") }
                )
                .padding(vertical = 28.dp, horizontal = 20.dp)
                .testTag("main_plus_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.22f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFFA7F3D0),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "НАЖМИТЕ ДЛЯ ПОДСЧЁТА (+1)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFD1FAE5),
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "$total",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("main_counter_value")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = Color.Black.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = lastClickSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFECFDF5),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Quick Action Row (-1, +2 Pair, +5 Group, Blind Mode)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickCounterButton(
                title = "−1",
                subtitle = "Ошибка",
                accentColor = RoseDanger,
                containerColor = RoseDanger.copy(alpha = 0.14f),
                onClick = { onManualClick(-1, "Коррекция -1") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_minus_one")
            )
            QuickCounterButton(
                title = "+2",
                subtitle = "Пара",
                accentColor = MaterialTheme.colorScheme.onSurface,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onManualClick(2, "Пара +2") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_plus_two")
            )
            QuickCounterButton(
                title = "+5",
                subtitle = "Группа",
                accentColor = MaterialTheme.colorScheme.onSurface,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onManualClick(5, "Группа +5") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_plus_five")
            )
            QuickCounterButton(
                title = "🕶️",
                subtitle = "Вслепую",
                accentColor = CyanParking,
                containerColor = CyanParking.copy(alpha = 0.14f),
                onClick = onOpenBlindMode,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_blind_mode")
            )
        }

        // Recent Events & Undo Bar
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Последние действия",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val tailText = if (events.isEmpty()) {
                        "пока нет записей"
                    } else {
                        events.takeLast(3).reversed().joinToString(" • ") { ev ->
                            val sign = if (ev.delta > 0) "+${ev.delta}" else "${ev.delta}"
                            "${ParkingPlateEngine.formatTimestampHHMMSS(ev.timestamp)} ($sign)"
                        }
                    }
                    Text(
                        text = tailText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedButton(
                    onClick = onUndoLast,
                    enabled = events.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("btn_undo_last")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Отменить последнее действие",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Отменить", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TelemetryStatCard(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = JetBrainsMonoFamily,
                color = valueColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickCounterButton(
    title: String,
    subtitle: String,
    accentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BlindModeOverlay(
    totalCount: Int,
    onIncrement: (Int, String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF04070D)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "РЕЖИМ «ВСЛЕПУЮ» (OLED ЭКОНОМИЯ)",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldBright
                    )
                    Text(
                        text = "Нажимайте в любое место экрана, не глядя на смартфон",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8)
                    )
                }
                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("btn_close_blind_mode")
                ) {
                    Text("Выйти", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                onClick = { onIncrement(1, "Вслепую +1") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp)
                    .testTag("blind_giant_pad"),
                shape = RoundedCornerShape(32.dp),
                color = EmeraldPrimary.copy(alpha = 0.10f),
                border = BorderStroke(2.dp, EmeraldPrimary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = EmeraldBright.copy(alpha = 0.7f),
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ВСЕГО ПРОШЛО",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "$totalCount",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
                        color = EmeraldBright,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = EmeraldPrimary.copy(alpha = 0.18f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Касание в любой точке = +1 человек",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFD1FAE5),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onIncrement(-1, "Вслепую -1") },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseDanger.copy(alpha = 0.25f),
                        contentColor = Color(0xFFFECDD3)
                    )
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("−1 (Ошибка)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Button(
                    onClick = { onIncrement(5, "Вслепую +5") },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanParking.copy(alpha = 0.25f),
                        contentColor = Color(0xFFCFFAFE)
                    )
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+5 (Группа)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
