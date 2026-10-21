package io.void.core.model

data class Plan(
 val planId: String,
 val taskId: String,
 val steps: List<Step>,
 val createdBy: String? = null,
 val metadata: Map<String, Any> = emptyMap()
) {
 init {
  require(planId.isNotBlank()) { "planId must not be blank" }
  require(taskId.isNotBlank()) { "taskId must not be blank" }
  require(steps.isNotEmpty()) { "plan must contain at least one step" }
 }
}
