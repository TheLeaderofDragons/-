package com.example

import com.example.data.SilhouetteMemoryEntity
import com.example.domain.ParkingPlateEngine
import com.example.domain.SilhouetteRadarEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parkingPlateNormalization_formatsStandardPlateAndLatinLookalikes() {
        val normalized = ParkingPlateEngine.normalizePlate("a452mp77")
        assertEquals("А 452 МР 77", normalized)

        val formatted3DigitRegion = ParkingPlateEngine.normalizePlate("М810КХ199")
        assertEquals("М 810 КХ 199", formatted3DigitRegion)
    }

    @Test
    fun parkingDurationFormatting_formatsMinutesAndHoursCleanly() {
        assertEquals("45 мин", ParkingPlateEngine.formatDurationMinutes(45))
        assertEquals("1 ч 30 мин", ParkingPlateEngine.formatDurationMinutes(90))
        assertEquals("2 ч ровно", ParkingPlateEngine.formatDurationMinutes(120))
    }

    @Test
    fun silhouetteRadar_catchesFarHorizonSilhouettesAndDeduplicates90MinMemory() {
        val engine = SilhouetteRadarEngine()
        val memory = mutableListOf<SilhouetteMemoryEntity>()
        var nextId = 1

        // Step 1: initial frame tracks distant silhouettes
        val res1 = engine.stepSimulation(
            sensitivity = 85,
            farHorizonBoost = true,
            stepFilterEnabled = true,
            stationaryLineMode = false,
            lineVertical = true,
            linePos = 0.5f,
            memoryMinutes = 90,
            currentMemory = memory,
            nextPersonIdProvider = { nextId++ }
        )
        assertTrue(res1.boxes.isNotEmpty())

        // Step 2: second confirmation frame registers unique silhouettes into the 90-minute memory bank
        val res2 = engine.stepSimulation(
            sensitivity = 85,
            farHorizonBoost = true,
            stepFilterEnabled = true,
            stationaryLineMode = false,
            lineVertical = true,
            linePos = 0.5f,
            memoryMinutes = 90,
            currentMemory = memory,
            nextPersonIdProvider = { nextId++ }
        )
        assertTrue(res2.newlyCounted.isNotEmpty())
        assertTrue(res2.newlyCounted.any { it.distanceZone.contains("Вдали") })
    }
}
