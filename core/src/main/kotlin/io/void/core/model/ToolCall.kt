package io.void.core.model

data class ToolCall(
 val callId: String,
 val toolId: String,
 val inputs: Map<String, Any>,
 val caller: String,
 val permissions: List<String> = emptyList()
) {
 init {
  require(callId.isNotBlank()) { "callId must not be blank" }
  require(toolId.isNotBlank()) { "toolId must not be blank" }
  require(caller.isNotBlank()) { "caller must not be blank" }
 }
}
