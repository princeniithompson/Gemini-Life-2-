package com.example.model

/**
 * Log levels used throughout the Gemini Live Diagnostic pipeline.
 */
enum class LogLevel(val label: String) {
    INFO("INFO"),
    SUCCESS("SUCCESS"),
    ERROR("ERROR"),
    WARN("WARN"),
    DEBUG("DEBUG")
}

/**
 * Represents a single timestamped entry in the diagnostic log.
 *
 * @param timestamp Formatted time string, e.g., "12:30:18"
 * @param level The log severity/category level
 * @param message Main descriptive message for the log line
 * @param details Optional extended diagnostic details, exception type, cause, or full stack trace
 */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String,
    val details: String? = null
) {
    /**
     * Formats the log entry into the standard output line string required:
     * e.g., "12:30:18 INFO SDK initialized"
     */
    fun toFormattedLine(): String {
        val base = "$timestamp ${level.label} $message"
        return if (details.isNullOrBlank()) {
            base
        } else {
            "$base\n    [DETAILS]\n${details.prependIndent("    ")}"
        }
    }
}
