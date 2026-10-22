package io.void.core.agent

import io.void.core.contract.ActionReviewStore
import io.void.core.contract.CapabilityPolicy
import io.void.core.contract.CapabilityRegistry
import io.void.core.contract.ExecutionAuthorization
import io.void.core.contract.ReviewResult
import io.void.core.contract.VerifiedPrincipal
import io.void.core.execution.AuthorizationFingerprint
import io.void.core.execution.BoundedInputValidator
import io.void.core.execution.InputLimits
import io.void.core.model.ActionProposal
import io.void.core.model.ActionProposalStatus
import io.void.core.model.CapabilityDescriptor
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Converts an interpreted intent into a single human-reviewable action proposal.
 * This service deliberately has no Tool reference and cannot execute an effect. The caller
 * identity is supplied as a VerifiedPrincipal by a trusted boundary; ToolCall.caller is ignored.
 */
class ActionProposalService(
  private val capabilities: CapabilityRegistry,
  private val policy: CapabilityPolicy,
  private val reviewStore: ActionReviewStore,
  private val clock: Clock = Clock.systemUTC(),
  private val proposalTtl: Duration = Duration.ofMinutes(10),
  private val authorizationTtl: Duration = Duration.ofMinutes(2),
  private val inputLimits: InputLimits = InputLimits(),
  private val proposalIdFactory: () -> String = { "proposal-${UUID.randomUUID()}" },
  private val authorizationIdFactory: () -> String = { "authorization-${UUID.randomUUID()}" }
) {
  init {
    require(!proposalTtl.isNegative && !proposalTtl.isZero) { "proposalTtl must be positive" }
    require(!authorizationTtl.isNegative && !authorizationTtl.isZero) { "authorizationTtl must be positive" }
  }

  suspend fun propose(
    principal: VerifiedPrincipal,
    taskId: String,
    toolId: String,
    action: String,
    inputs: Map<String, Any>,
    destination: String?,
    capabilityIds: Set<String>,
    summary: String
  ): ReviewResult {
    if (taskId.isBlank() || toolId.isBlank() || action != actionFor(toolId)) {
      return ReviewResult(false, "invalid_action_request")
    }
    if (summary.isBlank() || summary.length > MAX_SUMMARY_LENGTH) return ReviewResult(false, "invalid_summary")
    BoundedInputValidator.validate(inputs, inputLimits)?.let { return ReviewResult(false, it) }
    BoundedInputValidator.validate(destination, inputLimits)?.let { return ReviewResult(false, it) }
    if (capabilityIds.isEmpty() || capabilityIds.any { it.isBlank() }) return ReviewResult(false, "capability_required")

    val selected = capabilityIds.map { capabilities.get(it) ?: return ReviewResult(false, "capability_not_found") }
    if (selected.any { it.toolId != toolId }) return ReviewResult(false, "capability_tool_mismatch")
    if (selected.any { !it.requiresUserReview }) return ReviewResult(false, "capability_review_contract_invalid")

    val now = clock.instant()
    val proposal = ActionProposal(
      proposalId = proposalIdFactory(),
      taskId = taskId,
      principalId = principal.principalId,
      toolId = toolId,
      action = action,
      argumentsDigest = AuthorizationFingerprint.arguments(inputs),
      destinationDigest = AuthorizationFingerprint.destination(destination),
      capabilityIds = capabilityIds,
      summary = summary,
      createdAt = now,
      expiresAt = now.plus(proposalTtl)
    )
    val decision = policy.evaluate(proposal, selected)
    if (!decision.allowed) return ReviewResult(false, "policy_denied", policyVersion = decision.policyVersion)

    reviewStore.save(proposal)
    return ReviewResult(true, "awaiting_human_review", policyVersion = decision.policyVersion)
  }

  suspend fun reject(principal: VerifiedPrincipal, proposalId: String): ReviewResult {
    val now = clock.instant()
    val proposal = reviewStore.find(proposalId) ?: return ReviewResult(false, "proposal_not_found")
    if (proposal.principalId != principal.principalId) return ReviewResult(false, "principal_mismatch")
    if (proposal.status != ActionProposalStatus.AWAITING_REVIEW) return ReviewResult(false, "proposal_not_awaiting_review")
    if (!proposal.expiresAt.isAfter(now)) {
      reviewStore.expireIfDue(proposalId, now)
      return ReviewResult(false, "proposal_expired")
    }
    return if (reviewStore.rejectIfAwaitingReview(proposalId, principal.principalId, now)) {
      ReviewResult(true, "proposal_rejected")
    } else {
      ReviewResult(false, "proposal_unavailable")
    }
  }

  suspend fun approve(principal: VerifiedPrincipal, proposalId: String): ReviewResult {
    val now = clock.instant()
    val proposal = reviewStore.find(proposalId) ?: return ReviewResult(false, "proposal_not_found")
    if (proposal.principalId != principal.principalId) return ReviewResult(false, "principal_mismatch")
    if (proposal.status != ActionProposalStatus.AWAITING_REVIEW) return ReviewResult(false, "proposal_not_awaiting_review")
    if (!proposal.expiresAt.isAfter(now)) {
      reviewStore.expireIfDue(proposalId, now)
      return ReviewResult(false, "proposal_expired")
    }

    val selected = proposal.capabilityIds.map { capabilities.get(it) ?: return ReviewResult(false, "capability_not_found") }
    if (selected.any { it.toolId != proposal.toolId || !it.requiresUserReview }) {
      return ReviewResult(false, "capability_review_contract_invalid")
    }
    val decision = policy.evaluate(proposal, selected)
    if (!decision.allowed) return ReviewResult(false, "policy_denied", policyVersion = decision.policyVersion)

    val authorization = ExecutionAuthorization(
      authorizationId = authorizationIdFactory(),
      taskId = proposal.taskId,
      toolId = proposal.toolId,
      principalId = principal.principalId,
      action = proposal.action,
      argumentsDigest = proposal.argumentsDigest,
      destinationDigest = proposal.destinationDigest,
      grantedPermissions = selected.flatMap(CapabilityDescriptor::authorizationPermissions).toSet(),
      policyVersion = decision.policyVersion,
      issuedAt = now,
      expiresAt = minOf(proposal.expiresAt, now.plus(authorizationTtl))
    )
    if (!reviewStore.approveAndIssue(proposalId, principal.principalId, authorization, now)) {
      return ReviewResult(false, "proposal_unavailable")
    }
    return ReviewResult(true, "authorization_issued", authorization.authorizationId, decision.policyVersion)
  }

  private fun actionFor(toolId: String): String = "tool.execute/$toolId"

  private companion object {
    const val MAX_SUMMARY_LENGTH = 500
  }
}
