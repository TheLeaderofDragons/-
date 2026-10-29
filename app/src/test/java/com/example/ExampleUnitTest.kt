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

    @Test
    fun silhouetteRadar_ignoresShadowsWindowFramesTvLegsAndDeduplicatesSingleRoomWalker() {
        val engine = SilhouetteRadarEngine()
        val gw = SilhouetteRadarEngine.GRID_W
        val gh = SilhouetteRadarEngine.GRID_H
        val memory = mutableListOf<SilhouetteMemoryEntity>()
        var nextId = 1
        var totalCounted = 0

        fun buildLivingRoomBackground(): Triple<IntArray, IntArray, IntArray> {
            val r = IntArray(gw * gh) { 195 }
            val g = IntArray(gw * gh) { 192 }
            val b = IntArray(gw * gh) { 188 }
            // Window frame on left (x=18..34, y=14..52) - hollow frame
            for (y in 14..52) {
                for (x in 18..34) {
                    if (y in 14..16 || y in 50..52 || x in 18..20 || x in 32..34) {
                        val idx = y * gw + x
                        r[idx] = 80
                        g[idx] = 80
                        b[idx] = 85
                    }
                }
            }
            // TV stand legs on right (x=115..131, y=54..70) - two outer legs with empty middle
            for (y in 54..70) {
                for (x in listOf(115, 116, 130, 131)) {
                    val idx = y * gw + x
                    r[idx] = 35
                    g[idx] = 35
                    b[idx] = 40
                }
            }
            return Triple(r, g, b)
        }

        // Frame 0: initialize living room background
        val (bgR, bgG, bgB) = buildLivingRoomBackground()
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

        // Frames 1..14:
        // - Cast shadow dims wall area (x=40..54, y=20..65) by 20% while keeping chromaticity
        // - 1 person in dark shirt/pants walks right (frames 1..7) and then walks back left with wide stride (frames 8..14)
        for (frame in 1..14) {
            val (r, g, b) = buildLivingRoomBackground()

            // Moving cast shadow on wall/curtain (20% darkening, same color ratio)
            val shadowX = 36 + frame
            for (y in 20..64) {
                for (x in shadowX..(shadowX + 10)) {
                    val idx = y * gw + x
                    r[idx] = (r[idx] * 0.80f).toInt()
                    g[idx] = (g[idx] * 0.80f).toInt()
                    b[idx] = (b[idx] * 0.80f).toInt()
                }
            }

            // Single person walking right then left across the living room
            val personX = if (frame <= 7) 58 + frame * 3 else 79 - (frame - 7) * 3
            val strideExtra = if (frame % 2 == 0) 3 else 0 // Wide stride vs narrow stance
            for (y in 22..68) {
                val halfW = when {
                    y < 30 -> 3 // Head (narrower than shoulders)
                    y < 50 -> 5 // Torso/shoulders
                    else -> 5 + strideExtra // Legs in wide stride
                }
                for (x in (personX - halfW)..(personX + halfW)) {
                    val idx = y * gw + x
                    r[idx] = 28
                    g[idx] = 32
                    b[idx] = 38
                }
            }

            val res = engine.analyzeCameraRgbGrid(
                rGrid = r,
                gGrid = g,
                bGrid = b,
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

        // Only the 1 real person should be counted once (+1), never the shadow, window frame, or TV legs, and never +6 on turnaround
        assertEquals(1, totalCounted)
    }
}

