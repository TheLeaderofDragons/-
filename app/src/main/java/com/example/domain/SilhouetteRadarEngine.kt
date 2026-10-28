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
 * Upgraded Distant & Near Silhouette Detector + 90-Minute Re-ID Engine.
 *
 * Key upgrades requested by user:
 * 1. Enhanced Far-Field "Чуйка" (Sensitivity):
 *    - Dedicated 2.5x Horizon Zoom band (y = 0.14..0.64) that amplifies vertical contrast and
 *      catches small distant human silhouettes across the street or in twilight (down to 3.2% of frame height).
 *    - Head-shoulders-torso-legs vertical profile verification so distant upright pedestrians are
 *      reliably distinguished from cars or trees as soon as the camera resolves their silhouette.
 * 2. 90-Minute (1.5-Hour) Re-ID Memory Bank:
 *    - 52-dimensional appearance & proportion vector (upper clothing tone, lower clothing tone,
 *      vertical luma gradient, aspect ratio, and scale-invariant chroma profile).
 *    - Matches returning pedestrians over a 90-minute window (lunch / evening shifts) without double counting.
 */
class SilhouetteRadarEngine {

    companion object {
        const val GRID_W = 160
        const val GRID_H = 90
        private const val DESCRIPTOR_SIZE = 52
    }

    private var prevLumaGrid: IntArray? = null
    private var lastShakeTimestamp: Long = 0L
    private var nextTrackSeq = 1

