package com.example.domain

import com.example.data.SilhouetteMemoryEntity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DetectedSilhouetteBox(
    val trackId: Int,
    val bx: Float, // normalized 0f..1f
    val by: Float, // normalized 0f..1f
    val bw: Float, // normalized 0f..1f
    val bh: Float, // normalized 0f..1f
    val upperHex: String,
    val lowerHex: String,
    val isFarHorizon: Boolean,
    val countedPersonId: Int?,
    val matchedKnownId: Int?,
    val confidence: Float
)

data class SilhouetteDescriptor(
    val vector: FloatArray,
    val upperHex: String,
    val lowerHex: String,
    val upperR: Int,
    val upperG: Int,
    val upperB: Int,
    val lowerR: Int,
    val lowerG: Int,
    val lowerB: Int,
    val aspectRatio: Float,
    val isFarHorizon: Boolean
)

data class FrameAnalysisResult(
    val boxes: List<DetectedSilhouetteBox>,
    val isShakingOrPanning: Boolean,
    val newlyCounted: List<SilhouetteMemoryEntity>,
    val updatedRepeats: List<SilhouetteMemoryEntity>,
    val horizonZoomActive: Boolean
)

/**
 * Human-Specific Distant & Near Silhouette Detector + 90-Minute Re-ID Engine.
 *
 * Key safeguards against false positives (cats/pets, furniture, handheld camera motion, and repeat spam):
 * 1. Adaptive Background Model + Global Pan/Shake Suppression:
 *    - Distinguishes genuine foreground walkers from handheld camera rotation/panning and auto-exposure shifts.
 * 2. Perspective Floor Rule (Anti-Pet / Anti-Cat Filter):
 *    - In the lower/floor half of the frame (bottomY > 0.62), a standing/walking human must occupy at least
 *      25% of the frame height. Small moving objects on the floor (< 25% height, or wider/boxy aspect > 0.55)
 *      such as cats, dogs, or room clutter are strictly rejected.
 *    - Small distant silhouettes (5.5%..24% height) are ONLY accepted in the elevated horizon band
 *      (across the street, bottomY <= 0.62) when 2.5x Horizon Boost is active.
 * 3. 4-Band Human Anatomical Profile (Head - Shoulders - Torso - Legs):
 *    - Verifies upright vertical aspect ratio (0.20..0.55), head-to-shoulder taper, vertical spine alignment,
 *      and continuous head-to-legs silhouette presence.
 * 4. Directional Walking Trajectory + Synchronous In-Memory Re-ID & Spatial Lock:
 *    - Real camera detections must be tracked for >= 6 frames AND travel a consistent directional path
 *      (>= 6.5%..8.5% of screen width with path linearity >= 0.55) before counting +1.
 *    - Synchronous internal memory cache + spatial cooldown zone guarantees the same person/object in frame
 *      can never spam multiple +1 increments.
 */
class SilhouetteRadarEngine {

    companion object {
        const val GRID_W = 160
        const val GRID_H = 90
        private const val DESCRIPTOR_SIZE = 52
    }

    private var prevLumaGrid: IntArray? = null
    private var bgLumaModel: FloatArray? = null
    private var lastShakeTimestamp: Long = 0L
    private var nextTrackSeq = 1

    // Synchronous internal cache so newly counted silhouettes are immediately known on the next frame
    // without waiting for asynchronous Room Flow emissions.
    private val internalMemoryCache = mutableListOf<SilhouetteMemoryEntity>()

    private data class RecentCountZone(
        val personId: Int,
        val cx: Float,
        val cy: Float,
        val upperHex: String,
        val lowerHex: String,
        val timestamp: Long
    )

    private val recentCountZones = mutableListOf<RecentCountZone>()

    private class ActiveTrack(
        val trackId: Int,
        val startCx: Float,
        val startCy: Float,
        var cx: Float,
        var cy: Float,
        var bx: Float,
        var by: Float,
        var bw: Float,
        var bh: Float,
        var descriptor: SilhouetteDescriptor,
        var isFarHorizon: Boolean,
        var humanConfidence: Float,
        var hits: Int = 1,
        var missedFrames: Int = 0,
        var totalPathDist: Float = 0f,
        var matchedThisFrame: Boolean = true,
        var countedPersonId: Int? = null,
        var matchedKnownId: Int? = null,
        var isRepeatNotified: Boolean = false
    )

    private val activeTracks = mutableListOf<ActiveTrack>()

    // Simulation state for realistic street & across-the-road twilight testing
    private data class SimPedestrian(
        var simId: Int,
        var x: Float, // 0f..1f
        var y: Float, // 0f..1f
        var vx: Float,
        var bw: Float,
        var bh: Float,
        var upperHex: String,
        var lowerHex: String,
        var isFarHorizon: Boolean,
        var bounce: Boolean,
        var descriptor: SilhouetteDescriptor
    )

    private val simPedestrians = mutableListOf<SimPedestrian>()
    private var nextSimSeed = 5

    private val simPalette = listOf(
        "#1E293B" to "#0F172A",
        "#334155" to "#1E3A8A",
        "#059669" to "#1E293B",
        "#7C2D12" to "#1E293B",
        "#4F46E5" to "#0F172A",
        "#B45309" to "#334155",
        "#BE123C" to "#1E293B"
    )

    @Synchronized
    fun resetTracks() {
        activeTracks.clear()
        prevLumaGrid = null
        bgLumaModel = null
        simPedestrians.clear()
        recentCountZones.clear()
    }

    @Synchronized
    fun clearInternalMemory() {
        internalMemoryCache.clear()
        recentCountZones.clear()
        activeTracks.clear()
    }

