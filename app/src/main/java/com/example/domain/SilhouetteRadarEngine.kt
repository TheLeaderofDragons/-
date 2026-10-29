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
    val midAccentR: Int,
    val midAccentG: Int,
    val midAccentB: Int,
    val headLuma: Int,
    val shoeLuma: Int,
    val aspectRatio: Float,
    val shoulderToHipRatio: Float,
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
 * Multi-Scale Human Silhouette Detector (Far Horizon 2.5x + Mid + Close-Up) & 5-Zone Biometric/Clothing Re-ID Engine.
 *
 * Designed for real outdoor/twilight and indoor field surveys:
 * 1. Handheld Motion Compensation (bestDx, bestDy alignment):
 *    - Compensates for natural hand micro-sway so holding the phone does NOT lock up in "Стабилизация поворота руки".
 *    - Only pauses on rapid full-frame camera turns.
 * 2. Multi-Scale Silhouette Segmentation (Far 3.2%..22%, Mid 22%..55%, Close 55%..95%):
 *    - Combines motion/background difference with bilateral horizontal silhouette contrast so both distant pedestrians
 *      under streetlights and close-up pedestrians walking toward the camera are captured.
 * 3. Vertical Body Bridging & Non-Maximum Suppression (NMS):
 *    - Reconstructs a single human figure even when split by stair railings, belts, or carried bags,
 *      preventing a single approaching person from ever triggering +2 or +3 at once.
 * 4. Perspective Floor Anti-Pet Filter:
 *    - Small moving floor objects (bottomNorm > 0.65 and height < 22%, or quadruped width/height > 0.60)
 *      such as cats/dogs are strictly rejected.
 * 5. Two-Tier 5-Zone Clothing + Figure Proportion Re-ID (90-Min Memory):
 *    - Short-term spatial-scale lock guarantees 1 person in view = strictly +1.
 *    - Long-term 5-zone (Head/Shoulders/Mid-Torso-Bag/Legs/Shoes) + body build (aspect & shoulder-to-hip ratio)
 *      matching remembers counted people without confusing different people in similar dark jackets.
 */
class SilhouetteRadarEngine {

    companion object {
        const val GRID_W = 160
        const val GRID_H = 90
        private const val DESCRIPTOR_SIZE = 64
    }

    private var prevLumaGrid: IntArray? = null
    private var bgLumaModel: FloatArray? = null
    private var lastShakeTimestamp: Long = 0L
    private var nextTrackSeq = 1

    // Synchronous internal cache so newly counted silhouettes are immediately known on the very next frame
    private val internalMemoryCache = mutableListOf<SilhouetteMemoryEntity>()

    private data class RecentCountZone(
        val personId: Int,
        var cx: Float,
        var cy: Float,
        var bw: Float,
        var bh: Float,
        val isFarHorizon: Boolean,
        val descriptor: SilhouetteDescriptor,
        var timestamp: Long
    )

    private val recentCountZones = mutableListOf<RecentCountZone>()

