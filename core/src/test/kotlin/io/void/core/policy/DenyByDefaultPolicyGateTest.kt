package io.void.core.policy

import io.void.core.contract.Persistence
import io.void.core.contract.PolicyRequest
import io.void.core.model.AuditLog
import io.void.core.model.Plan
import io.void.core.model.Task
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DenyByDefaultPolicyGateTest {
  @Test fun deniesEveryRequestAndRecordsMinimalAuditEvidence() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val gate = DenyByDefaultPolicyGate(
      persistence = persistence,
      clock = Clock.fixed(Instant.parse("2026-10-21T13:40:00Z"), ZoneOffset.UTC),
      auditIdFactory = { "audit-policy-1" }
    )
    val request = PolicyRequest(
      taskId = "task-1",
      action = "send_message",
      destination = "private@example.invalid",
      arguments = mapOf("message" to "confidential")
    )

    val decision = gate.evaluate(request)

    assertFalse(decision.allowed)
    assertEquals(listOf("execution_disabled"), decision.reasons)
    assertEquals("v1-deny-by-default", decision.policyVersion)
    val audit = persistence.audit.single()
    assertEquals("audit-policy-1", audit.entryId)
    assertEquals(Instant.parse("2026-10-21T13:40:00Z"), audit.timestamp)
    assertEquals("void-policy-gate", audit.actor)
    assertEquals("policy_denied", audit.action)
    assertEquals(false, audit.outcome["allowed"])
    assertEquals(listOf("execution_disabled"), audit.outcome["reasons"])
    assertEquals("send_message", audit.evidence.single()["requested_action"])
    assertNull(audit.outcome["arguments"])
    assertNull(audit.outcome["destination"])
  }

  private class FakePersistence : Persistence {
    val audit = mutableListOf<AuditLog>()
    override suspend fun saveTask(task: Task) = Unit
    override suspend fun getTask(taskId: String): Task? = null
    override suspend fun savePlan(plan: Plan) = Unit
    override suspend fun getPlan(planId: String): Plan? = null
    override suspend fun appendAudit(log: AuditLog) { audit += log }
    override suspend fun listAudit(taskId: String): List<AuditLog> = audit.filter { it.taskId == taskId }
  }
}