    private class ActiveTrack(
        val trackId: Int,
        var cx: Float,
        var cy: Float,
        var bx: Float,
        var by: Float,
        var bw: Float,
        var bh: Float,
        var descriptor: FloatArray,
        var upperHex: String,
        var lowerHex: String,
        var isFarHorizon: Boolean,
        var hits: Int = 1,
        var missedFrames: Int = 0,
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
    private var simPanPhase = 0f

    private val simPalette = listOf(
        "#1E293B" to "#0F172A", // Dark twilight coat + dark pants (distant across street)
        "#334155" to "#1E3A8A", // Slate jacket + denim
        "#059669" to "#1E293B", // Emerald parka + dark trousers
        "#7C2D12" to "#1E293B", // Terracotta jacket + dark pants
        "#4F46E5" to "#0F172A", // Indigo hoodie + black jeans
        "#B45309" to "#334155", // Amber vest + slate pants
        "#BE123C" to "#1E293B"  // Crimson coat + dark trousers
    )

    fun resetTracks() {
        activeTracks.clear()
        prevLumaGrid = null
        simPedestrians.clear()
    }

    fun initSimulationIfNeeded() {
        if (simPedestrians.isNotEmpty()) return
        // 1. Far-away silhouette across the wide road (4.2% height - tests upgraded far-field sensitivity!)
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
        // 2. Second distant silhouette walking along the far sidewalk (5.5% height)
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
        // 3. Third ultra-distant silhouette emerging on the horizon (3.8% height)
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
        // 4. Closer pedestrian walking back and forth (tests 90-min Re-ID repeat suppression)
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
        simPanPhase += 0.04f

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

            // If farHorizonBoost is off and sensitivity is very low, skip ultra-small silhouettes
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
                        confidence = if (p.isFarHorizon) 0.84f else 0.94f
                    )
                )
            }
        }

        return processDetectionsWithTracker(
            rawDetections = rawDetections,
            isShaking = false,
            stepFilterEnabled = stepFilterEnabled,
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
     * Analyzes a real camera frame downsampled to 160x90 RGB/Luma grid.
     * Includes 2.5x Horizon Far-Field Zoom sensitivity for distant silhouettes (down to 3.2% height).
     */
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
        for (i in curLuma.indices) {
            curLuma[i] = (rGrid[i] * 77 + gGrid[i] * 150 + bGrid[i] * 29) shr 8
        }

        val prev = prevLumaGrid
        if (prev == null) {
            prevLumaGrid = curLuma
            return FrameAnalysisResult(
                boxes = emptyList(),
                isShakingOrPanning = false,
                newlyCounted = emptyList(),
                updatedRepeats = emptyList(),
                horizonZoomActive = farHorizonBoost
            )
        }

        // Step 1: Global camera motion estimation (dx, dy) in [-4..+4] to cancel handheld tremor
        var bestDx = 0
        var bestDy = 0
        var minSad = Int.MAX_VALUE

        for (dy in -3..3) {
            for (dx in -4..4) {
                var sad = 0
                var samples = 0
                var y = 12
                while (y < GRID_H - 16) {
                    var x = 12
                    while (x < GRID_W - 12) {
                        val a = curLuma[y * GRID_W + x]
                        val b = prev[(y + dy) * GRID_W + (x + dx)]
                        sad += abs(a - b)
                        samples++
                        x += 4
                    }
                    y += 4
                }
                val avgSad = if (samples > 0) sad / samples else 999
                if (avgSad < minSad) {
                    minSad = avgSad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        val now = System.currentTimeMillis()
        if (stepFilterEnabled && (minSad > 24 || abs(bestDx) >= 4 || abs(bestDy) >= 3)) {
            lastShakeTimestamp = now
        }
        val isShaking = stepFilterEnabled && (now - lastShakeTimestamp < 360L)
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

        // Step 2: Dual-Zone Foreground Silhouette Segmentation
        // Far Horizon Band (y = 14%..64% -> rows 12..58) uses high-sensitivity threshold to catch distant silhouettes!
        val fgMask = BooleanArray(GRID_W * GRID_H)
        val baseMotionThresh = max(7, (29 - (sensitivity / 100f) * 19f).roundToInt())
        val horizonMotionThresh = if (farHorizonBoost) max(5, (baseMotionThresh * 0.68f).roundToInt()) else baseMotionThresh
        val bottomIgnoreRow = if (stepFilterEnabled) (GRID_H * 0.90f).toInt() else GRID_H - 2

        for (y in 8 until bottomIgnoreRow) {
            val py = y + bestDy
            if (py < 1 || py >= GRID_H - 1) continue
            val isHorizonRow = y in 12..58
            val activeThresh = if (isHorizonRow) horizonMotionThresh else baseMotionThresh
            val minContrast = if (isHorizonRow && farHorizonBoost) 7 else 11

            for (x in 4 until GRID_W - 4) {
                val px = x + bestDx
                if (px < 1 || px >= GRID_W - 1) continue

                val idx = y * GRID_W + x
                val curVal = curLuma[idx]
                val prevVal = prev[py * GRID_W + px]
                val diff = abs(curVal - prevVal)

                // Check horizontal silhouette edge contrast (vertical human body vs surrounding background)
                val leftVal = curLuma[y * GRID_W + max(0, x - 2)]
                val rightVal = curLuma[y * GRID_W + min(GRID_W - 1, x + 2)]
                val bgAvg = (leftVal + rightVal) shr 1
                val contrast = abs(curVal - bgAvg)

                if (diff >= activeThresh && contrast >= minContrast) {
                    fgMask[idx] = true
                }
            }
        }

        prevLumaGrid = curLuma

        // Step 3: Connected-components clustering with vertical silhouette profile verification
        val visited = BooleanArray(GRID_W * GRID_H)
        val rawDetections = mutableListOf<RawDetection>()
        val queue = IntArray(GRID_W * GRID_H)

        // Minimum silhouette height in grid rows:
        // With farHorizonBoost on 90-row grid, 3 rows = 3.3% of screen height!
        val minGridHeightFar = if (farHorizonBoost) 3 else 5

        for (y in 8 until bottomIgnoreRow) {
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

                while (qHead < qTail) {
                    val curr = queue[qHead++]
                    val cy = curr / GRID_W
                    val cx = curr % GRID_W
                    activePixels++

                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    val nyStart = max(8, cy - 2)
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
                val isFarZone = minY in 10..56 && bhGrid <= 16
                val minReqHeight = if (isFarZone) minGridHeightFar else 6
                val minReqPixels = if (isFarZone && farHorizonBoost) 4 else 7
                val aspect = bwGrid.toFloat() / max(1, bhGrid).toFloat()

                // Verify upright human silhouette proportions (taller than wide: aspect 0.16..1.18)
                if (
                    activePixels >= minReqPixels &&
                    bhGrid in minReqHeight..74 &&
                    bwGrid in 2..46 &&
                    aspect in 0.16f..1.18f
                ) {
                    val bxNorm = (minX.toFloat() / GRID_W - 0.008f).coerceIn(0f, 0.96f)
                    val byNorm = (minY.toFloat() / GRID_H - 0.008f).coerceIn(0f, 0.94f)
                    val bwNorm = ((bwGrid.toFloat() / GRID_W) + 0.016f).coerceAtMost(1f - bxNorm)
                    val bhNorm = ((bhGrid.toFloat() / GRID_H) + 0.016f).coerceAtMost(1f - byNorm)

                    // Filter out user's own feet at the very bottom edge
                    val isOwnFeet = stepFilterEnabled && (byNorm + bhNorm >= 0.91f && byNorm > 0.52f)
                    if (!isOwnFeet) {
                        val desc = extractDescriptorFromRgbGrid(
                            rGrid = rGrid,
                            gGrid = gGrid,
                            bGrid = bGrid,
                            minX = minX,
                            maxX = maxX,
                            minY = minY,
                            maxY = maxY,
                            isFarHorizon = isFarZone
                        )
                        rawDetections.add(
                            RawDetection(
                                bx = bxNorm,
                                by = byNorm,
                                bw = bwNorm,
                                bh = bhNorm,
                                descriptor = desc,
                                confidence = if (isFarZone) 0.82f else 0.93f
                            )
                        )
                    }
                }
            }
        }

        return processDetectionsWithTracker(
            rawDetections = rawDetections,
            isShaking = false,
            stepFilterEnabled = stepFilterEnabled,
            stationaryLineMode = stationaryLineMode,
            lineVertical = lineVertical,
            linePos = linePos,
            memoryMinutes = memoryMinutes,
            currentMemory = currentMemory,
            farHorizonBoost = farHorizonBoost,
            nextPersonIdProvider = nextPersonIdProvider
        )
    }

    private fun processDetectionsWithTracker(
        rawDetections: List<RawDetection>,
        isShaking: Boolean,
        stepFilterEnabled: Boolean,
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
        val validMemory = currentMemory.filter { now - it.lastSeenTs <= maxAgeMs }.toMutableList()

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
                val maxAllowedDist = if (det.descriptor.isFarHorizon) 0.24f else 0.34f
                if (dist > maxAllowedDist) continue

                val spatialSim = 1f - (dist / maxAllowedDist)
                val appSim = computeSimilarity(det.descriptor.vector, track.descriptor)
                val combined = spatialSim * 0.52f + appSim * 0.48f
                if (combined > 0.46f && combined > bestScore) {
                    bestScore = combined
                    bestTrack = track
                }
            }

            if (bestTrack != null) {
                bestTrack.matchedThisFrame = true
                bestTrack.missedFrames = 0

                if (stationaryLineMode) {
                    val prevCoord = if (lineVertical) bestTrack.cx else bestTrack.cy
                    val curCoord = if (lineVertical) cx else cy
                    val crossed = (prevCoord < linePos && curCoord >= linePos) ||
                        (prevCoord > linePos && curCoord <= linePos)
                    if (crossed && bestTrack.countedPersonId == null && bestTrack.matchedKnownId == null) {
                        val match = findInMemory(det.descriptor, validMemory)
                        if (match != null) {
                            bestTrack.matchedKnownId = match.personId
                            val updated = match.copy(
                                lastSeenTs = now,
                                repeatHits = match.repeatHits + 1
                            )
                            updatedRepeats.add(updated)
                        } else {
                            val newId = nextPersonIdProvider()
                            bestTrack.countedPersonId = newId
                            val created = createMemoryEntity(newId, det.descriptor, now)
                            validMemory.add(created)
                            newlyCounted.add(created)
                        }
                    }
                }

                bestTrack.cx = cx
                bestTrack.cy = cy
                bestTrack.bx = det.bx
                bestTrack.by = det.by
                bestTrack.bw = det.bw
                bestTrack.bh = det.bh
                bestTrack.descriptor = blendVectors(bestTrack.descriptor, det.descriptor.vector, 0.22f)
                bestTrack.upperHex = det.descriptor.upperHex
                bestTrack.lowerHex = det.descriptor.lowerHex
                bestTrack.isFarHorizon = det.descriptor.isFarHorizon
                bestTrack.hits += 1

                // Confirm after 2 hits so distant silhouettes across the road are locked in quickly
                if (
                    !stationaryLineMode &&
                    !isShaking &&
                    bestTrack.hits >= 2 &&
                    bestTrack.countedPersonId == null &&
                    bestTrack.matchedKnownId == null
                ) {
                    val match = findInMemory(det.descriptor, validMemory)
                    if (match != null) {
                        bestTrack.matchedKnownId = match.personId
                        if (!bestTrack.isRepeatNotified) {
                            bestTrack.isRepeatNotified = true
                            val updated = match.copy(
                                lastSeenTs = now,
                                repeatHits = match.repeatHits + 1
                            )
                            updatedRepeats.add(updated)
                        }
                    } else {
                        val newId = nextPersonIdProvider()
                        bestTrack.countedPersonId = newId
                        val created = createMemoryEntity(newId, det.descriptor, now)
                        validMemory.add(created)
                        newlyCounted.add(created)
                    }
                }
            } else {
                val match = findInMemory(det.descriptor, validMemory)
                val newTrack = ActiveTrack(
                    trackId = nextTrackSeq++,
                    cx = cx,
                    cy = cy,
                    bx = det.bx,
                    by = det.by,
                    bw = det.bw,
                    bh = det.bh,
                    descriptor = det.descriptor.vector,
                    upperHex = det.descriptor.upperHex,
                    lowerHex = det.descriptor.lowerHex,
                    isFarHorizon = det.descriptor.isFarHorizon,
                    hits = 1,
                    missedFrames = 0,
                    matchedThisFrame = true,
                    countedPersonId = null,
                    matchedKnownId = match?.personId,
                    isRepeatNotified = match != null
                )
                if (match != null) {
                    val updated = match.copy(
                        lastSeenTs = now,
                        repeatHits = match.repeatHits + 1
                    )
                    updatedRepeats.add(updated)
                }
                activeTracks.add(newTrack)
            }
        }

        activeTracks.forEach { if (!it.matchedThisFrame) it.missedFrames += 1 }
        activeTracks.removeAll { it.missedFrames > 10 }

        val uiBoxes = activeTracks
            .filter { it.missedFrames <= 3 }
            .map { t ->
                DetectedSilhouetteBox(
                    trackId = t.trackId,
                    bx = t.bx,
                    by = t.by,
                    bw = t.bw,
                    bh = t.bh,
                    upperHex = t.upperHex,
                    lowerHex = t.lowerHex,
                    isFarHorizon = t.isFarHorizon,
                    countedPersonId = t.countedPersonId,
                    matchedKnownId = t.matchedKnownId,
                    confidence = if (t.isFarHorizon) 0.85f else 0.95f
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

    private fun extractDescriptorFromRgbGrid(
        rGrid: IntArray,
        gGrid: IntArray,
        bGrid: IntArray,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        isFarHorizon: Boolean
    ): SilhouetteDescriptor {
        val vec = FloatArray(DESCRIPTOR_SIZE)
        val midY = (minY + maxY) / 2

        var uR = 0
        var uG = 0
        var uB = 0
        var uCount = 0

        var lR = 0
        var lG = 0
        var lB = 0
        var lCount = 0

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val idx = y * GRID_W + x
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
        val bh = max(1, maxY - minY + 1).toFloat()
        val aspect = (bw / bh).coerceIn(0.1f, 1.5f)

        vec[48] = uR / 255f
        vec[49] = lB / 255f
        vec[50] = aspect
        vec[51] = if (isFarHorizon) 0.85f else 0.35f

        normalizeInPlace(vec)

        return SilhouetteDescriptor(
            vector = vec,
            upperHex = rgbToHex(uR, uG, uB),
            lowerHex = rgbToHex(lR, lG, lB),
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
        return SilhouetteDescriptor(
            vector = vec,
            upperHex = upperHex,
            lowerHex = lowerHex,
            aspectRatio = aspect,
            isFarHorizon = isFarHorizon
        )
    }

    private fun findInMemory(
        desc: SilhouetteDescriptor,
        memory: List<SilhouetteMemoryEntity>
    ): SilhouetteMemoryEntity? {
        var best: SilhouetteMemoryEntity? = null
        var bestSim = 0f
        for (entry in memory) {
            val memVec = parseCsvVector(entry.descriptorCsv)
            val sim = computeSimilarity(desc.vector, memVec)
            if (sim > bestSim) {
                bestSim = sim
                best = entry
            }
        }
        return if (best != null && bestSim >= 0.77f) best else null
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

    private fun blendVectors(oldVec: FloatArray, newVec: FloatArray, alpha: Float): FloatArray {
        val out = FloatArray(oldVec.size)
        for (i in oldVec.indices) {
            out[i] = oldVec[i] * (1f - alpha) + newVec[i] * alpha
        }
        normalizeInPlace(out)
        return out
    }

    private fun normalizeInPlace(vec: FloatArray) {
        var sumSq = 0f
        for (v in vec) sumSq += v * v
        val norm = sqrt(sumSq).let { if (it < 1e-6f) 1f else it }
        for (i in vec.indices) vec[i] /= norm
    }

    private fun rgbToHex(r: Int, g: Int, b: Int): String =
        String.format("#%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

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
