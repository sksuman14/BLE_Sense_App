package com.blesense.app.model.pedometer

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * StepResult contains real-time pedometer metrics and telemetry
 * returned by [StepCounter.addSample].
 */
data class StepResult(
    val totalSteps: Int,
    val isWalking: Boolean,
    val cadenceSpm: Int,            // Steps per minute
    val distanceMeters: Float,      // Estimated distance in meters (~0.75m / step)
    val caloriesKcal: Float,        // Estimated calories burned (~0.04 kcal / step)
    val currentMagnitude: Float,    // Acceleration magnitude: √(X² + Y² + Z²) in m/s²
    val currentMotion: Float,       // Dynamic AC motion component: magnitude - dynamic baseline
    val dynamicThreshold: Float,    // Adaptive peak detection threshold
    val lastStepTimeMs: Long,
    val sampleTimestampMs: Long
)

/**
 * High-accuracy, pocket-optimized pedometer algorithm for LIS3DH accelerometer.
 *
 * Engineered with:
 * 1. 3D Acceleration Magnitude (√(X² + Y² + Z²)) for complete orientation independence.
 * 2. Adaptive low-pass baseline gravity removal that won't absorb walking impacts.
 * 3. Hysteresis zero-crossing peak detector: cannot get trapped in deadlocks.
 * 4. Dual-condition step confirmation: triggers on valley return (<= 0.10 m/s²) OR a 60% crest drop.
 * 5. Refractory debounce window (260 ms) to prevent false harmonic bounces.
 * 6. Dynamic cadence calculation and automatic idle detection (> 2.5s).
 */
