package io.void.core.contract

import io.void.core.model.*

interface Persistence {
  suspend fun saveTask(task: Task)
  suspend fun getTask(taskId: String): Task?
  suspend fun savePlan(plan: Plan)
  suspend fun getPlan(planId: String): Plan?
  suspend fun appendAudit(log: AuditLog)
  suspend fun listAudit(taskId: String): List<AuditLog>
}
