package com.example.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Engine for normalizing vehicle license plates, running the live/simulated ALPR parking gate scanner,
 * and formatting entry/exit durations over the 12-hour (2..24h configurable) memory window.
 */
object ParkingPlateEngine {

    private val latinToCyrillicMap = mapOf(
        'A' to 'А',
        'B' to 'В',
        'E' to 'Е',
        'K' to 'К',
        'M' to 'М',
        'H' to 'Н',
        'O' to 'О',
        'P' to 'Р',
        'C' to 'С',
        'T' to 'Т',
        'Y' to 'У',
        'X' to 'Х'
    )

    val ALLOWED_GOST_LETTERS = listOf("А", "В", "Е", "К", "М", "Н", "О", "Р", "С", "Т", "У", "Х")
    val VEHICLE_TAGS = listOf("Легковой", "Кроссовер / SUV", "Такси", "Доставка / Коммерч.")

    // Realistic demo pool for testing automatic Entry -> Exit detection
    val DEMO_PLATES_POOL = listOf(
        "А 452 МР 77" to "Кроссовер / SUV",
        "М 810 КХ 199" to "Легковой",
        "Е 204 ОО 97" to "Легковой",
        "К 739 СТ 799" to "Такси",
        "О 118 АА 78" to "Кроссовер / SUV",
        "В 593 НМ 197" to "Доставка / Коммерч.",
        "С 902 ТК 777" to "Легковой",
        "Х 341 УЕ 50" to "Кроссовер / SUV"
    )

    /**
     * Normalizes raw user or OCR input into a clean canonical plate string (e.g., "А123ВС77" -> "А 123 ВС 77")
     */
    fun normalizePlate(raw: String): String {
        val cleaned = buildString {
            for (ch in raw.uppercase(Locale.getDefault())) {
                if (ch.isLetterOrDigit()) {
                    append(latinToCyrillicMap[ch] ?: ch)
                }
            }
        }
        if (cleaned.isEmpty()) return ""

        // Match standard RF plate pattern: L DDD LL RR(R)
        val regex = Regex("^([АВЕКМНОРСТУХA-Z])(\\d{3})([АВЕКМНОРСТУХA-Z]{2})(\\d{2,3})$")
        val match = regex.matchEntire(cleaned)
        if (match != null) {
            val (l1, digits, l23, reg) = match.destructured
            return "$l1 $digits $l23 $reg"
        }

        // Fallback for taxi/commercial/foreign plates
        return cleaned.chunked(3).joinToString(" ").trim()
    }

    fun formatDurationMinutes(minutes: Int): String {
        val m = max(1, minutes)
        val hrs = m / 60
        val remMin = m % 60
        return when {
            hrs > 0 && remMin > 0 -> "${hrs} ч ${remMin} мин"
            hrs > 0 -> "${hrs} ч ровно"
            else -> "${remMin} мин"
        }
    }

    fun formatTimestampHHMM(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    fun formatTimestampHHMMSS(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    /**
     * Evaluates a downsampled camera luma grid (160x90) inside the central plate scan box
     * to detect high-contrast rectangular license plate edges and trigger candidate lock.
     */
    fun detectPlateCandidateContrast(lumaGrid: IntArray, gridW: Int, gridH: Int): Float {
        if (lumaGrid.size < gridW * gridH) return 0f
        val xStart = (gridW * 0.25f).toInt()
        val xEnd = (gridW * 0.75f).toInt()
        val yStart = (gridH * 0.35f).toInt()
        val yEnd = (gridH * 0.65f).toInt()

        var edgeEnergy = 0
        var count = 0
        for (y in yStart until yEnd) {
            for (x in xStart until xEnd - 1) {
                val idx = y * gridW + x
                val diff = kotlin.math.abs(lumaGrid[idx + 1] - lumaGrid[idx])
                if (diff > 28) edgeEnergy++
                count++
            }
        }
        if (count == 0) return 0f
        return (edgeEnergy.toFloat() / count.toFloat() * 3.2f).coerceIn(0f, 1f)
    }
}
