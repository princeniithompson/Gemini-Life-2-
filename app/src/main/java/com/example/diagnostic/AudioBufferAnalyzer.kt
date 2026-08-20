package com.example.diagnostic

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic analysis report generated when an audio chunk is placed into the virtual buffer.
 */
data class BufferAnalysisReport(
    val currentBufferBytes: Long,
    val peakBufferBytes: Long,
    val bufferDurationMs: Double,
    val isUnderflowDetected: Boolean,
    val isOverflowDetected: Boolean,
    val isStarvationRisk: Boolean,
    val latencyMs: Long,
    val warnings: List<String>
)

/**
 * Thread-safe Virtual Audio Buffer Analyzer.
 *
 * Simulates a real PCM audio playback engine buffer (24,000 Hz, 16-bit Mono = 48 bytes/ms).
 * Tracks:
 * - Current buffer byte level
 * - Peak buffer size
 * - Buffer underflows & overflows
 * - Playback starvation risks
 * - Latency between frame reception and buffering
 */
class AudioBufferAnalyzer(
    val bytesPerMs: Double = 48.0, // 24000 Hz * 2 bytes = 48,000 bytes/sec = 48 bytes/ms
    val maxBufferBytesThreshold: Long = 256_000L, // ~5.3 seconds max buffer threshold
    val starvationThresholdMs: Double = 20.0 // Starvation risk if buffer drops below 20ms
) {
    private val currentBufferBytes = AtomicLong(0L)
    private val peakBufferBytes = AtomicLong(0L)
    private val totalReceivedBytes = AtomicLong(0L)

    private val underflowCount = AtomicInteger(0)
    private val overflowCount = AtomicInteger(0)
    private val starvationRiskCount = AtomicInteger(0)

    private val playbackStartTimeMs = AtomicLong(0L)
    private val latencies = ConcurrentLinkedQueue<Long>()

    private val lock = Any()

    fun reset() {
        synchronized(lock) {
            currentBufferBytes.set(0L)
            peakBufferBytes.set(0L)
            totalReceivedBytes.set(0L)
            underflowCount.set(0)
            overflowCount.set(0)
            starvationRiskCount.set(0)
            playbackStartTimeMs.set(0L)
            latencies.clear()
        }
    }

    /**
     * Registers a new audio frame in the virtual/actual playback buffer and calculates state metrics.
     *
     * @param byteSize Size of incoming PCM chunk in bytes
     * @param receptionTimeMs System time when frame was received
     * @param nowMs System time when buffer processing started
     * @param actualBufferedBytes Optional actual buffer level (queue + AudioTrack buffer) from playback engine
     */
    fun processFrameBuffer(
        byteSize: Int,
        receptionTimeMs: Long,
        nowMs: Long,
        actualBufferedBytes: Long? = null
    ): BufferAnalysisReport {
        val warnings = mutableListOf<String>()
        val latency = (nowMs - receptionTimeMs).coerceAtLeast(0L)
        latencies.add(latency)

        var underflow = false
        var overflow = false
        var starvation = false

        var bufferBytes: Long
        var peakBytes: Long

        synchronized(lock) {
            val totalRx = totalReceivedBytes.addAndGet(byteSize.toLong())

            if (playbackStartTimeMs.get() == 0L) {
                playbackStartTimeMs.set(nowMs)
            }

            if (actualBufferedBytes != null) {
                bufferBytes = actualBufferedBytes
            } else {
                val elapsedPlaybackMs = (nowMs - playbackStartTimeMs.get()).coerceAtLeast(0L)
                val consumedBytes = (elapsedPlaybackMs * bytesPerMs).toLong().coerceAtMost(totalRx)
                bufferBytes = (totalRx - consumedBytes).coerceAtLeast(0L)
            }
            currentBufferBytes.set(bufferBytes)

            peakBytes = peakBufferBytes.get()
            if (bufferBytes > peakBytes) {
                peakBufferBytes.set(bufferBytes)
                peakBytes = bufferBytes
            }

            val bufferDurationMs = bufferBytes / bytesPerMs

            // 1. Underflow Check (Virtual queue disabled in Direct Streaming mode)
            if (bufferBytes == 0L && totalRx > byteSize) {
                underflow = true
                underflowCount.incrementAndGet()
            }

            // 2. Overflow Check
            if (bufferBytes > maxBufferBytesThreshold) {
                overflow = true
                overflowCount.incrementAndGet()
                warnings.add("Buffer Overflow Warning: Current buffer (${bufferBytes}B / ${bufferDurationMs.toInt()}ms) exceeds threshold (${maxBufferBytesThreshold}B)")
            }

            // 3. Playback Starvation Risk Check
            if (bufferDurationMs < starvationThresholdMs && totalRx > byteSize) {
                starvation = true
                starvationRiskCount.incrementAndGet()
            }
        }

        val finalBufferDuration = bufferBytes / bytesPerMs

        return BufferAnalysisReport(
            currentBufferBytes = bufferBytes,
            peakBufferBytes = peakBytes,
            bufferDurationMs = finalBufferDuration,
            isUnderflowDetected = underflow,
            isOverflowDetected = overflow,
            isStarvationRisk = starvation,
            latencyMs = latency,
            warnings = warnings
        )
    }

    // Getters for Thread-Safe Statistics
    fun getCurrentBufferBytes(): Long = currentBufferBytes.get()
    fun getPeakBufferBytes(): Long = peakBufferBytes.get()
    fun getUnderflowCount(): Int = underflowCount.get()
    fun getOverflowCount(): Int = overflowCount.get()
    fun getStarvationRiskCount(): Int = starvationRiskCount.get()

    fun getAverageLatencyMs(): Double {
        val size = latencies.size
        if (size == 0) return 0.0
        val sum = latencies.sumOf { it }
        return sum.toDouble() / size
    }
}