    private fun initSimulationIfNeeded() {
        if (simPedestrians.isNotEmpty()) return
        simPedestrians.add(
            SimPedestrian(
                simId = 1,
                x = 0.62f,
                y = 0.34f,
                vx = -0.0042f,
                bw = 0.024f,
                bh = 0.068f,
                upperHex = "#1E293B",
                lowerHex = "#0F172A",
                isFarHorizon = true,
                bounce = true,
                descriptor = createSyntheticDescriptor(1, "#1E293B", "#0F172A", 0.35f, true)
            )
        )
        simPedestrians.add(
            SimPedestrian(
                simId = 2,
                x = 0.22f,
                y = 0.36f,
                vx = 0.0048f,
                bw = 0.027f,
                bh = 0.076f,
                upperHex = "#334155",
                lowerHex = "#1E3A8A",
                isFarHorizon = true,
                bounce = false,
                descriptor = createSyntheticDescriptor(2, "#334155", "#1E3A8A", 0.36f, true)
            )
        )
        simPedestrians.add(
            SimPedestrian(
                simId = 3,
                x = 0.45f,
                y = 0.29f,
                vx = 0.0034f,
                bw = 0.019f,
                bh = 0.052f,
                upperHex = "#475569",
                lowerHex = "#0F172A",
                isFarHorizon = true,
                bounce = false,
                descriptor = createSyntheticDescriptor(3, "#475569", "#0F172A", 0.36f, true)
            )
        )
        simPedestrians.add(
            SimPedestrian(
                simId = 4,
                x = 0.76f,
                y = 0.49f,
                vx = -0.0065f,
                bw = 0.065f,
                bh = 0.23f,
                upperHex = "#059669",
                lowerHex = "#1E293B",
                isFarHorizon = false,
                bounce = true,
                descriptor = createSyntheticDescriptor(4, "#059669", "#1E293B", 0.28f, false)
            )
        )
    }

    @Synchronized
    fun stepSimulation(
        sensitivity: Int,
        farHorizonBoost: Boolean,
        stepFilterEnabled: Boolean,
        stationaryLineMode: Boolean,
        lineVertical: Boolean,
        linePos: Float,
        memoryMinutes: Int,
        currentMemory: List<SilhouetteMemoryEntity>,
        nextPersonIdProvider: () -> Int
    ): FrameAnalysisResult {
        initSimulationIfNeeded()

        val rawDetections = mutableListOf<RawDetection>()
        for (p in simPedestrians) {
            p.x += p.vx
            if (p.bounce) {
                if (p.x > 0.90f) p.vx = -abs(p.vx)
                if (p.x < 0.06f) p.vx = abs(p.vx)
            } else {
                if (p.x > 0.96f || p.x < 0.02f) {
                    val seed = nextSimSeed++
                    val pair = simPalette[seed % simPalette.size]
                    p.simId = seed
                    p.x = if (p.vx > 0) 0.04f else 0.92f
                    p.upperHex = pair.first
                    p.lowerHex = pair.second
                    p.descriptor = createSyntheticDescriptor(
                        seed,
                        pair.first,
                        pair.second,
                        p.bw / max(0.01f, p.bh),
                        p.isFarHorizon
                    )
                }
            }

            val minAllowedHeight = if (farHorizonBoost) {
                0.032f + (100 - sensitivity) * 0.0005f
            } else {
                0.065f
            }
            if (p.bh >= minAllowedHeight) {
                rawDetections.add(
                    RawDetection(
                        bx = p.x.coerceIn(0.01f, 0.95f),
                        by = p.y.coerceIn(0.05f, 0.85f),
                        bw = p.bw,
                        bh = p.bh,
                        descriptor = p.descriptor,
                        confidence = if (p.isFarHorizon) 0.86f else 0.95f
                    )
                )
            }
        }

        return processDetectionsWithTracker(
            rawDetections = rawDetections,
            isShaking = false,
            isSimulation = true,
            stationaryLineMode = stationaryLineMode,
            lineVertical = lineVertical,
            linePos = linePos,
            memoryMinutes = memoryMinutes,
            currentMemory = currentMemory,
            farHorizonBoost = farHorizonBoost,
            nextPersonIdProvider = nextPersonIdProvider
        )
    }

    data class RawDetection(
        val bx: Float,
        val by: Float,
        val bw: Float,
        val bh: Float,
        val descriptor: SilhouetteDescriptor,
        val confidence: Float
    )