class StepCounter(
    private val minStepIntervalMs: Long = 260L,     // Debounce refractory limit (max ~230 SPM)
    private val idleTimeoutMs: Long = 2500L         // 2.5s without steps -> Idle
) {
    // ── Metrics ──────────────────────────────────────────────────────────────
    var totalSteps: Int = 0
        private set

    var isWalking: Boolean = false
        private set

    var cadenceSpm: Int = 0
        private set

    var isPaused: Boolean = false

    // ── Signal Processing State ──────────────────────────────────────────────
    private var lastSampleTimestamp: Long = 0L
    private var baseline: Float = 0f
    private var smoothedMotion: Float = 0f

    // ── Hysteresis Peak-Valley State ──────────────────────────────────────────
    private var isAbovePeak: Boolean = false
    private var peakValue: Float = 0f
    private var peakTimeMs: Long = 0L
    private var lastStepTimeMs: Long = 0L

    var latestResult: StepResult? = null
        private set

    /**
     * Process an incoming LIS3DH sample.
     *
     * @param timestampMs Sample timestamp in milliseconds.
     * @param x Acceleration along X axis (m/s²).
     * @param y Acceleration along Y axis (m/s²).
     * @param z Acceleration along Z axis (m/s²).
     * @return Updated [StepResult] containing current steps and telemetry.
     */
    @Synchronized
    fun addSample(timestampMs: Long, x: Float, y: Float, z: Float): StepResult {
        // 1. Calculate orientation-invariant 3D magnitude
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (isPaused) {
            val res = buildResult(magnitude, 0f, 0.45f, timestampMs)
            latestResult = res
            return res
        }

        // 2. Compute sample time interval dt
        val dt = if (lastSampleTimestamp != 0L) (timestampMs - lastSampleTimestamp).coerceAtLeast(10L) else 50L
        lastSampleTimestamp = timestampMs

        // 3. Dynamic Gravity Baseline Removal
        // Re-center if first sample or after long connection pause (> 3 seconds)
        if (baseline == 0f || dt > 3000L) {
            baseline = magnitude
            smoothedMotion = 0f
            isAbovePeak = false
            peakValue = 0f
        } else {
            // Smooth baseline tracking gravity: slow adaptation (~2-3s time constant)
            val alpha = 0.94f
            baseline = alpha * baseline + (1f - alpha) * magnitude
        }

        val rawMotion = magnitude - baseline

        // 4. Smooth motion slightly to remove sensor white noise
        smoothedMotion = 0.60f * smoothedMotion + 0.40f * rawMotion

        // 5. Pocket Walking Thresholds
        // In a pocket, normal walking generates AC swings of +/- 0.8 to +/- 3.5 m/s².
        // Upper threshold of 0.45 m/s² captures gentle walking while ignoring stationary noise.
        val upperThreshold = 0.45f
        val lowerThreshold = 0.10f

        // 6. Robust Hysteresis Peak-to-Valley Detection
        if (!isAbovePeak) {
            // Waiting for motion to crest above upper threshold
            if (smoothedMotion > upperThreshold) {
                isAbovePeak = true
                peakValue = smoothedMotion
                peakTimeMs = timestampMs
            }
        } else {
            // Currently tracking the step crest
            if (smoothedMotion > peakValue) {
                peakValue = smoothedMotion
            }

            // Step confirms when motion falls back down toward baseline (<= 0.10 m/s²)
            // OR falls by at least 50% from its crest:
            val hasFallen = (smoothedMotion <= lowerThreshold) || (smoothedMotion <= peakValue * 0.45f)

            if (hasFallen) {
                val peakProminence = peakValue - smoothedMotion
                val timeSinceLast = if (lastStepTimeMs > 0L) timestampMs - lastStepTimeMs else minStepIntervalMs

                // Confirm step if:
                // 1. Peak prominence >= 0.35 m/s² (validates real foot impact, not micro-jitter)
                // 2. Refractory debounce >= 260 ms (prevents double-counting a single footfall bounce)
                if (peakProminence >= 0.35f && timeSinceLast >= minStepIntervalMs) {
                    totalSteps++
                    lastStepTimeMs = timestampMs
                    isWalking = true

                    if (timeSinceLast in minStepIntervalMs..2500L) {
                        updateCadence(timeSinceLast)
                    }
                }
                isAbovePeak = false // Immediately ready for next step cycle
            } else if (timestampMs - peakTimeMs > 2000L) {
                // Safety timeout if acceleration stayed high for 2 seconds (e.g. car acceleration)
                isAbovePeak = false
            }
        }

        // 7. Check Idle Timeout (> 2.5s without steps -> Idle)
        if (lastStepTimeMs > 0L && (timestampMs - lastStepTimeMs > idleTimeoutMs)) {
            isWalking = false
            cadenceSpm = 0
        }

        val result = buildResult(magnitude, smoothedMotion, upperThreshold, timestampMs)
        latestResult = result
        return result
    }

    /**
     * Smoothly update cadence (steps per minute).
     */
    private fun updateCadence(intervalMs: Long) {
        if (intervalMs <= 0L) return
        val rawCadence = (60000.0 / intervalMs).toInt().coerceIn(40, 220)
        cadenceSpm = if (cadenceSpm == 0) {
            rawCadence
        } else {
            (0.65f * cadenceSpm + 0.35f * rawCadence).toInt()
        }
    }

    /**
     * Resets all step counts, distance, calories, and algorithm state.
     */
    @Synchronized
    fun reset() {
        totalSteps = 0
        isWalking = false
        cadenceSpm = 0
        baseline = 0f
        smoothedMotion = 0f
        isAbovePeak = false
        peakValue = 0f
        peakTimeMs = 0L
        lastSampleTimestamp = 0L
        lastStepTimeMs = 0L

        latestResult = buildResult(0f, 0f, 0.45f, System.currentTimeMillis())
    }

    private fun buildResult(
        magnitude: Float,
        motion: Float,
        threshold: Float,
        timestampMs: Long
    ): StepResult {
        return StepResult(
            totalSteps = totalSteps,
            isWalking = isWalking,
            cadenceSpm = cadenceSpm,
            distanceMeters = totalSteps * 0.75f,
            caloriesKcal = totalSteps * 0.04f,
            currentMagnitude = magnitude,
            currentMotion = motion,
            dynamicThreshold = threshold,
            lastStepTimeMs = lastStepTimeMs,
            sampleTimestampMs = timestampMs
        )
    }
}
