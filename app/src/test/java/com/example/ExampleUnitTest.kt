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

    @Test
    fun silhouetteRadar_countsDistantAndStairPedestrianAsStrictlyOneEach() {
        val engine = SilhouetteRadarEngine()
        val gw = SilhouetteRadarEngine.GRID_W
        val gh = SilhouetteRadarEngine.GRID_H
        val memory = mutableListOf<SilhouetteMemoryEntity>()
        var nextId = 1
        var totalCounted = 0

        // Frame 0: clean background
        val bgR = IntArray(gw * gh) { 40 }
        val bgG = IntArray(gw * gh) { 42 }
        val bgB = IntArray(gw * gh) { 48 }
        engine.analyzeCameraRgbGrid(
            rGrid = bgR,
            gGrid = bgG,
            bGrid = bgB,
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

        // Frames 1..6:
        // (A) Distant person at y=22..26 (5 rows tall, 2 cols wide) walking slowly across the horizon
        // (B) Man walking up stairs at x=90..102, y=30..68 split by 2 stair railing gaps at y=40..41 and y=53..54
        for (frame in 1..6) {
            val rGrid = IntArray(gw * gh) { 40 }
            val gGrid = IntArray(gw * gh) { 42 }
            val bGrid = IntArray(gw * gh) { 48 }

            // (A) Distant walker
            val farX = 30 + frame
            for (y in 22..26) {
                for (x in farX..(farX + 1)) {
                    val idx = y * gw + x
                    rGrid[idx] = 165
                    gGrid[idx] = 175
                    bGrid[idx] = 185
                }
            }

            // (B) Approaching man on stairs split by railings into 3 vertical segments
            val nearX = 90 + frame
            for (y in 30..68) {
                if (y in 40..41 || y in 53..54) continue // Stair railing occlusion bars
                val halfW = if (y < 38) 3 else 5
                for (x in (nearX - halfW)..(nearX + halfW)) {
                    val idx = y * gw + x
                    rGrid[idx] = 150
                    gGrid[idx] = 160
                    bGrid[idx] = 175
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
            memory.addAll(res.newlyCounted)
            totalCounted += res.newlyCounted.size
        }

        // Exactly 2 people (1 in the distance + 1 on the stairs, never +3 for the stair fragments!)
        assertEquals(2, totalCounted)
    }
}
