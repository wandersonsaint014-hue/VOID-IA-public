package io.void.core.model

data class Plan(
  val planId: String,
  val taskId: String,
  val steps: List<Step>,
  val createdBy: String? = null,
  val metadata: Map<String, Any> = emptyMap()
)
