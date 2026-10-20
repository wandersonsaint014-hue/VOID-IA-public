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
)