    private class ActiveTrack(
        val trackId: Int,
        val startCx: Float,
        val startCy: Float,
        val startBh: Float,
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

    private data class RawCluster(
        var minX: Int,
        var maxX: Int,
        var minY: Int,
        var maxY: Int,
        var activePixels: Int,
        var movingPixels: Int
    )

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
                0.030f + (100 - sensitivity) * 0.0004f
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
     * Supports distant horizon pedestrians (from 3.3% screen height), mid-range pedestrians,
     * and close-up pedestrians (up to 95% screen height), while filtering out floor pets (cats/dogs),
     * furniture, and preventing multi-counting (+3) on stairs/railings.
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
        for (i in curLuma.indices) {
            curLuma[i] = (rGrid[i] * 77 + gGrid[i] * 150 + bGrid[i] * 29) shr 8
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

        // Step 1: Global camera motion estimation (dx, dy) in [-6..+6, -4..+4]
        // First measure zero-shift SAD (dx=0, dy=0). If the background is already stable (zeroShiftSad <= 14),
        // keep (bestDx=0, bestDy=0) so the motion estimator NEVER locks onto a walking pedestrian!
        var zeroShiftSad = 0
        var zeroSamples = 0
        for (y in 8 until GRID_H - 10 step 5) {
            for (x in 8 until GRID_W - 8 step 5) {
                zeroShiftSad += abs(curLuma[y * GRID_W + x] - prev[y * GRID_W + x])
                zeroSamples++
            }
        }
        val avgZeroShiftSad = if (zeroSamples > 0) zeroShiftSad / zeroSamples else 0

        var bestDx = 0
        var bestDy = 0
        var minSad = avgZeroShiftSad

        if (avgZeroShiftSad > 14) {
            for (dy in -4..4) {
                for (dx in -6..6) {
                    if (dx == 0 && dy == 0) continue
                    var sad = 0
                    var samples = 0
                    var y = 8
                    while (y < GRID_H - 10) {
                        var x = 8
                        while (x < GRID_W - 8) {
                            val a = curLuma[y * GRID_W + x]
                            val b = prev[(y + dy) * GRID_W + (x + dx)]
                            sad += abs(a - b)
                            samples++
                            x += 5
                        }
                        y += 5
                    }
                    val avgSad = if (samples > 0) sad / samples else 999
                    // Only accept a non-zero camera shift if it reduces global SAD by at least 30%
                    if (avgSad < (avgZeroShiftSad * 0.70f).toInt() && avgSad < minSad) {
                        minSad = avgSad
                        bestDx = dx
                        bestDy = dy
                    }
                }
            }
        }

        // Measure how much of the scene changed AFTER compensating for handheld micro-shift (bestDx, bestDy)
        var alignedChangedBlocks = 0
        var alignedTotalBlocks = 0
        for (y in 8 until GRID_H - 10 step 4) {
            for (x in 8 until GRID_W - 8 step 4) {
                val py = (y + bestDy).coerceIn(0, GRID_H - 1)
                val px = (x + bestDx).coerceIn(0, GRID_W - 1)
                val d = abs(curLuma[y * GRID_W + x] - prev[py * GRID_W + px])
                alignedTotalBlocks++
                if (d >= 18) alignedChangedBlocks++
            }
        }
        val alignedChangeFraction = alignedChangedBlocks.toFloat() / max(1, alignedTotalBlocks).toFloat()
        val now = System.currentTimeMillis()

        // Only declare true camera panning when the phone is being rapidly turned/swept across the scene,
        // NOT during normal handheld aiming or when a person walks close to the lens!
        val panSadLimit = if (stepFilterEnabled) 22 else 27
        val isCameraTurningFast =
            minSad > panSadLimit ||
                (abs(bestDx) >= 6 && minSad > 14) ||
                (abs(bestDy) >= 4 && minSad > 14) ||
                alignedChangeFraction > 0.56f

        if (isCameraTurningFast) {
            lastShakeTimestamp = now
            for (i in curLuma.indices) {
                bg[i] = bg[i] * 0.35f + curLuma[i].toFloat() * 0.65f
            }
            activeTracks.removeAll { it.countedPersonId == null && it.matchedKnownId == null && it.hits <= 1 }
            prevLumaGrid = curLuma
            return FrameAnalysisResult(
                boxes = emptyList(),
                isShakingOrPanning = true,
                newlyCounted = emptyList(),
                updatedRepeats = emptyList(),
                horizonZoomActive = farHorizonBoost
            )
        }

        val isShaking = (now - lastShakeTimestamp) < 180L

        // Step 2: Multi-Scale Foreground & Silhouette Contrast Segmentation
        val fgMask = BooleanArray(GRID_W * GRID_H)
        val motionMask = BooleanArray(GRID_W * GRID_H)

        // Sensitivity 35..98 maps to background threshold 23..10 (far horizon 17..7)
        val baseBgThresh = max(10, (26 - (sensitivity / 100f) * 15f).roundToInt())
        val horizonBgThresh = if (farHorizonBoost) max(7, (baseBgThresh * 0.72f).roundToInt()) else baseBgThresh
        val bottomIgnoreRow = if (stepFilterEnabled) (GRID_H * 0.93f).toInt() else GRID_H - 2

        for (y in 3 until bottomIgnoreRow) {
            val py = (y + bestDy).coerceIn(1, GRID_H - 2)
            val isHorizonRow = y in 6..58
            val activeBgThresh = if (isHorizonRow) horizonBgThresh else baseBgThresh
            val minMotionDiff = if (isHorizonRow && farHorizonBoost) 4 else 6
            val minEdgeContrast = if (isHorizonRow && farHorizonBoost) 8 else 12

            for (x in 3 until GRID_W - 3) {
                val px = (x + bestDx).coerceIn(1, GRID_W - 2)
                val idx = y * GRID_W + x
                val alignIdx = py * GRID_W + px

                val curVal = curLuma[idx]
                val prevVal = prev[alignIdx]
                val bgVal = bg[alignIdx].roundToInt()

                val frameDiff = abs(curVal - prevVal)
                val bgDiff = abs(curVal - bgVal)

                val span = if (isHorizonRow) 2 else 4
                val leftVal = curLuma[y * GRID_W + max(0, x - span)]
                val rightVal = curLuma[y * GRID_W + min(GRID_W - 1, x + span)]
                val bilateralContrast = min(abs(curVal - leftVal), abs(curVal - rightVal))

                if (frameDiff >= minMotionDiff) {
                    motionMask[idx] = true
                }

                // A pixel belongs to a foreground silhouette if:
                // (a) It stands out from the aligned running background model (bgDiff >= activeBgThresh), OR
                // (b) It is an actively moving contour with strong bilateral contrast
                if (
                    bgDiff >= activeBgThresh ||
                    (frameDiff >= minMotionDiff + 2 && bilateralContrast >= minEdgeContrast)
                ) {
                    fgMask[idx] = true
                }
            }
        }

        // Step 3: Connected-components extraction
        val visited = BooleanArray(GRID_W * GRID_H)
        val queue = IntArray(GRID_W * GRID_H)
        val initialClusters = mutableListOf<RawCluster>()

        for (y in 3 until bottomIgnoreRow) {
            for (x in 3 until GRID_W - 3) {
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

                    val nyStart = max(3, cy - 2)
                    val nyEnd = min(bottomIgnoreRow - 1, cy + 2)
                    val nxStart = max(3, cx - 2)
                    val nxEnd = min(GRID_W - 4, cx + 2)

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

                if (activePixels >= 3) {
                    initialClusters.add(
                        RawCluster(
                            minX = minX,
                            maxX = maxX,
                            minY = minY,
                            maxY = maxY,
                            activePixels = activePixels,
                            movingPixels = movingPixels
                        )
                    )
                }
            }
        }

        // Step 3B: Vertical & Spatial Body Bridging
        // Merges fragments of the SAME person separated by stair railings, belts, bags, or shadows
        val mergedClusters = mergeFragmentsOfSamePerson(initialClusters)

        // Step 4: Evaluate Human Silhouette Anatomy & Perspective Rules
        val candidateDetections = mutableListOf<RawDetection>()
        val minGridHeightFar = if (farHorizonBoost) 3 else 7

        for (cluster in mergedClusters) {
            val minX = cluster.minX
            val maxX = cluster.maxX
            val minY = cluster.minY
            val maxY = cluster.maxY
            val bwGrid = maxX - minX + 1
            val bhGrid = maxY - minY + 1
            val bottomNorm = (maxY + 1).toFloat() / GRID_H.toFloat()
            val bhNorm = bhGrid.toFloat() / GRID_H.toFloat()

            // PERSPECTIVE FLOOR RULE (Anti-Cat / Anti-Pet / Anti-Clutter):
            // Any object whose bottom is on the near floor (bottomNorm > 0.65) MUST be at least 20 rows tall
            // (22.2% of frame height). Small silhouettes (3..19 rows) are ONLY allowed in the elevated
            // distant horizon/midground band (bottomNorm <= 0.65)!
            val isFarHorizonCandidate = bottomNorm <= 0.65f && minY >= 4 && bhGrid in minGridHeightFar..19
            val isNearMidHumanCandidate = bhGrid >= 20

            if (!isFarHorizonCandidate && !isNearMidHumanCandidate) {
                continue
            }
            if (isFarHorizonCandidate && !farHorizonBoost && bhGrid < 7) {
                continue
            }

            // Require at least leading-edge movement (when a uniform coat moves by 1px/frame, its 1px leading edge
            // has movingPixels >= max(2, bhGrid / 4))
            val minMovingPixels = if (isFarHorizonCandidate) 2 else max(4, bhGrid / 4)
            if (cluster.movingPixels < minMovingPixels) continue

            val humanScore = evaluateHumanSilhouetteAnatomy(
                fgMask = fgMask,
                curLuma = curLuma,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                activePixels = cluster.activePixels,
                isFarHorizon = isFarHorizonCandidate
            )

            if (humanScore >= 0.62f) {
                val bxNorm = (minX.toFloat() / GRID_W).coerceIn(0f, 0.96f)
                val byNorm = (minY.toFloat() / GRID_H).coerceIn(0f, 0.95f)
                val bwNorm = (bwGrid.toFloat() / GRID_W).coerceAtMost(1f - bxNorm)
                val bhNormClamped = bhNorm.coerceAtMost(1f - byNorm)

                val isOwnFeet = stepFilterEnabled && (byNorm > 0.68f && byNorm + bhNormClamped >= 0.91f)
                if (!isOwnFeet) {
                    val desc = extractDescriptorFromRgbGrid(
                        rGrid = rGrid,
                        gGrid = gGrid,
                        bGrid = bGrid,
                        curLuma = curLuma,
                        fgMask = fgMask,
                        minX = minX,
                        maxX = maxX,
                        minY = minY,
                        maxY = maxY,
                        isFarHorizon = isFarHorizonCandidate
                    )
                    candidateDetections.add(
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

        // Step 5: Non-Maximum Suppression (NMS) so overlapping/nested boxes on one person never double-count
        val nmsDetections = suppressOverlappingDetections(candidateDetections)

        // Update adaptive background model: freeze background inside detected human boxes so slow/standing
        // people in front of the camera are not absorbed into the background!
        val insideHumanBox = BooleanArray(GRID_W * GRID_H)
        for (det in nmsDetections) {
            val x0 = (det.bx * GRID_W).toInt().coerceIn(0, GRID_W - 1)
            val x1 = ((det.bx + det.bw) * GRID_W).toInt().coerceIn(0, GRID_W - 1)
            val y0 = (det.by * GRID_H).toInt().coerceIn(0, GRID_H - 1)
            val y1 = ((det.by + det.bh) * GRID_H).toInt().coerceIn(0, GRID_H - 1)
            for (yy in y0..y1) {
                for (xx in x0..x1) {
                    insideHumanBox[yy * GRID_W + xx] = true
                }
            }
        }
        for (i in curLuma.indices) {
            val alpha = when {
                insideHumanBox[i] -> 0.003f
                fgMask[i] -> 0.015f
                else -> 0.12f
            }
            bg[i] = bg[i] * (1f - alpha) + curLuma[i].toFloat() * alpha
        }
        prevLumaGrid = curLuma

        return processDetectionsWithTracker(
            rawDetections = nmsDetections,
            isShaking = isShaking,
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
     * Merges vertically or spatially adjacent foreground clusters that belong to the same human body
     * (e.g., head/torso/legs split by stair railings, a bright bag, or a belt).
     */
    private fun mergeFragmentsOfSamePerson(clusters: List<RawCluster>): List<RawCluster> {
        if (clusters.size <= 1) return clusters
        val sorted = clusters.sortedBy { it.minY }.toMutableList()
        var mergedAny = true
        while (mergedAny) {
            mergedAny = false
            outer@ for (i in 0 until sorted.size) {
                for (j in i + 1 until sorted.size) {
                    val a = sorted[i]
                    val b = sorted[j]
                    val aw = a.maxX - a.minX + 1
                    val bw = b.maxX - b.minX + 1
                    val acx = (a.minX + a.maxX) * 0.5f
                    val bcx = (b.minX + b.maxX) * 0.5f

                    val horizOverlap = min(a.maxX, b.maxX) - max(a.minX, b.minX) + 1
                    val maxW = max(aw, bw).toFloat()
                    val centerDistX = abs(acx - bcx)

                    val vertGap = max(0, max(a.minY, b.minY) - min(a.maxY, b.maxY) - 1)
                    val combinedMinX = min(a.minX, b.minX)
                    val combinedMaxX = max(a.maxX, b.maxX)
                    val combinedMinY = min(a.minY, b.minY)
                    val combinedMaxY = max(a.maxY, b.maxY)
                    val combinedW = combinedMaxX - combinedMinX + 1
                    val combinedH = combinedMaxY - combinedMinY + 1
                    val combinedAspect = combinedW.toFloat() / max(1, combinedH).toFloat()

                    // Merge if vertically stacked (e.g. head + torso + legs across stair railing)
                    // or overlapping horizontally and forming a valid upright human aspect ratio
                    val maxAllowedGap = if (combinedH >= 28) 9 else 4
                    val shouldMerge =
                        (centerDistX <= maxW * 0.75f + 2f || horizOverlap >= min(aw, bw) * 0.4f) &&
                            vertGap <= maxAllowedGap &&
                            combinedAspect <= 0.64f &&
                            combinedH <= 86

                    if (shouldMerge) {
                        sorted[i] = RawCluster(
                            minX = combinedMinX,
                            maxX = combinedMaxX,
                            minY = combinedMinY,
                            maxY = combinedMaxY,
                            activePixels = a.activePixels + b.activePixels,
                            movingPixels = a.movingPixels + b.movingPixels
                        )
                        sorted.removeAt(j)
                        mergedAny = true
                        break@outer
                    }
                }
            }
        }
        return sorted
    }

    /**
     * Non-Maximum Suppression (NMS) to ensure a single person never produces 2 or 3 simultaneous boxes.
     */
    private fun suppressOverlappingDetections(detections: List<RawDetection>): List<RawDetection> {
        if (detections.size <= 1) return detections
        val sorted = detections.sortedByDescending { it.confidence * (it.bw * it.bh + 0.05f) }
        val kept = mutableListOf<RawDetection>()

        for (cand in sorted) {
            var suppressed = false
            val ccx = cand.bx + cand.bw * 0.5f
            val ccy = cand.by + cand.bh * 0.5f

            for (existing in kept) {
                val ecx = existing.bx + existing.bw * 0.5f
                val ecy = existing.by + existing.bh * 0.5f

                val interLeft = max(cand.bx, existing.bx)
                val interTop = max(cand.by, existing.by)
                val interRight = min(cand.bx + cand.bw, existing.bx + existing.bw)
                val interBottom = min(cand.by + cand.bh, existing.by + existing.bh)

                val interW = max(0f, interRight - interLeft)
                val interH = max(0f, interBottom - interTop)
                val interArea = interW * interH
                val candArea = max(0.0001f, cand.bw * cand.bh)
                val existArea = max(0.0001f, existing.bw * existing.bh)
                val unionArea = candArea + existArea - interArea

                val iou = interArea / unionArea
                val containment = interArea / min(candArea, existArea)

                val horizOverlap = interW / min(cand.bw, existing.bw)
                val vertDist = abs(ccy - ecy)
                val maxH = max(cand.bh, existing.bh)

                // If boxes significantly overlap or are vertically aligned parts of the same person, suppress
                if (
                    iou >= 0.18f ||
                    containment >= 0.45f ||
                    (horizOverlap >= 0.45f && vertDist <= maxH * 0.85f) ||
                    (maxH >= 0.28f && abs(ccx - ecx) <= max(cand.bw, existing.bw) * 0.85f && vertDist <= maxH * 0.75f)
                ) {
                    suppressed = true
                    break
                }
            }
            if (!suppressed) {
                kept.add(cand)
            }
        }
        return kept
    }

    /**
     * Evaluates whether a foreground cluster matches an upright human silhouette (far, mid, or close-up),
     * while rejecting horizontal quadrupeds (cats/dogs), cars, and boxy furniture.
     */
    private fun evaluateHumanSilhouetteAnatomy(
        fgMask: BooleanArray,
        curLuma: IntArray,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        activePixels: Int,
        isFarHorizon: Boolean
    ): Float {
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1

        // Allow distant silhouettes down to bw=1..2, bh=3..4; and close-up silhouettes up to bh=86, bw=68!
        val minW = if (isFarHorizon) 1 else 4
        val minH = if (isFarHorizon) 3 else 20
        if (bw < minW || bh < minH || bh > 87 || bw > 70) return 0f

        // 1. Vertical Upright Human Aspect Ratio (width / height):
        // Standing/walking humans (even in winter coats or carrying a bag) have aspect in 0.14..0.60.
        // Cats, dogs, cars, and couches are wider (aspect > 0.62) and are rejected!
        val aspect = bw.toFloat() / bh.toFloat()
        val maxAllowedAspect = when {
            isFarHorizon -> 0.58f
            bh >= 45 -> 0.62f // Close-up person in winter coat / holding a bag (Screenshot 5)
            else -> 0.56f
        }
        if (aspect < 0.13f || aspect > maxAllowedAspect) return 0f

        val boxArea = max(1, bw * bh)
        val fillRatio = activePixels.toFloat() / boxArea.toFloat()
        val minFill = if (bh >= 36) 0.16f else 0.22f
        val maxFill = if (isFarHorizon || bh <= 16) 1.0f else 0.96f
        if (fillRatio < minFill || fillRatio > maxFill) return 0f

        // 2. Distant Horizon Human Check (bh in 3..11 rows, e.g., people walking far away under streetlights)
        if (isFarHorizon && bh <= 11) {
            var activeRows = 0
            var contrastSum = 0
            for (y in minY..maxY) {
                var rowHas = false
                for (x in minX..maxX) {
                    if (fgMask[y * GRID_W + x]) {
                        rowHas = true
                        break
                    }
                }
                if (rowHas) activeRows++
                val midX = (minX + maxX) shr 1
                val centerLuma = curLuma[y * GRID_W + midX]
                val leftBg = curLuma[y * GRID_W + max(0, minX - 2)]
                val rightBg = curLuma[y * GRID_W + min(GRID_W - 1, maxX + 2)]
                contrastSum += max(abs(centerLuma - leftBg), abs(centerLuma - rightBg))
            }
            val avgContrast = contrastSum / max(1, bh)
            val minRowsRequired = max(2, bh - 2)
            return if (activeRows >= minRowsRequired && avgContrast >= 9) {
                0.78f
            } else {
                0f
            }
        }

        // 3. Mid-Range & Close-Up 4-Band Anatomical Check (Head - Shoulders - Torso/Hips - Legs)
        val yHeadEnd = minY + max(1, (bh * 0.24f).toInt())
        val yShoulderEnd = minY + max(2, (bh * 0.54f).toInt())
        val yHipEnd = minY + max(3, (bh * 0.78f).toInt())

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

        // Allow at least 3 of the 4 vertical zones to have active pixels (in case stair railing or dark pants
        // briefly occlude one zone), provided upper and lower halves are both present!
        val nonEmptyBands = (if (headPx > 0) 1 else 0) +
            (if (shoulderPx > 0) 1 else 0) +
            (if (hipPx > 0) 1 else 0) +
            (if (legPx > 0) 1 else 0)
        if (nonEmptyBands < 3 || (headPx + shoulderPx == 0) || (hipPx + legPx == 0)) return 0f

        // Verify vertical spine alignment for present bands
        val maxSpineDeviation = bw * 0.42f + 2f
        if (headPx > 0 && shoulderPx > 0 && abs(headCx - shoulderCx) > maxSpineDeviation) return 0f
        if (shoulderPx > 0 && hipPx > 0 && abs(shoulderCx - hipCx) > maxSpineDeviation) return 0f
        if (hipPx > 0 && legPx > 0 && abs(hipCx - legCx) > maxSpineDeviation * 1.2f) return 0f

        var score = 0.70f
        if (headPx > 0 && shoulderPx > 0 && headSpan <= shoulderSpan * 0.96f) score += 0.10f
        if (aspect in 0.22f..0.52f) score += 0.10f
        if (nonEmptyBands == 4) score += 0.06f
        if (legSpan <= max(shoulderSpan, hipSpan) * 1.05f) score += 0.04f
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

        // Merge external Room memory with our synchronous internalMemoryCache
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

        // Keep short-term scene continuity zones for 22 seconds
        recentCountZones.removeAll { now - it.timestamp > 22_000L }

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
                // Allow larger step distance for close-up pedestrians (e.g., walking up stairs)
                val maxAllowedDist = when {
                    det.descriptor.isFarHorizon -> 0.18f
                    det.bh >= 0.35f || track.bh >= 0.35f -> 0.36f
                    else -> 0.28f
                }
                if (dist > maxAllowedDist) continue

                val spatialSim = 1f - (dist / maxAllowedDist)
                val appSim = computeSimilarity(det.descriptor.vector, track.descriptor.vector)
                val combined = spatialSim * 0.62f + appSim * 0.38f
                if (combined > 0.38f && combined > bestScore) {
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
                    val minLineHits = if (isSimulation) 1 else 2
                    if (crossed && bestTrack.hits >= minLineHits && bestTrack.countedPersonId == null && bestTrack.matchedKnownId == null) {
                        registerOrDeduplicateTrack(
                            track = bestTrack,
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
                bestTrack.descriptor = blendDescriptors(bestTrack.descriptor, det.descriptor, 0.22f)
                bestTrack.isFarHorizon = det.descriptor.isFarHorizon
                bestTrack.humanConfidence = max(bestTrack.humanConfidence, det.confidence)
                bestTrack.hits += 1

                // Keep active spatial zone updated while person remains in view
                val activePersonId = bestTrack.countedPersonId ?: bestTrack.matchedKnownId
                if (activePersonId != null) {
                    recentCountZones.find { it.personId == activePersonId }?.let { zone ->
                        zone.cx = cx
                        zone.cy = cy
                        zone.bw = det.bw
                        zone.bh = det.bh
                        zone.timestamp = now
                    }
                }

                // Qualification logic for Far, Mid, and Close-Up Humans:
                // Counts people walking across the road in the distance, walking toward the camera up stairs,
                // or passing close by, without requiring them to traverse the entire width of the screen!
                val netDisplacement = hypot(cx - bestTrack.startCx, cy - bestTrack.startCy)
                val scaleChange = abs(det.bh - bestTrack.startBh)

                val qualifiesToCount = if (isSimulation) {
                    bestTrack.hits >= 2
                } else if (bestTrack.isFarHorizon) {
                    // Distant pedestrians: 3 frames + small movement along the road/ramp
                    (bestTrack.hits >= 3 && (netDisplacement >= 0.010f || bestTrack.totalPathDist >= 0.015f)) ||
                        (bestTrack.hits >= 4 && bestTrack.humanConfidence >= 0.74f)
                } else {
                    // Near & mid-range pedestrians:
                    // Either lateral movement, approaching/receding scale change (stairs!), or clear 3-frame human figure
                    (bestTrack.hits >= 3 && (netDisplacement >= 0.018f || scaleChange >= 0.018f || bestTrack.totalPathDist >= 0.024f)) ||
                        (bestTrack.hits >= 3 && bestTrack.humanConfidence >= 0.74f)
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
                // Check if this new track is a continuation of a person currently/recently in the scene
                val alreadyActiveIds = activeTracks
                    .filter { it.missedFrames <= 2 }
                    .mapNotNull { it.countedPersonId ?: it.matchedKnownId }
                    .toSet()
                val recentZoneMatch = findMatchingRecentZone(cx, cy, det.bw, det.bh, det.descriptor, now, alreadyActiveIds)
                val memMatch = findInMemory(det.descriptor, validMemory, now, alreadyActiveIds)
                val knownId = recentZoneMatch?.personId ?: memMatch?.personId

                val newTrack = ActiveTrack(
                    trackId = nextTrackSeq++,
                    startCx = cx,
                    startCy = cy,
                    startBh = det.bh,
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
        activeTracks.removeAll { it.missedFrames > 10 }

        val uiBoxes = activeTracks
            .filter { it.missedFrames <= 3 && (isSimulation || it.hits >= 2 || it.countedPersonId != null || it.matchedKnownId != null) }
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

    /**
     * Short-term scene continuity matcher: prevents the same person walking up stairs or standing in view
     * from ever being counted twice (+2/+3) if their track briefly resets or shifts.
     */
    private fun findMatchingRecentZone(
        cx: Float,
        cy: Float,
        bw: Float,
        bh: Float,
        desc: SilhouetteDescriptor,
        now: Long,
        excludeActiveIds: Set<Int>
    ): RecentCountZone? {
        for (zone in recentCountZones) {
            if (zone.personId in excludeActiveIds) continue
            val ageMs = now - zone.timestamp
            if (ageMs > 18_000L) continue
            val dist = hypot(cx - zone.cx, cy - zone.cy)
            val maxRadius = when {
                desc.isFarHorizon && zone.isFarHorizon -> 0.14f
                bh >= 0.30f || zone.bh >= 0.30f -> 0.38f // Large foreground person on stairs / close-up
                else -> 0.24f
            }
            if (dist <= maxRadius) {
                val sim = computeBiometricAndClothingSimilarity(desc, zone.descriptor)
                // Within the same spatial zone during the first 6 seconds, or with matching appearance up to 18s
                if ((ageMs < 5_000L && dist <= maxRadius * 0.75f) || sim >= 0.68f) {
                    zone.cx = cx
                    zone.cy = cy
                    zone.bw = bw
                    zone.bh = bh
                    zone.timestamp = now
                    return zone
                }
            }
        }
        return null
    }

    private fun registerOrDeduplicateTrack(
        track: ActiveTrack,
        cx: Float,
        cy: Float,
        now: Long,
        validMemory: MutableList<SilhouetteMemoryEntity>,
        newlyCounted: MutableList<SilhouetteMemoryEntity>,
        updatedRepeats: MutableList<SilhouetteMemoryEntity>,
        nextPersonIdProvider: () -> Int
    ) {
        val alreadyActiveIds = activeTracks
            .filter { it != track && it.missedFrames <= 2 }
            .mapNotNull { it.countedPersonId ?: it.matchedKnownId }
            .toSet()

        // 1. Check short-term scene continuity lock first (prevents +3 surges on a single person!)
        val zoneHit = findMatchingRecentZone(cx, cy, track.bw, track.bh, track.descriptor, now, alreadyActiveIds)
        if (zoneHit != null) {
            track.matchedKnownId = zoneHit.personId
            track.isRepeatNotified = true
            return
        }

        // 2. Check 90-minute Re-ID memory bank (distinguishes different people in similar clothes vs true repeats)
        val match = findInMemory(track.descriptor, validMemory, now, alreadyActiveIds)
        if (match != null) {
            track.matchedKnownId = match.personId
            recentCountZones.add(
                RecentCountZone(
                    personId = match.personId,
                    cx = cx,
                    cy = cy,
                    bw = track.bw,
                    bh = track.bh,
                    isFarHorizon = track.isFarHorizon,
                    descriptor = track.descriptor,
                    timestamp = now
                )
            )
            if (!track.isRepeatNotified) {
                track.isRepeatNotified = true
                // Only increment repeat counter if the person re-entered after at least 15 seconds
                // (so brief track re-acquisition while walking doesn't inflate repeat count)
                if (now - match.lastSeenTs >= 15_000L) {
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
                    bw = track.bw,
                    bh = track.bh,
                    isFarHorizon = track.isFarHorizon,
                    descriptor = track.descriptor,
                    timestamp = now
                )
            )
            newlyCounted.add(created)
        }
    }

    /**
     * Extracts a 64-dimensional 5-Zone Biometric Figure + Multi-Part Clothing Descriptor:
     * - Zone 1 (0..18%): Head / Hair / Hood / Hat
     * - Zone 2 (18..38%): Shoulders / Chest / Scarf / Collar
     * - Zone 3 (38..58%): Mid-Torso / Waist / Carried Bag (e.g. orange bag) / Hands
     * - Zone 4 (58..78%): Hips / Coat Hem / Upper Legs
     * - Zone 5 (78..100%): Lower Legs / Footwear
     * Plus body figure geometry (aspectRatio, shoulderToHipRatio, headToShoulderRatio, accessory accent).
     * This ensures two different people in dark jackets are NOT confused, while the same person is remembered!
     */
    private fun extractDescriptorFromRgbGrid(
        rGrid: IntArray,
        gGrid: IntArray,
        bGrid: IntArray,
        curLuma: IntArray,
        fgMask: BooleanArray,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        isFarHorizon: Boolean
    ): SilhouetteDescriptor {
        val vec = FloatArray(DESCRIPTOR_SIZE)
        val bh = max(1, maxY - minY + 1)
        val bw = max(1, maxX - minX + 1)

        val z1End = minY + (bh * 0.18f).toInt()
        val z2End = minY + (bh * 0.38f).toInt()
        val z3End = minY + (bh * 0.58f).toInt()
        val z4End = minY + (bh * 0.78f).toInt()

        var uR = 0; var uG = 0; var uB = 0; var uCount = 0
        var mR = 0; var mG = 0; var mB = 0; var mCount = 0
        var lR = 0; var lG = 0; var lB = 0; var lCount = 0
        var headLumaSum = 0; var headCount = 0
        var shoeLumaSum = 0; var shoeCount = 0

        var shoulderWidthSum = 0f; var shoulderRows = 0
        var hipWidthSum = 0f; var hipRows = 0

        for (y in minY..maxY) {
            var rowFirstX = -1
            var rowLastX = -1
            for (x in minX..maxX) {
                val idx = y * GRID_W + x
                // Sample foreground pixels (or central torso spine if fgMask is sparse in uniform dark clothing)
                val isCenterSpine = x in (minX + bw / 4)..(maxX - bw / 4)
                if (!fgMask[idx] && !isCenterSpine) continue

                if (rowFirstX == -1) rowFirstX = x
                rowLastX = x

                val r = rGrid[idx].coerceIn(0, 255)
                val g = gGrid[idx].coerceIn(0, 255)
                val b = bGrid[idx].coerceIn(0, 255)
                val luma = curLuma[idx].coerceIn(0, 255)

                when {
                    y <= z1End -> {
                        headLumaSum += luma
                        headCount++
                        vec[min(3, r shr 6)] += 1f
                        vec[4 + min(3, g shr 6)] += 1f
                        vec[8 + min(3, b shr 6)] += 1f
                    }
                    y <= z2End -> {
                        uR += r; uG += g; uB += b; uCount++
                        vec[12 + min(3, r shr 6)] += 1f
                        vec[16 + min(3, g shr 6)] += 1f
                        vec[20 + min(3, b shr 6)] += 1f
                    }
                    y <= z3End -> {
                        mR += r; mG += g; mB += b; mCount++
                        uR += r; uG += g; uB += b; uCount++
                        vec[24 + min(3, r shr 6)] += 1f
                        vec[28 + min(3, g shr 6)] += 1f
                        vec[32 + min(3, b shr 6)] += 1f
                    }
                    y <= z4End -> {
                        lR += r; lG += g; lB += b; lCount++
                        vec[36 + min(3, r shr 6)] += 1f
                        vec[40 + min(3, g shr 6)] += 1f
                        vec[44 + min(3, b shr 6)] += 1f
                    }
                    else -> {
                        shoeLumaSum += luma
                        shoeCount++
                        lR += r; lG += g; lB += b; lCount++
                        vec[48 + min(3, r shr 6)] += 1f
                        vec[52 + min(3, g shr 6)] += 1f
                    }
                }
            }
            if (rowFirstX != -1) {
                val span = (rowLastX - rowFirstX + 1).toFloat()
                if (y in (z1End + 1)..z2End) {
                    shoulderWidthSum += span
                    shoulderRows++
                } else if (y in (z3End + 1)..z4End) {
                    hipWidthSum += span
                    hipRows++
                }
            }
        }

        if (headCount > 0) for (i in 0 until 12) vec[i] /= headCount.toFloat()
        if (uCount > 0) {
            for (i in 12 until 24) vec[i] /= uCount.toFloat()
            uR /= uCount; uG /= uCount; uB /= uCount
        }
        if (mCount > 0) {
            for (i in 24 until 36) vec[i] /= mCount.toFloat()
            mR /= mCount; mG /= mCount; mB /= mCount
        } else {
            mR = uR; mG = uG; mB = uB
        }
        if (lCount > 0) {
            for (i in 36 until 56) vec[i] /= lCount.toFloat()
            lR /= lCount; lG /= lCount; lB /= lCount
        } else {
            lR = uR; lG = uG; lB = uB
        }

        val headLuma = if (headCount > 0) headLumaSum / headCount else 96
        val shoeLuma = if (shoeCount > 0) shoeLumaSum / shoeCount else 64
        val aspect = (bw.toFloat() / bh.toFloat()).coerceIn(0.14f, 0.75f)
        val avgShoulder = if (shoulderRows > 0) shoulderWidthSum / shoulderRows else bw * 0.75f
        val avgHip = if (hipRows > 0) hipWidthSum / hipRows else bw * 0.68f
        val shoulderToHip = (avgShoulder / max(1f, avgHip)).coerceIn(0.65f, 1.65f)

        // Encode biometric figure geometry + 5-zone color accents in slots 56..63
        vec[56] = headLuma / 255f
        vec[57] = shoeLuma / 255f
        vec[58] = mR / 255f
        vec[59] = mG / 255f
        vec[60] = mB / 255f
        vec[61] = aspect * 1.4f
        vec[62] = (shoulderToHip - 0.65f)
        vec[63] = if (isFarHorizon) 0.75f else 0.35f

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
            midAccentR = mR,
            midAccentG = mG,
            midAccentB = mB,
            headLuma = headLuma,
            shoeLuma = shoeLuma,
            aspectRatio = aspect,
            shoulderToHipRatio = shoulderToHip,
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
        vec[(seed * 7) % 56] = 1.0f
        vec[(seed * 13 + 5) % 56] = 0.82f
        vec[61] = aspect * 1.4f
        vec[62] = 0.45f
        vec[63] = if (isFarHorizon) 0.75f else 0.35f
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
            midAccentR = uR,
            midAccentG = uG,
            midAccentB = uB,
            headLuma = 110,
            shoeLuma = 60,
            aspectRatio = aspect,
            shoulderToHipRatio = 1.1f,
            isFarHorizon = isFarHorizon
        )
    }

    private fun computeBiometricAndClothingSimilarity(
        a: SilhouetteDescriptor,
        b: SilhouetteDescriptor
    ): Float {
        val vecSim = computeSimilarity(a.vector, b.vector)
        val upperDiff = (abs(a.upperR - b.upperR) + abs(a.upperG - b.upperG) + abs(a.upperB - b.upperB)) / 3f
        val lowerDiff = (abs(a.lowerR - b.lowerR) + abs(a.lowerG - b.lowerG) + abs(a.lowerB - b.lowerB)) / 3f
        val midDiff = (abs(a.midAccentR - b.midAccentR) + abs(a.midAccentG - b.midAccentG) + abs(a.midAccentB - b.midAccentB)) / 3f
        val colorSim = (1f - ((upperDiff * 0.4f + lowerDiff * 0.35f + midDiff * 0.25f) / 140f)).coerceIn(0f, 1f)
        val figureDiff = abs(a.aspectRatio - b.aspectRatio) * 2.5f + abs(a.shoulderToHipRatio - b.shoulderToHipRatio) * 0.8f
        val figureSim = (1f - figureDiff).coerceIn(0f, 1f)
        return (vecSim * 0.45f + colorSim * 0.35f + figureSim * 0.20f).coerceIn(0f, 1f)
    }

    /**
     * Searches the 90-minute Re-ID memory bank.
     * Uses a strict multi-feature threshold (clothing zones + body figure aspect ratio) so two different
     * pedestrians wearing similar dark jackets are NOT confused, while the same person is recognized.
     */
    private fun findInMemory(
        desc: SilhouetteDescriptor,
        memory: List<SilhouetteMemoryEntity>,
        now: Long,
        excludeActiveIds: Set<Int> = emptySet()
    ): SilhouetteMemoryEntity? {
        var best: SilhouetteMemoryEntity? = null
        var bestScore = 0f

        for (entry in memory) {
            if (entry.personId in excludeActiveIds) continue
            val memVec = parseCsvVector(entry.descriptorCsv)
            val vecSim = computeSimilarity(desc.vector, memVec)

            val (muR, muG, muB) = hexToRgb(entry.upperHex)
            val (mlR, mlG, mlB) = hexToRgb(entry.lowerHex)
            val upperDiff = (abs(desc.upperR - muR) + abs(desc.upperG - muG) + abs(desc.upperB - muB)) / 3f
            val lowerDiff = (abs(desc.lowerR - mlR) + abs(desc.lowerG - mlG) + abs(desc.lowerB - mlB)) / 3f
            val colorSim = (1f - ((upperDiff + lowerDiff) / 150f)).coerceIn(0f, 1f)

            // Compare body build (aspectRatio) so a slim person and a broad person in dark clothes are never confused!
            val aspectDiff = abs(desc.aspectRatio - entry.aspectRatio)
            val figureSim = (1f - (aspectDiff / 0.16f)).coerceIn(0f, 1f)

            // Also check mid-torso/bag accent encoded in vector[58..60] if both vectors have 64 elements
            val midAccentDiff = abs(desc.vector[58] - memVec[58]) +
                abs(desc.vector[59] - memVec[59]) +
                abs(desc.vector[60] - memVec[60])
            val accentPenalty = if (midAccentDiff > 0.14f) 0.12f else 0f

            val combined = (vecSim * 0.50f + colorSim * 0.30f + figureSim * 0.20f) - accentPenalty

            // If seen within the last 15 seconds, allow slightly more forgiving match (0.80f) for continuity;
            // After 15 seconds, require strict match (0.87f) + close body figure proportions (aspectDiff <= 0.065f)
            // so another pedestrian in similar dark clothes is counted as a NEW person (+1)!
            val ageMs = now - entry.lastSeenTs
            val requiredThreshold = if (ageMs < 15_000L) 0.80f else 0.87f
            val figureCompatible = if (ageMs < 15_000L) aspectDiff <= 0.10f else aspectDiff <= 0.065f

            if (figureCompatible && combined >= requiredThreshold && combined > bestScore) {
                bestScore = combined
                best = entry
            }
        }
        return best
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
        val len = min(a.size, b.size)
        if (len == 0) return 0f
        var dot = 0f
        for (i in 0 until len) dot += a[i] * b[i]
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
        val mR = (oldDesc.midAccentR * (1f - alpha) + newDesc.midAccentR * alpha).roundToInt()
        val mG = (oldDesc.midAccentG * (1f - alpha) + newDesc.midAccentG * alpha).roundToInt()
        val mB = (oldDesc.midAccentB * (1f - alpha) + newDesc.midAccentB * alpha).roundToInt()
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
            midAccentR = mR,
            midAccentG = mG,
            midAccentB = mB,
            headLuma = (oldDesc.headLuma * (1f - alpha) + newDesc.headLuma * alpha).roundToInt(),
            shoeLuma = (oldDesc.shoeLuma * (1f - alpha) + newDesc.shoeLuma * alpha).roundToInt(),
            aspectRatio = oldDesc.aspectRatio * (1f - alpha) + newDesc.aspectRatio * alpha,
            shoulderToHipRatio = oldDesc.shoulderToHipRatio * (1f - alpha) + newDesc.shoulderToHipRatio * alpha,
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
