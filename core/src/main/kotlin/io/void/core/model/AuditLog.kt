package io.void.core.model

import java.time.Instant

/** Audit entry corresponding to the fields defined in schemas/audit.json. */
data class AuditLog(
  val entryId: String,
  val taskId: String,
  val timestamp: Instant,
  val actor: String,
  val action: String,
  val outcome: Map<String, Any> = emptyMap(),
  val evidence: List<Map<String, Any>> = emptyList()
) {
  init {
    require(entryId.isNotBlank()) { "entryId must not be blank" }
    require(taskId.isNotBlank()) { "taskId must not be blank" }
    require(actor.isNotBlank()) { "actor must not be blank" }
    require(action.isNotBlank()) { "action must not be blank" }
  }
}
