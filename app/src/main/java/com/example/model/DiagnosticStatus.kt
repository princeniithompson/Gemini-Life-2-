package com.example.model

/**
 * Represents the execution state of a specific pipeline step.
 */
enum class StatusState(val symbol: String, val description: String) {
    NOT_STARTED("⚪", "Not Started"),
    IN_PROGRESS("🟡", "In Progress"),
    SUCCESS("🟢", "Success"),
    FAILED("🔴", "Failed")
}

/**
 * Encapsulates the status of each individual step in the Gemini Live API pipeline.
 */
data class PipelineStatusState(
    val sdkStatus: StatusState = StatusState.NOT_STARTED,
    val apiKeyStatus: StatusState = StatusState.NOT_STARTED,
    val liveClientStatus: StatusState = StatusState.NOT_STARTED,
    val webSocketStatus: StatusState = StatusState.NOT_STARTED,
    val sessionStatus: StatusState = StatusState.NOT_STARTED,
    val readyStatus: StatusState = StatusState.NOT_STARTED
) {
    fun reset(): PipelineStatusState = PipelineStatusState()
}