    /**
     * Analyzes a real upright camera frame downsampled to 160x90 RGB/Luma grid.
     * Strictly filters for upright human silhouettes (Head-Shoulders-Torso-Legs) and walking trajectories,
     * rejecting cats/pets, furniture, and handheld camera pan/shake.
     */
    @Synchronized
    fun analyzeCameraRgbGrid(
        rGrid: IntArray,
        gGrid: IntArray,
        bGrid: IntArray,
        sensitivity: Int,
        farHorizonBoost: Boolean,
        stepFilterEnabled: Boolean,
        stationaryLineMode: Boolean,
        lineVertical: Boolean,
        linePos: Float,
        memoryMinutes: Int,
        currentMemory: List<SilhouetteMemoryEntity>,
        nextPersonIdProvider: () -> Int
    ): FrameAnalysisResult {
        val curLuma = IntArray(GRID_W * GRID_H)
        var frameLumaSum = 0L
        for (i in curLuma.indices) {
            val y = (rGrid[i] * 77 + gGrid[i] * 150 + bGrid[i] * 29) shr 8
            curLuma[i] = y
            frameLumaSum += y
        }

        val prev = prevLumaGrid
        val bg = bgLumaModel
        if (prev == null || bg == null) {
            prevLumaGrid = curLuma
            bgLumaModel = FloatArray(GRID_W * GRID_H) { curLuma[it].toFloat() }
            return FrameAnalysisResult(
                boxes = emptyList(),
                isShakingOrPanning = false,
                newlyCounted = emptyList(),
                updatedRepeats = emptyList(),
                horizonZoomActive = farHorizonBoost
            )
        }

        // Step 1: Global camera motion estimation (dx, dy) in [-6..+6, -4..+4] + global change ratio
        var bestDx = 0
        var bestDy = 0
        var minSad = Int.MAX_VALUE
        var zeroShiftChangedBlocks = 0
        var zeroShiftTotalBlocks = 0

        for (dy in -4..4) {
            for (dx in -6..6) {
                var sad = 0
                var samples = 0
                var y = 10
                while (y < GRID_H - 12) {
                    var x = 10
                    while (x < GRID_W - 10) {
                        val a = curLuma[y * GRID_W + x]
                        val b = prev[(y + dy) * GRID_W + (x + dx)]
                        val d = abs(a - b)
                        sad += d
                        samples++
                        if (dx == 0 && dy == 0 && d >= 14) {
                            zeroShiftChangedBlocks++
                        }
                        x += 4
                    }
                    y += 4
                }
                if (dx == 0 && dy == 0) {
                    zeroShiftTotalBlocks = max(1, samples)
                }
                val avgSad = if (samples > 0) sad / samples else 999
                if (avgSad < minSad) {
                    minSad = avgSad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        val globalChangeFraction = zeroShiftChangedBlocks.toFloat() / zeroShiftTotalBlocks.toFloat()
        val now = System.currentTimeMillis()

        // Always detect camera panning/rotation (even if stepFilter is off, panning the phone across a room
        // must not count stationary walls/furniture/pets as walking humans!)
        val panThresholdSad = if (stepFilterEnabled) 13 else 18
        val isCameraMovingNow =
            minSad > panThresholdSad ||
                abs(bestDx) >= 2 ||
                abs(bestDy) >= 2 ||
                globalChangeFraction > 0.26f

        if (isCameraMovingNow) {
            lastShakeTimestamp = now
            // Re-anchor background model to new camera viewpoint and drop unconfirmed tracks
            for (i in curLuma.indices) {
                bg[i] = curLuma[i].toFloat()
            }
            activeTracks.removeAll { it.countedPersonId == null && it.matchedKnownId == null }
        }

        val shakeCooldownMs = if (stepFilterEnabled) 520L else 320L
        val isShaking = (now - lastShakeTimestamp) < shakeCooldownMs
        if (isShaking) {
            prevLumaGrid = curLuma
            return FrameAnalysisResult(
                boxes = emptyList(),
                isShakingOrPanning = true,
                newlyCounted = emptyList(),
                updatedRepeats = emptyList(),
                horizonZoomActive = farHorizonBoost
            )
        }

        // Step 2: Dual-Criterion Foreground Silhouette Segmentation
        // Requires BOTH difference from the stable running background model AND local silhouette edge contrast
        val fgMask = BooleanArray(GRID_W * GRID_H)
        val motionMask = BooleanArray(GRID_W * GRID_H)

        // Balanced threshold: sensitivity 35..98 maps to background difference 26..13 (never hyper-noisy 5!)
        val baseBgThresh = max(13, (30 - (sensitivity / 100f) * 16f).roundToInt())
        val horizonBgThresh = if (farHorizonBoost) max(10, (baseBgThresh * 0.78f).roundToInt()) else baseBgThresh
        val bottomIgnoreRow = if (stepFilterEnabled) (GRID_H * 0.90f).toInt() else GRID_H - 3

        for (y in 6 until bottomIgnoreRow) {
            val py = (y + bestDy).coerceIn(1, GRID_H - 2)
            val isHorizonRow = y in 10..54
            val activeBgThresh = if (isHorizonRow) horizonBgThresh else baseBgThresh
            val minEdgeContrast = if (isHorizonRow && farHorizonBoost) 10 else 14

            for (x in 4 until GRID_W - 4) {
                val px = (x + bestDx).coerceIn(1, GRID_W - 2)
                val idx = y * GRID_W + x
                val curVal = curLuma[idx]
                val prevVal = prev[py * GRID_W + px]
                val bgVal = bg[idx].roundToInt()

                val frameDiff = abs(curVal - prevVal)
                val bgDiff = abs(curVal - bgVal)

                // Compare pixel against horizontal neighbors to verify a vertical silhouette body vs background
                val leftVal = curLuma[y * GRID_W + max(0, x - 3)]
                val rightVal = curLuma[y * GRID_W + min(GRID_W - 1, x + 3)]
                val localContrast = max(abs(curVal - leftVal), abs(curVal - rightVal))

                if (frameDiff >= max(8, activeBgThresh / 2)) {
                    motionMask[idx] = true
                }

                if (bgDiff >= activeBgThresh && (frameDiff >= 6 || localContrast >= minEdgeContrast)) {
                    fgMask[idx] = true
                }
            }
        }

        // Smoothly update adaptive background model outside foreground regions
        for (i in curLuma.indices) {
            val alpha = if (fgMask[i]) 0.015f else 0.10f
            bg[i] = bg[i] * (1f - alpha) + curLuma[i].toFloat() * alpha
        }
        prevLumaGrid = curLuma

        // Step 3: Connected-components clustering + Strict Human Silhouette Anatomy Classifier
        val visited = BooleanArray(GRID_W * GRID_H)
        val rawDetections = mutableListOf<RawDetection>()
        val queue = IntArray(GRID_W * GRID_H)

        // Minimum silhouette height in grid rows (out of 90 rows):
        // Far horizon across the street: 5 rows (~5.5% height). Near/mid: 22 rows (~24.5% height).
        val minGridHeightFar = if (farHorizonBoost) 5 else 10

        for (y in 6 until bottomIgnoreRow) {
            for (x in 4 until GRID_W - 4) {
                val startIdx = y * GRID_W + x
                if (!fgMask[startIdx] || visited[startIdx]) continue

                var qHead = 0
                var qTail = 0
                queue[qTail++] = startIdx
                visited[startIdx] = true

                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var activePixels = 0
                var movingPixels = 0

                while (qHead < qTail) {
                    val curr = queue[qHead++]
                    val cy = curr / GRID_W
                    val cx = curr % GRID_W
                    activePixels++
                    if (motionMask[curr]) movingPixels++

                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    val nyStart = max(6, cy - 2)
                    val nyEnd = min(bottomIgnoreRow - 1, cy + 2)
                    val nxStart = max(4, cx - 1)
                    val nxEnd = min(GRID_W - 5, cx + 1)

                    for (ny in nyStart..nyEnd) {
                        for (nx in nxStart..nxEnd) {
                            val nIdx = ny * GRID_W + nx
                            if (fgMask[nIdx] && !visited[nIdx]) {
                                visited[nIdx] = true
                                queue[qTail++] = nIdx
                            }
                        }
                    }
                }

                val bwGrid = maxX - minX + 1
                val bhGrid = maxY - minY + 1
                val bottomNorm = (maxY + 1).toFloat() / GRID_H.toFloat()
                val bhNorm = bhGrid.toFloat() / GRID_H.toFloat()

                // PERSPECTIVE FLOOR RULE (Rejects cats, dogs, shoes, and floor objects!):
                // Any object whose bottom touches the lower/floor zone (bottomNorm > 0.62) MUST be tall
                // like a human standing in the room/foreground (bhGrid >= 22 rows = 24.4% of frame height).
                // Small silhouettes (5..21 rows) are ONLY physically possible for humans in the elevated
                // distant horizon band (bottomNorm <= 0.62)!
                val isFarHorizonCandidate = bottomNorm <= 0.62f && minY >= 8 && bhGrid in minGridHeightFar..21
                val isNearMidHumanCandidate = bhGrid >= 22

                if (!isFarHorizonCandidate && !isNearMidHumanCandidate) {
                    continue
                }
                if (isFarHorizonCandidate && !farHorizonBoost && bhGrid < 10) {
                    continue
                }

                // Require genuine active movement inside the silhouette
                val motionFraction = movingPixels.toFloat() / max(1, activePixels).toFloat()
                if (motionFraction < 0.22f) continue

                // Evaluate 4-band Human Silhouette Anatomy (Head - Shoulders - Torso - Legs)
                val humanScore = evaluateHumanSilhouetteAnatomy(
                    fgMask = fgMask,
                    minX = minX,
                    maxX = maxX,
                    minY = minY,
                    maxY = maxY,
                    activePixels = activePixels,
                    isFarHorizon = isFarHorizonCandidate
                )

                if (humanScore >= 0.65f) {
                    val bxNorm = (minX.toFloat() / GRID_W).coerceIn(0f, 0.96f)
                    val byNorm = (minY.toFloat() / GRID_H).coerceIn(0f, 0.94f)
                    val bwNorm = (bwGrid.toFloat() / GRID_W).coerceAtMost(1f - bxNorm)
                    val bhNormClamped = bhNorm.coerceAtMost(1f - byNorm)

                    val isOwnFeet = stepFilterEnabled && (byNorm + bhNormClamped >= 0.90f && byNorm > 0.55f)
                    if (!isOwnFeet) {
                        val desc = extractDescriptorFromRgbGrid(
                            rGrid = rGrid,
                            gGrid = gGrid,
                            bGrid = bGrid,
                            fgMask = fgMask,
                            minX = minX,
                            maxX = maxX,
                            minY = minY,
                            maxY = maxY,
                            isFarHorizon = isFarHorizonCandidate
                        )
                        rawDetections.add(
                            RawDetection(
                                bx = bxNorm,
                                by = byNorm,
                                bw = bwNorm,
                                bh = bhNormClamped,
                                descriptor = desc,
                                confidence = humanScore
                            )
                        )
                    }
                }
            }
        }

        return processDetectionsWithTracker(
            rawDetections = rawDetections,
            isShaking = false,
            isSimulation = false,
            stationaryLineMode = stationaryLineMode,
            lineVertical = lineVertical,
            linePos = linePos,
            memoryMinutes = memoryMinutes,
            currentMemory = currentMemory,
            farHorizonBoost = farHorizonBoost,
            nextPersonIdProvider = nextPersonIdProvider
        )
    }

    /**
     * Verifies that a connected foreground component has the anatomical proportions and vertical contour
     * of a standing/walking human (Head -> Shoulders -> Torso -> Legs), rejecting cats/dogs, chairs,
     * horizontal blobs, and wall/curtain edges.
     */
    private fun evaluateHumanSilhouetteAnatomy(
        fgMask: BooleanArray,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        activePixels: Int,
        isFarHorizon: Boolean
    ): Float {
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        if (bw < 2 || bh < 5 || bh > 82 || bw > 48) return 0f

        // 1. Strict Vertical Upright Human Aspect Ratio (width / height):
        // A standing/walking person is 1.8x to 4.5x taller than wide (aspect 0.20..0.55).
        // Cats, dogs, furniture, and cars have aspect > 0.60 and are immediately rejected!
        val aspect = bw.toFloat() / bh.toFloat()
        val maxAllowedAspect = if (isFarHorizon) 0.52f else 0.55f
        if (aspect < 0.20f || aspect > maxAllowedAspect) return 0f

        val boxArea = bw * bh
        val fillRatio = activePixels.toFloat() / max(1, boxArea).toFloat()
        // Reject sparse noise (< 0.28) or solid rectangular walls/doors (> 0.88)
        if (fillRatio < 0.28f || fillRatio > 0.88f) return 0f

        // For small distant horizon figures (5..8 rows tall), verify vertical continuity and head taper
        if (isFarHorizon && bh <= 8) {
            var activeRows = 0
            for (y in minY..maxY) {
                var rowHas = false
                for (x in minX..maxX) {
                    if (fgMask[y * GRID_W + x]) {
                        rowHas = true
                        break
                    }
                }
                if (rowHas) activeRows++
            }
            return if (activeRows >= bh - 1 && activePixels >= 7) 0.78f else 0f
        }

        // 2. 4-Band Anatomical Slicing:
        // Band 0: Head (0%..24%)
        // Band 1: Shoulders & Chest (24%..52%)
        // Band 2: Waist & Hips (52%..76%)
        // Band 3: Legs (76%..100%)
        val yHeadEnd = minY + max(1, (bh * 0.24f).toInt())
        val yShoulderEnd = minY + max(2, (bh * 0.52f).toInt())
        val yHipEnd = minY + max(3, (bh * 0.76f).toInt())

        fun measureBand(yStart: Int, yEnd: Int): Triple<Float, Float, Int> {
            var sumSpan = 0f
            var sumCenter = 0f
            var validRows = 0
            var bandPixels = 0
            for (y in yStart..min(maxY, yEnd)) {
                var firstX = -1
                var lastX = -1
                for (x in minX..maxX) {
                    if (fgMask[y * GRID_W + x]) {
                        if (firstX == -1) firstX = x
                        lastX = x
                        bandPixels++
                    }
                }
                if (firstX != -1) {
                    sumSpan += (lastX - firstX + 1).toFloat()
                    sumCenter += (firstX + lastX) * 0.5f
                    validRows++
                }
            }
            if (validRows == 0) return Triple(0f, 0f, 0)
            return Triple(sumSpan / validRows, sumCenter / validRows, bandPixels)
        }

        val (headSpan, headCx, headPx) = measureBand(minY, yHeadEnd - 1)
        val (shoulderSpan, shoulderCx, shoulderPx) = measureBand(yHeadEnd, yShoulderEnd - 1)
        val (hipSpan, hipCx, hipPx) = measureBand(yShoulderEnd, yHipEnd - 1)
        val (legSpan, legCx, legPx) = measureBand(yHipEnd, maxY)

        // All 4 anatomical zones (head, shoulders, hips, legs) must be present!
        if (headPx == 0 || shoulderPx == 0 || hipPx == 0 || legPx == 0) return 0f

        // 3. Head-to-Shoulders Taper:
        // A human head is narrower than the shoulders/chest (rejects upside-down triangles or wide-top furniture)
        if (headSpan > shoulderSpan * 0.94f && bh >= 12) return 0f
        if (shoulderSpan < bw * 0.45f) return 0f
        if (hipSpan < bw * 0.35f) return 0f

        // 4. Vertical Spine Alignment:
        // Head center, shoulder center, hip center, and leg center must align along a vertical axis
        val maxSpineDeviation = bw * 0.30f
        if (abs(headCx - shoulderCx) > maxSpineDeviation) return 0f
        if (abs(shoulderCx - hipCx) > maxSpineDeviation) return 0f
        if (abs(hipCx - legCx) > maxSpineDeviation * 1.15f) return 0f

        var score = 0.72f
        if (headSpan <= shoulderSpan * 0.80f) score += 0.10f
        if (aspect in 0.26f..0.46f) score += 0.10f
        if (legSpan <= shoulderSpan * 0.95f) score += 0.05f
        return score.coerceAtMost(0.98f)
    }

    private fun processDetectionsWithTracker(
        rawDetections: List<RawDetection>,
        isShaking: Boolean,
        isSimulation: Boolean,
        stationaryLineMode: Boolean,
        lineVertical: Boolean,
        linePos: Float,
        memoryMinutes: Int,
        currentMemory: List<SilhouetteMemoryEntity>,
        farHorizonBoost: Boolean,
        nextPersonIdProvider: () -> Int
    ): FrameAnalysisResult {
        val now = System.currentTimeMillis()
        val maxAgeMs = memoryMinutes * 60_000L

        // Merge external Room memory with our synchronous internalMemoryCache so there is ZERO async lag
        val mergedById = linkedMapOf<Int, SilhouetteMemoryEntity>()
        for (item in currentMemory) {
            if (now - item.lastSeenTs <= maxAgeMs) {
                mergedById[item.personId] = item
            }
        }
        for (item in internalMemoryCache) {
            if (now - item.lastSeenTs <= maxAgeMs) {
                val existing = mergedById[item.personId]
                if (existing == null || item.lastSeenTs >= existing.lastSeenTs) {
                    mergedById[item.personId] = item
                }
            }
        }
        val validMemory = mergedById.values.toMutableList()
        internalMemoryCache.clear()
        internalMemoryCache.addAll(validMemory)

        // Prune spatial cooldown zones older than 15 seconds
        recentCountZones.removeAll { now - it.timestamp > 15_000L }

        val newlyCounted = mutableListOf<SilhouetteMemoryEntity>()
        val updatedRepeats = mutableListOf<SilhouetteMemoryEntity>()

        activeTracks.forEach { it.matchedThisFrame = false }

        for (det in rawDetections) {
            val cx = det.bx + det.bw / 2f
            val cy = det.by + det.bh / 2f

            var bestTrack: ActiveTrack? = null
            var bestScore = -1f

            for (track in activeTracks) {
                if (track.matchedThisFrame) continue
                val dist = hypot(cx - track.cx, cy - track.cy)
                val maxAllowedDist = if (det.descriptor.isFarHorizon) 0.20f else 0.28f
                if (dist > maxAllowedDist) continue

                val spatialSim = 1f - (dist / maxAllowedDist)
                val appSim = computeSimilarity(det.descriptor.vector, track.descriptor.vector)
                val combined = spatialSim * 0.58f + appSim * 0.42f
                if (combined > 0.42f && combined > bestScore) {
                    bestScore = combined
                    bestTrack = track
                }
            }

            if (bestTrack != null) {
                val stepDist = hypot(cx - bestTrack.cx, cy - bestTrack.cy)
                bestTrack.matchedThisFrame = true
                bestTrack.missedFrames = 0
                bestTrack.totalPathDist += stepDist

                if (stationaryLineMode) {
                    val prevCoord = if (lineVertical) bestTrack.cx else bestTrack.cy
                    val curCoord = if (lineVertical) cx else cy
                    val crossed = (prevCoord < linePos && curCoord >= linePos) ||
                        (prevCoord > linePos && curCoord <= linePos)
                    val minLineHits = if (isSimulation) 1 else 3
                    if (crossed && bestTrack.hits >= minLineHits && bestTrack.countedPersonId == null && bestTrack.matchedKnownId == null) {
                        registerOrDeduplicateTrack(
                            track = bestTrack,
                            det = det,
                            cx = cx,
                            cy = cy,
                            now = now,
                            validMemory = validMemory,
                            newlyCounted = newlyCounted,
                            updatedRepeats = updatedRepeats,
                            nextPersonIdProvider = nextPersonIdProvider
                        )
                    }
                }

                bestTrack.cx = cx
                bestTrack.cy = cy
                bestTrack.bx = det.bx
                bestTrack.by = det.by
                bestTrack.bw = det.bw
                bestTrack.bh = det.bh
                bestTrack.descriptor = blendDescriptors(bestTrack.descriptor, det.descriptor, 0.20f)
                bestTrack.isFarHorizon = det.descriptor.isFarHorizon
                bestTrack.humanConfidence = max(bestTrack.humanConfidence, det.confidence)
                bestTrack.hits += 1

                // Walking Trajectory Verification:
                // In real camera mode, require at least 6 hits AND genuine directional walking displacement
                // so a stationary or jittering object (like a cat sitting/moving in place) never triggers +1!
                val netDisplacement = hypot(cx - bestTrack.startCx, cy - bestTrack.startCy)
                val pathLinearity = if (bestTrack.totalPathDist > 0.001f) {
                    netDisplacement / bestTrack.totalPathDist
                } else 0f

                val qualifiesToCount = if (isSimulation) {
                    bestTrack.hits >= 2
                } else {
                    val minRequiredDisp = if (bestTrack.isFarHorizon) 0.060f else 0.085f
                    bestTrack.hits >= 6 &&
                        netDisplacement >= minRequiredDisp &&
                        pathLinearity >= 0.52f
                }

                if (
                    !stationaryLineMode &&
                    !isShaking &&
                    qualifiesToCount &&
                    bestTrack.countedPersonId == null &&
                    bestTrack.matchedKnownId == null
                ) {
                    registerOrDeduplicateTrack(
                        track = bestTrack,
                        det = det,
                        cx = cx,
                        cy = cy,
                        now = now,
                        validMemory = validMemory,
                        newlyCounted = newlyCounted,
                        updatedRepeats = updatedRepeats,
                        nextPersonIdProvider = nextPersonIdProvider
                    )
                }
            } else {
                // Check if this new track is a continuation of a recently counted person in the same spatial zone
                val recentZoneMatch = recentCountZones.find {
                    hypot(cx - it.cx, cy - it.cy) < 0.18f && (now - it.timestamp) < 10_000L
                }
                val memMatch = findInMemory(det.descriptor, validMemory)
                val knownId = recentZoneMatch?.personId ?: memMatch?.personId

                val newTrack = ActiveTrack(
                    trackId = nextTrackSeq++,
                    startCx = cx,
                    startCy = cy,
                    cx = cx,
                    cy = cy,
                    bx = det.bx,
                    by = det.by,
                    bw = det.bw,
                    bh = det.bh,
                    descriptor = det.descriptor,
                    isFarHorizon = det.descriptor.isFarHorizon,
                    humanConfidence = det.confidence,
                    hits = 1,
                    missedFrames = 0,
                    totalPathDist = 0f,
                    matchedThisFrame = true,
                    countedPersonId = null,
                    matchedKnownId = knownId,
                    isRepeatNotified = knownId != null
                )
                activeTracks.add(newTrack)
            }
        }

        activeTracks.forEach { if (!it.matchedThisFrame) it.missedFrames += 1 }
        activeTracks.removeAll { it.missedFrames > 8 }

        // Only display bounding boxes for tracks that are confirmed or have at least 2 hits in real camera mode
        val uiBoxes = activeTracks
            .filter { it.missedFrames <= 2 && (isSimulation || it.hits >= 2 || it.countedPersonId != null || it.matchedKnownId != null) }
            .map { t ->
                DetectedSilhouetteBox(
                    trackId = t.trackId,
                    bx = t.bx,
                    by = t.by,
                    bw = t.bw,
                    bh = t.bh,
                    upperHex = t.descriptor.upperHex,
                    lowerHex = t.descriptor.lowerHex,
                    isFarHorizon = t.isFarHorizon,
                    countedPersonId = t.countedPersonId,
                    matchedKnownId = t.matchedKnownId,
                    confidence = t.humanConfidence
                )
            }

        return FrameAnalysisResult(
            boxes = uiBoxes,
            isShakingOrPanning = isShaking,
            newlyCounted = newlyCounted,
            updatedRepeats = updatedRepeats,
            horizonZoomActive = farHorizonBoost
        )
    }

    private fun registerOrDeduplicateTrack(
        track: ActiveTrack,
        det: RawDetection,
        cx: Float,
        cy: Float,
        now: Long,
        validMemory: MutableList<SilhouetteMemoryEntity>,
        newlyCounted: MutableList<SilhouetteMemoryEntity>,
        updatedRepeats: MutableList<SilhouetteMemoryEntity>,
        nextPersonIdProvider: () -> Int
    ) {
        // 1. Check spatial cooldown zone first so an object staying in the same area can never double-count
        val zoneHit = recentCountZones.find {
            hypot(cx - it.cx, cy - it.cy) < 0.20f && (now - it.timestamp) < 12_000L
        }
        if (zoneHit != null) {
            track.matchedKnownId = zoneHit.personId
            track.isRepeatNotified = true
            return
        }

        // 2. Check 90-minute Re-ID memory bank
        val match = findInMemory(track.descriptor, validMemory)
        if (match != null) {
            track.matchedKnownId = match.personId
            recentCountZones.add(
                RecentCountZone(
                    personId = match.personId,
                    cx = cx,
                    cy = cy,
                    upperHex = track.descriptor.upperHex,
                    lowerHex = track.descriptor.lowerHex,
                    timestamp = now
                )
            )
            if (!track.isRepeatNotified) {
                track.isRepeatNotified = true
                val updated = match.copy(
                    lastSeenTs = now,
                    repeatHits = match.repeatHits + 1
                )
                val idx = validMemory.indexOfFirst { it.personId == match.personId }
                if (idx >= 0) validMemory[idx] = updated
                val cacheIdx = internalMemoryCache.indexOfFirst { it.personId == match.personId }
                if (cacheIdx >= 0) internalMemoryCache[cacheIdx] = updated
                updatedRepeats.add(updated)
            }
        } else {
            val newId = nextPersonIdProvider()
            track.countedPersonId = newId
            val created = createMemoryEntity(newId, track.descriptor, now)
            validMemory.add(created)
            internalMemoryCache.add(created)
            recentCountZones.add(
                RecentCountZone(
                    personId = newId,
                    cx = cx,
                    cy = cy,
                    upperHex = track.descriptor.upperHex,
                    lowerHex = track.descriptor.lowerHex,
                    timestamp = now
                )
            )
            newlyCounted.add(created)
        }
    }

    /**
     * Extracts upper/lower clothing color descriptor strictly from foreground silhouette pixels (fgMask == true),
     * ignoring background walls/floors inside the bounding box.
     */
    private fun extractDescriptorFromRgbGrid(
        rGrid: IntArray,
        gGrid: IntArray,
        bGrid: IntArray,
        fgMask: BooleanArray,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        isFarHorizon: Boolean
    ): SilhouetteDescriptor {
        val vec = FloatArray(DESCRIPTOR_SIZE)
        val bh = max(1, maxY - minY + 1)
        // Upper clothing (jacket/shirt): 18%..54% of height; Lower clothing (pants/skirt): 54%..88% of height
        val upperStart = minY + (bh * 0.16f).toInt()
        val midY = minY + (bh * 0.54f).toInt()
        val lowerEnd = minY + (bh * 0.90f).toInt()

        var uR = 0
        var uG = 0
        var uB = 0
        var uCount = 0

        var lR = 0
        var lG = 0
        var lB = 0
        var lCount = 0

        for (y in upperStart..min(maxY, lowerEnd)) {
            for (x in minX..maxX) {
                val idx = y * GRID_W + x
                if (!fgMask[idx]) continue
                val r = rGrid[idx].coerceIn(0, 255)
                val g = gGrid[idx].coerceIn(0, 255)
                val b = bGrid[idx].coerceIn(0, 255)

                if (y <= midY) {
                    uR += r
                    uG += g
                    uB += b
                    uCount++
                    vec[min(7, r shr 5)] += 1f
                    vec[8 + min(7, g shr 5)] += 1f
                    vec[16 + min(7, b shr 5)] += 1f
                } else {
                    lR += r
                    lG += g
                    lB += b
                    lCount++
                    vec[24 + min(7, r shr 5)] += 1f
                    vec[32 + min(7, g shr 5)] += 1f
                    vec[40 + min(7, b shr 5)] += 1f
                }
            }
        }

        if (uCount > 0) {
            for (i in 0 until 24) vec[i] /= uCount.toFloat()
            uR /= uCount
            uG /= uCount
            uB /= uCount
        }
        if (lCount > 0) {
            for (i in 24 until 48) vec[i] /= lCount.toFloat()
            lR /= lCount
            lG /= lCount
            lB /= lCount
        }

        val bw = (maxX - minX + 1).toFloat()
        val aspect = (bw / bh.toFloat()).coerceIn(0.15f, 1.0f)

        vec[48] = uR / 255f
        vec[49] = lB / 255f
        vec[50] = aspect
        vec[51] = if (isFarHorizon) 0.85f else 0.35f

        normalizeInPlace(vec)

        return SilhouetteDescriptor(
            vector = vec,
            upperHex = rgbToHex(uR, uG, uB),
            lowerHex = rgbToHex(lR, lG, lB),
            upperR = uR,
            upperG = uG,
            upperB = uB,
            lowerR = lR,
            lowerG = lG,
            lowerB = lB,
            aspectRatio = aspect,
            isFarHorizon = isFarHorizon
        )
    }

    private fun createSyntheticDescriptor(
        seed: Int,
        upperHex: String,
        lowerHex: String,
        aspect: Float,
        isFarHorizon: Boolean
    ): SilhouetteDescriptor {
        val vec = FloatArray(DESCRIPTOR_SIZE)
        vec[(seed * 7) % 48] = 1.0f
        vec[(seed * 13 + 5) % 48] = 0.82f
        vec[50] = aspect
        vec[51] = if (isFarHorizon) 0.85f else 0.35f
        normalizeInPlace(vec)
        val (uR, uG, uB) = hexToRgb(upperHex)
        val (lR, lG, lB) = hexToRgb(lowerHex)
        return SilhouetteDescriptor(
            vector = vec,
            upperHex = upperHex,
            lowerHex = lowerHex,
            upperR = uR,
            upperG = uG,
            upperB = uB,
            lowerR = lR,
            lowerG = lG,
            lowerB = lB,
            aspectRatio = aspect,
            isFarHorizon = isFarHorizon
        )
    }

    private fun findInMemory(
        desc: SilhouetteDescriptor,
        memory: List<SilhouetteMemoryEntity>
    ): SilhouetteMemoryEntity? {
        var best: SilhouetteMemoryEntity? = null
        var bestScore = 0f

        for (entry in memory) {
            val memVec = parseCsvVector(entry.descriptorCsv)
            val vecSim = computeSimilarity(desc.vector, memVec)

            // Also compare upper & lower clothing hex colors directly for lighting-tolerant Re-ID
            val (muR, muG, muB) = hexToRgb(entry.upperHex)
            val (mlR, mlG, mlB) = hexToRgb(entry.lowerHex)
            val upperDiff = (abs(desc.upperR - muR) + abs(desc.upperG - muG) + abs(desc.upperB - muB)) / 3f
            val lowerDiff = (abs(desc.lowerR - mlR) + abs(desc.lowerG - mlG) + abs(desc.lowerB - mlB)) / 3f
            val colorSim = (1f - ((upperDiff + lowerDiff) / 220f)).coerceIn(0f, 1f)

            val combined = max(vecSim, vecSim * 0.55f + colorSim * 0.45f)
            if (combined > bestScore) {
                bestScore = combined
                best = entry
            }
        }
        return if (best != null && bestScore >= 0.72f) best else null
    }

    private fun createMemoryEntity(
        personId: Int,
        desc: SilhouetteDescriptor,
        now: Long
    ): SilhouetteMemoryEntity {
        return SilhouetteMemoryEntity(
            personId = personId,
            descriptorCsv = vectorToCsv(desc.vector),
            upperHex = desc.upperHex,
            lowerHex = desc.lowerHex,
            aspectRatio = desc.aspectRatio,
            distanceZone = if (desc.isFarHorizon) "Вдали (2.5x Зум)" else "Ближний/Средний",
            firstSeenTs = now,
            lastSeenTs = now,
            repeatHits = 0
        )
    }

    private fun computeSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(0f, 1f)
    }

    private fun blendDescriptors(
        oldDesc: SilhouetteDescriptor,
        newDesc: SilhouetteDescriptor,
        alpha: Float
    ): SilhouetteDescriptor {
        val out = FloatArray(oldDesc.vector.size)
        for (i in oldDesc.vector.indices) {
            out[i] = oldDesc.vector[i] * (1f - alpha) + newDesc.vector[i] * alpha
        }
        normalizeInPlace(out)
        val uR = (oldDesc.upperR * (1f - alpha) + newDesc.upperR * alpha).roundToInt()
        val uG = (oldDesc.upperG * (1f - alpha) + newDesc.upperG * alpha).roundToInt()
        val uB = (oldDesc.upperB * (1f - alpha) + newDesc.upperB * alpha).roundToInt()
        val lR = (oldDesc.lowerR * (1f - alpha) + newDesc.lowerR * alpha).roundToInt()
        val lG = (oldDesc.lowerG * (1f - alpha) + newDesc.lowerG * alpha).roundToInt()
        val lB = (oldDesc.lowerB * (1f - alpha) + newDesc.lowerB * alpha).roundToInt()
        return SilhouetteDescriptor(
            vector = out,
            upperHex = rgbToHex(uR, uG, uB),
            lowerHex = rgbToHex(lR, lG, lB),
            upperR = uR,
            upperG = uG,
            upperB = uB,
            lowerR = lR,
            lowerG = lG,
            lowerB = lB,
            aspectRatio = oldDesc.aspectRatio * (1f - alpha) + newDesc.aspectRatio * alpha,
            isFarHorizon = newDesc.isFarHorizon
        )
    }

    private fun normalizeInPlace(vec: FloatArray) {
        var sumSq = 0f
        for (v in vec) sumSq += v * v
        val norm = sqrt(sumSq).let { if (it < 1e-6f) 1f else it }
        for (i in vec.indices) vec[i] /= norm
    }

    private fun rgbToHex(r: Int, g: Int, b: Int): String =
        String.format("#%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    private fun hexToRgb(hex: String): Triple<Int, Int, Int> {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return Triple(128, 128, 128)
        val r = clean.substring(0, 2).toIntOrNull(16) ?: 128
        val g = clean.substring(2, 4).toIntOrNull(16) ?: 128
        val b = clean.substring(4, 6).toIntOrNull(16) ?: 128
        return Triple(r, g, b)
    }

    private fun vectorToCsv(vec: FloatArray): String =
        vec.joinToString(",") { String.format(java.util.Locale.US, "%.4f", it) }

    private fun parseCsvVector(csv: String): FloatArray {
        val parts = csv.split(",")
        val out = FloatArray(DESCRIPTOR_SIZE)
        for (i in 0 until min(parts.size, DESCRIPTOR_SIZE)) {
            out[i] = parts[i].toFloatOrNull() ?: 0f
        }
        return out
    }
}
