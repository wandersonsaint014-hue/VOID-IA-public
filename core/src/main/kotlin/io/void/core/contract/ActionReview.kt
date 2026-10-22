package io.void.core.contract

import io.void.core.model.ActionProposal
import io.void.core.model.CapabilityDescriptor
import java.time.Instant

/**
 * Identity must come from an authenticated Android/app boundary, never from a JSON request.
 * The core consumes this minimal trusted view and does not implement authentication itself.
 */
data class VerifiedPrincipal(val principalId: String, val verifiedAt: Instant) {
  init { require(principalId.isNotBlank()) { "verified principalId must not be blank" } }
}

data class CapabilityDecision(
  val allowed: Boolean,
  val reasons: List<String>,
  val policyVersion: String
) {
  init {
    require(reasons.isNotEmpty()) { "capability decision must include at least one reason" }
    require(policyVersion.isNotBlank()) { "capability decision policyVersion must not be blank" }
  }
}

/** A deterministic policy that evaluates a proposal but cannot execute it. */
interface CapabilityPolicy {
  suspend fun evaluate(proposal: ActionProposal, capabilities: List<CapabilityDescriptor>): CapabilityDecision
}

/**
 * Transactional boundary for a human decision and its capability token. Implementations must
 * atomically mark the proposal approved and persist the authorization exactly once.
 */
interface ActionReviewStore {
  suspend fun save(proposal: ActionProposal)
  suspend fun find(proposalId: String): ActionProposal?
  suspend fun approveAndIssue(
    proposalId: String,
    principalId: String,
    authorization: ExecutionAuthorization,
    now: Instant
  ): Boolean
  suspend fun rejectIfAwaitingReview(proposalId: String, principalId: String, now: Instant): Boolean
  suspend fun expireIfDue(proposalId: String, now: Instant): Boolean
}

data class ReviewResult(
  val approved: Boolean,
  val reason: String,
  val authorizationId: String? = null,
  val policyVersion: String? = null
)
