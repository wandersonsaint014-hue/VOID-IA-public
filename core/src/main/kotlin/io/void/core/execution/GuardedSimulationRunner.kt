package io.void.core.execution

import io.void.core.contract.ExecutionAuthorizationStore
import io.void.core.contract.ExecutionContext
import io.void.core.contract.Persistence
import io.void.core.contract.PolicyDecision
import io.void.core.contract.PolicyGate
import io.void.core.contract.PolicyRequest
import io.void.core.contract.ToolResult
import io.void.core.contract.ToolStatus
import io.void.core.model.AuditLog
import io.void.core.model.ToolCall
import java.time.Clock
import java.util.UUID

/**
 * Executes one explicitly simulated tool only after a precise, expiring and single-use
 * authorization and a fresh policy decision. Client-provided caller/permissions are never
 * used to grant access.
 */
class GuardedSimulationRunner(
  private val tool: SimulatedTool,
  private val policyGate: PolicyGate,
  private val authorizationStore: ExecutionAuthorizationStore,
  private val persistence: Persistence,
  private val clock: Clock = Clock.systemUTC(),
  private val inputLimits: InputLimits = InputLimits(),
  private val auditIdFactory: () -> String = { "audit-${UUID.randomUUID()}" }
) {
  suspend fun run(
    call: ToolCall,
    context: ExecutionContext,
    authorizationId: String,
    destination: String? = null
  ): ToolResult {
    val now = clock.instant()
    BoundedInputValidator.validate(call.inputs, inputLimits)?.let { reason ->
      return blocked(call, context, reason, now)
    }
    BoundedInputValidator.validate(destination, inputLimits)?.let { reason ->
      return blocked(call, context, reason, now)
    }
    val authorization = authorizationStore.find(authorizationId)
      ?: return blocked(call, context, "authorization_not_found", now)

    val bindingError = bindingError(call, context, authorizationId, authorization, destination, now)
    if (bindingError != null) return blocked(call, context, bindingError, now)

    val validation = tool.validateInputs(call.inputs)
    if (!validation.valid) return blocked(call, context, "invalid_tool_inputs", now, validation.errors)

    val decision = policyGate.evaluate(
      PolicyRequest(
        taskId = context.taskId ?: return blocked(call, context, "task_id_required", now),
        action = authorization.action,
        destination = AuthorizationFingerprint.destination(destination),
        arguments = mapOf("arguments_digest" to AuthorizationFingerprint.arguments(call.inputs))
      )
    )
    if (!decision.allowed) return policyBlocked(call, context, decision, now)
    if (decision.policyVersion != authorization.policyVersion) {
      return blocked(call, context, "policy_version_mismatch", now)
    }
    if (!authorizationStore.consumeIfActive(authorizationId, now)) {
      return blocked(call, context, "authorization_unavailable", now)
    }

    return try {
      val result = tool.execute(call, context)
      audit(call, context, "simulation_executed", now, mapOf(
        "status" to result.status.toString(),
        "authorization_id" to authorizationId,
        "tool_id" to tool.descriptor.toolId
      ))
      result
    } catch (error: Exception) {
      audit(call, context, "simulation_failed", now, mapOf(
        "authorization_id" to authorizationId,
        "tool_id" to tool.descriptor.toolId,
        "error_type" to error::class.simpleName.orEmpty()
      ))
      ToolResult(ToolStatus.ERROR, mapOf("reason" to "simulation_failed"))
    }
  }

  private fun bindingError(
    call: ToolCall,
    context: ExecutionContext,
    authorizationId: String,
    authorization: io.void.core.contract.ExecutionAuthorization,
    destination: String?,
    now: java.time.Instant
  ): String? = when {
    context.taskId == null -> "task_id_required"
    authorization.taskId != context.taskId -> "task_mismatch"
    authorization.toolId != tool.descriptor.toolId || call.toolId != tool.descriptor.toolId -> "tool_mismatch"
    authorization.principalId != context.userId -> "principal_mismatch"
    authorization.action != actionFor(tool.descriptor.toolId) -> "action_mismatch"
    authorization.expiresAt <= now -> "authorization_expired"
    authorization.argumentsDigest != AuthorizationFingerprint.arguments(call.inputs) -> "arguments_mismatch"
    authorization.destinationDigest != AuthorizationFingerprint.destination(destination) -> "destination_mismatch"
    !authorization.grantedPermissions.containsAll(tool.descriptor.permissionsRequired) -> "required_permission_missing"
    authorizationId.isBlank() -> "authorization_not_found"
    else -> null
  }

  private suspend fun policyBlocked(
    call: ToolCall,
    context: ExecutionContext,
    decision: PolicyDecision,
    now: java.time.Instant
  ): ToolResult {
    audit(call, context, "simulation_policy_denied", now, mapOf(
      "policy_version" to decision.policyVersion,
      "reason_count" to decision.reasons.size
    ))
    return ToolResult(ToolStatus.REQUIRES_APPROVAL, mapOf("reason" to "policy_denied"))
  }

  private suspend fun blocked(
    call: ToolCall,
    context: ExecutionContext,
    reason: String,
    now: java.time.Instant,
    errors: List<String> = emptyList()
  ): ToolResult {
    audit(call, context, "simulation_blocked", now, mapOf(
      "reason" to reason,
      "validation_error_count" to errors.size,
      "tool_id" to tool.descriptor.toolId
    ))
    return ToolResult(ToolStatus.REQUIRES_APPROVAL, mapOf("reason" to reason))
  }

  private suspend fun audit(
    call: ToolCall,
    context: ExecutionContext,
    action: String,
    timestamp: java.time.Instant,
    outcome: Map<String, Any>
  ) {
    val taskId = context.taskId ?: return
    persistence.appendAudit(AuditLog(
      entryId = auditIdFactory(),
      taskId = taskId,
      timestamp = timestamp,
      actor = "void-simulation-runner",
      action = action,
      outcome = outcome,
      evidence = listOf(mapOf("call_digest" to AuthorizationFingerprint.digest(call.callId)))
    ))
  }

  private fun actionFor(toolId: String): String = "tool.execute/$toolId"
}
