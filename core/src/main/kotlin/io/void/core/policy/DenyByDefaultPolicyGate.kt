package io.void.core.policy

import io.void.core.contract.Persistence
import io.void.core.contract.PolicyDecision
import io.void.core.contract.PolicyGate
import io.void.core.contract.PolicyRequest
import io.void.core.model.AuditLog
import java.time.Clock
import java.util.UUID

/**
 * Initial policy boundary for VOID.
 *
 * It records a reviewable denial for every request and deliberately contains no execution path.
 * Request arguments and destinations are not copied to the audit trail because they may be sensitive.
 */
class DenyByDefaultPolicyGate(
  private val persistence: Persistence,
  private val clock: Clock = Clock.systemUTC(),
  private val auditIdFactory: () -> String = { "audit-${UUID.randomUUID()}" }
) : PolicyGate {
  override suspend fun evaluate(request: PolicyRequest): PolicyDecision {
    val decision = PolicyDecision(
      allowed = false,
      reasons = listOf("execution_disabled"),
      policyVersion = POLICY_VERSION
    )

    persistence.appendAudit(
      AuditLog(
        entryId = auditIdFactory(),
        taskId = request.taskId,
        timestamp = clock.instant(),
        actor = "void-policy-gate",
        action = "policy_denied",
        outcome = mapOf(
          "allowed" to decision.allowed,
          "reasons" to decision.reasons,
          "policy_version" to decision.policyVersion
        ),
        evidence = listOf(mapOf("requested_action" to request.action))
      )
    )
    return decision
  }

  private companion object {
    const val POLICY_VERSION = "v1-deny-by-default"
  }
}
