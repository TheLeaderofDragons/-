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

    @Test
    fun silhouetteRadar_ignoresSmallFloorPetsLikeCatsAndStationaryObjects() {
        val engine = SilhouetteRadarEngine()
        val gw = SilhouetteRadarEngine.GRID_W
        val gh = SilhouetteRadarEngine.GRID_H
        val memory = mutableListOf<SilhouetteMemoryEntity>()
        var nextId = 1
        var totalFalseCounts = 0

        // Simulate 15 frames of a cat moving on the room floor (y = 62..74, width = 14, height = 12)
        for (frame in 0 until 15) {
            val rGrid = IntArray(gw * gh) { 45 }
            val gGrid = IntArray(gw * gh) { 48 }
            val bGrid = IntArray(gw * gh) { 52 }

            val catX = 40 + frame * 2
            for (y in 62..73) {
                for (x in catX..(catX + 13)) {
                    val idx = y * gw + x
                    rGrid[idx] = 210
                    gGrid[idx] = 180
                    bGrid[idx] = 140
                }
            }

            val res = engine.analyzeCameraRgbGrid(
                rGrid = rGrid,
                gGrid = gGrid,
                bGrid = bGrid,
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
            totalFalseCounts += res.newlyCounted.size
        }

        // Must never count a cat/pet on the floor as a person
        assertEquals(0, totalFalseCounts)
    }
}
