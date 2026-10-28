package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PredefinedStreamPresets
import com.example.data.StreamCategory
import com.example.ui.TrafficUiState
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CyanParking
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.VioletAccent
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun StreamsScreen(
    uiState: TrafficUiState,
    onSelectPreset: (String) -> Unit,
    onStreamClick: (String, Int) -> Unit,
    onOpenAddCategory: () -> Unit,
    onDismissAddCategory: () -> Unit,
    onConfirmAddCategory: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = uiState.streamCategories
    val totalStreams = categories.sumOf { max(0, it.count) }
    val activePreset = PredefinedStreamPresets.getByKey(uiState.activePresetKey)

    val (mainMetricText, subtitleText) = when (activePreset.metricType) {
        "conversion" -> {
            val targetCat = categories.find { it.role == "target" } ?: categories.lastOrNull()
            val entered = targetCat?.count ?: 0
            val pct = if (totalStreams > 0) {
                String.format(Locale.US, "%.1f%%", (entered.toFloat() / totalStreams) * 100f)
            } else "0.0%"
            pct to "Целевых («${targetCat?.name ?: "Зашли"}»): $entered из $totalStreams чел."
        }
        "inside" -> {
            val inCount = categories.find { it.role == "in" }?.count ?: 0
            val outCount = categories.find { it.role == "out" }?.count ?: 0
            val insideNow = max(0, inCount - outCount)
            "$insideNow чел." to "Сейчас находятся внутри объекта (Вошли: $inCount • Вышли: $outCount)"
        }
        else -> {
            val topCat = categories.maxByOrNull { it.count }
            val topShare = if (totalStreams > 0 && topCat != null) {
                ((topCat.count.toFloat() / totalStreams) * 100f).roundToInt()
            } else 0
            "$topShare%" to if (topCat != null && topCat.count > 0) {
                "Лидирует направление: ${topCat.name}"
            } else {
                "Нажимайте на карточки направлений ниже для замера долей"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Preset Selector Bar + Add Custom Category button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PredefinedStreamPresets.ALL.forEach { preset ->
                val selected = preset.key == uiState.activePresetKey
                FilterChip(
                    selected = selected,
                    onClick = { onSelectPreset(preset.key) },
                    label = {
                        Text(
                            text = preset.shortTitle,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary.copy(alpha = 0.22f),
                        selectedLabelColor = EmeraldBright
                    )
                )
            }

            Button(
                onClick = onOpenAddCategory,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanParking,
                    contentColor = Color(0xFF042F2E)
                ),
                modifier = Modifier.testTag("btn_add_custom_stream")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Своя кнопка", fontWeight = FontWeight.Bold)
            }
        }

        // Conversion & Insight Banner
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activePreset.bannerTitle.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "МЕТРИКА",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = mainMetricText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = EmeraldBright,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "СУММА",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$totalStreams",
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Stream Category Cards
        categories.forEach { cat ->
            val accent = resolveCategoryColor(cat.colorKey)
            val sharePct = if (totalStreams > 0) {
                ((cat.count.toFloat() / totalStreams) * 100f).roundToInt()
            } else 0

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.45f))
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
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = accent.copy(alpha = 0.16f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "$sharePct%",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = JetBrainsMonoFamily,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { onStreamClick(cat.id, 1) },
                            modifier = Modifier
                                .weight(1f)
                                .height(68.dp)
                                .testTag("stream_plus_${cat.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = accent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${cat.count}",
                                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp),
                                    fontFamily = JetBrainsMonoFamily,
                                    color = Color(0xFF04131A),
                                    fontWeight = FontWeight.Black
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.20f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "+1",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = { onStreamClick(cat.id, -1) },
                            modifier = Modifier
                                .width(60.dp)
                                .height(68.dp)
                                .testTag("stream_minus_${cat.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.Remove,
                                    contentDescription = "Убавить 1",
                                    tint = RoseDanger
                                )
                            }
                        }
                    }
                }
            }
        }

        // Visual Distribution Bar
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Доли потоков",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (totalStreams > 0) "100% ($totalStreams чел.)" else "Нет данных",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (totalStreams > 0) {
                        categories.filter { it.count > 0 }.forEach { cat ->
                            val weight = (cat.count.toFloat() / totalStreams).coerceAtLeast(0.02f)
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                                    .background(resolveCategoryColor(cat.colorKey))
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.addCategoryDialogOpen) {
        AddStreamCategoryDialog(
            onDismiss = onDismissAddCategory,
            onConfirm = onConfirmAddCategory
        )
    }
}

@Composable
private fun AddStreamCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("emerald") }

    val colorChoices = listOf(
        "emerald" to "Изумрудный",
        "sky" to "Циан",
        "amber" to "Янтарный",
        "violet" to "Фиолетовый",
        "rose" to "Коралловый"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Новая кнопка потока", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Например: «Посмотрели на баннер», «С коляской», «Курьеры»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название направления") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Цвет карточки:",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorChoices.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedColor == key,
                            onClick = { selectedColor = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, selectedColor) }) {
                Text("Добавить", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

fun resolveCategoryColor(colorKey: String): Color = when (colorKey) {
    "emerald" -> EmeraldPrimary
    "sky" -> CyanParking
    "amber" -> AmberAlert
    "rose" -> RoseDanger
    "violet" -> VioletAccent
    else -> Color(0xFF64748B)
}
