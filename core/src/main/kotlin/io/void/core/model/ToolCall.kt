package io.void.core.model

data class ToolCall(
 val callId: String,
 val toolId: String,
 val inputs: Map<String, Any>,
 val caller: String,
 val permissions: List<String> = emptyList()
)
