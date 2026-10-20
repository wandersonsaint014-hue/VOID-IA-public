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
)
