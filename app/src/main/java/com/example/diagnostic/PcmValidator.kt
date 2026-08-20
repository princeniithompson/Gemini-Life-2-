package com.example.diagnostic

/**
 * Result of validating a PCM audio frame.
 *
 * @param isValid True if frame passes all structural and alignment checks
 * @param sampleRate Expected or detected sample rate in Hz (default: 24000 Hz)
 * @param bitDepth Expected or detected bit depth (default: 16-bit)
 * @param channels Channel count (default: 1 mono)
 * @param sampleCount Number of complete 16-bit PCM samples in this frame
 * @param byteCount Size of frame in bytes
 * @param warnings List of validation warnings generated for this frame
 * @param isTruncated True if byte count is odd (incomplete 16-bit sample)
 * @param isEmpty True if byte count is 0
 */
data class ValidationResult(
    val isValid: Boolean,
    val sampleRate: Int = 24000,
    val bitDepth: Int = 16,
    val channels: Int = 1,
    val sampleCount: Int,
    val byteCount: Int,
    val warnings: List<String>,
    val isTruncated: Boolean,
    val isEmpty: Boolean
)

/**
 * Validates incoming PCM audio frames for Gemini Live audio streams.
 *
 * Checks:
 * - Sample rate (24,000 Hz)
 * - Bit depth (16-bit Linear PCM = 2 bytes per sample)
 * - Channel count (Mono = 1 channel)
 * - Sample alignment (byte count must be divisible by 2)
 * - Empty frames (byte count == 0)
 * - Truncated frames (incomplete sample bytes at frame end)
 */
class PcmValidator(
    val targetSampleRate: Int = 24000,
    val targetBitDepth: Int = 16,
    val targetChannels: Int = 1
) {
    val bytesPerSample: Int = (targetBitDepth / 8) * targetChannels // 2 bytes for 16-bit mono

    /**
     * Validates a raw PCM byte array frame.
     */
    fun validateFrame(byteArray: ByteArray, mimeType: String = "audio/pcm;rate=24000"): ValidationResult {
        val warnings = mutableListOf<String>()
        val byteCount = byteArray.size

        if (byteCount == 0) {
            warnings.add("Empty PCM frame received (0 bytes)")
            return ValidationResult(
                isValid = false,
                sampleRate = targetSampleRate,
                bitDepth = targetBitDepth,
                channels = targetChannels,
                sampleCount = 0,
                byteCount = 0,
                warnings = warnings,
                isTruncated = false,
                isEmpty = true
            )
        }

        val isTruncated = byteCount % bytesPerSample != 0
        if (isTruncated) {
            warnings.add("Truncated PCM frame: $byteCount bytes is not divisible by $bytesPerSample (incomplete 16-bit sample at frame boundary)")
        }

        // Validate sample rate from MIME type if provided
        if (mimeType.contains("rate=")) {
            val rateStr = mimeType.substringAfter("rate=").substringBefore(";").trim()
            val parsedRate = rateStr.toIntOrNull()
            if (parsedRate != null && parsedRate != targetSampleRate) {
                warnings.add("Sample rate mismatch: MIME type indicates ${parsedRate}Hz, expected ${targetSampleRate}Hz")
            }
        }

        val sampleCount = byteCount / bytesPerSample
        val isValid = !isTruncated && warnings.isEmpty()

        return ValidationResult(
            isValid = isValid,
            sampleRate = targetSampleRate,
            bitDepth = targetBitDepth,
            channels = targetChannels,
            sampleCount = sampleCount,
            byteCount = byteCount,
            warnings = warnings,
            isTruncated = isTruncated,
            isEmpty = false
        )
    }
}
