package io.void.core.model

data class Step(
 val stepId: String,
 val description: String,
 val responsible: String,
 val inputs: Map<String, Any> = emptyMap(),
 val tool: String? = null,
 val preconditions: List<String> = emptyList(),
 val verification: Map<String, Any> = emptyMap(),
 val retries: Int = 0,
 val timeoutSeconds: Int = 60
) {
 init {
  require(stepId.isNotBlank()) { "stepId must not be blank" }
  require(description.isNotBlank()) { "description must not be blank" }
  require(responsible.isNotBlank()) { "responsible must not be blank" }
  require(retries >= 0) { "retries must be non-negative" }
  require(timeoutSeconds > 0) { "timeoutSeconds must be positive" }
 }
}
