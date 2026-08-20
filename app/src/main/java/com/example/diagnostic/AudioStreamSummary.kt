package com.example.diagnostic

import java.util.Locale

/**
 * Encapsulates calculation and formatting of the Audio Stream Summary report.
 */
object AudioStreamSummaryBuilder {

    /**
     * Calculates Integrity Score (0 to 100) based on stream inspector and buffer metrics.
     */
    fun calculateIntegrityScore(
        inspector: AudioStreamInspector,
        bufferAnalyzer: AudioBufferAnalyzer
    ): Int {
        var score = 100

        // Deductions for audio stream anomalies
        score -= inspector.getMalformedFrames() * 15
        score -= inspector.getDuplicateFrames() * 5
        score -= inspector.getMissingFrames() * 10
        score -= inspector.getOutOfOrderFrames() * 10

        // Deductions for buffer anomalies
        score -= bufferAnalyzer.getUnderflowCount() * 10
        score -= bufferAnalyzer.getOverflowCount() * 10
        score -= bufferAnalyzer.getStarvationRiskCount() * 5

        return score.coerceIn(0, 100)
    }

    /**
     * Determines overall status text based on integrity score.
     */
    fun getOverallStatus(score: Int): String {
        return when {
            score == 100 -> "PASS (100% - Production Quality)"
            score >= 85 -> "PASS WITH MINOR WARNINGS ($score/100)"
            score >= 60 -> "NEEDS ATTENTION ($score/100)"
            else -> "CRITICAL FAIL ($score/100)"
        }
    }

    /**
     * Formats bytes into human readable KB or MB.
     */
    private fun formatByteSize(bytes: Long): String {
        return when {
            bytes >= 1_048_576 -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }

    /**
     * Builds the required ======== AUDIO STREAM SUMMARY ======== log block.
     */
    fun buildSummaryString(
        inspector: AudioStreamInspector,
        bufferAnalyzer: AudioBufferAnalyzer
    ): String {
        val totalFrames = inspector.getTotalFrames()
        val duplicateFrames = inspector.getDuplicateFrames()
        val missingFrames = inspector.getMissingFrames()
        val outOfOrderFrames = inspector.getOutOfOrderFrames()
        val pcmBytes = inspector.getTotalPcmBytes()
        val formattedPcmBytes = formatByteSize(pcmBytes)

        val durationMs = inspector.getTotalDurationMs().toLong()
        val durationSec = String.format(Locale.US, "%.2f", inspector.getTotalDurationSeconds())

        val avgFrameSize = String.format(Locale.US, "%.1f", inspector.getAverageFrameSizeBytes())
        val avgFrameInterval = String.format(Locale.US, "%.1f", inspector.getAverageFrameIntervalMs())

        val peakBufferBytes = bufferAnalyzer.getPeakBufferBytes()
        val peakBufferMs = (peakBufferBytes / 48.0).toInt()

        val bitrate = String.format(Locale.US, "%.1f", inspector.getStreamBitrateKbps())

        val score = calculateIntegrityScore(inspector, bufferAnalyzer)
        val overallStatus = getOverallStatus(score)

        return buildString {
            append("\n======== AUDIO STREAM SUMMARY ========\n")
            append("Total Frames: $totalFrames\n")
            append("Duplicate Frames: $duplicateFrames\n")
            append("Missing Frames: $missingFrames\n")
            append("Out-of-Order Frames: $outOfOrderFrames\n")
            append("PCM Bytes: $pcmBytes bytes ($formattedPcmBytes)\n")
            append("Duration: ${durationSec}s ($durationMs ms)\n")
            append("Average Frame Size: $avgFrameSize bytes\n")
            append("Average Frame Interval: $avgFrameInterval ms\n")
            append("Maximum Buffer Size: $peakBufferBytes bytes ($peakBufferMs ms)\n")
            append("Average Throughput: $bitrate kbps\n")
            append("Detected Sample Rate: 24000 Hz\n")
            append("Detected Encoding: PCM 16-bit Mono (audio/pcm;rate=24000)\n")
            append("Integrity Score (0-100): $score/100\n")
            append("Overall Status: $overallStatus\n")
            append("======================================")
        }
    }
}
