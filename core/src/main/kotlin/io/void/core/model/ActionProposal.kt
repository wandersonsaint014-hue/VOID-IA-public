package io.void.core.model

import java.time.Instant

/** Lifecycle for a concrete, reviewable effect. This is separate from a planning Task. */
enum class ActionProposalStatus {
  AWAITING_REVIEW, APPROVED, REJECTED, EXPIRED, CANCELLED;
  override fun toString(): String = name.lowercase()
}

/**
 * A redacted proposal for exactly one potential effect.
 * Raw arguments and destinations stay with the trusted caller; only their digests enter
 * this contract so the approval remains bound without becoming a log of private content.
 */
data class ActionProposal(
  val proposalId: String,
  val taskId: String,
  val principalId: String,
  val toolId: String,
  val action: String,
  val argumentsDigest: String,
  val destinationDigest: String? = null,
  val capabilityIds: Set<String>,
  val summary: String,
  val createdAt: Instant,
  val expiresAt: Instant,
  val status: ActionProposalStatus = ActionProposalStatus.AWAITING_REVIEW
) {
  init {
    require(proposalId.isNotBlank()) { "proposalId must not be blank" }
    require(taskId.isNotBlank()) { "proposal taskId must not be blank" }
    require(principalId.isNotBlank()) { "proposal principalId must not be blank" }
    require(toolId.isNotBlank()) { "proposal toolId must not be blank" }
    require(action.isNotBlank()) { "proposal action must not be blank" }
    requireDigest(argumentsDigest, "proposal argumentsDigest")
    destinationDigest?.let { requireDigest(it, "proposal destinationDigest") }
    require(capabilityIds.isNotEmpty()) { "proposal must request at least one capability" }
    require(capabilityIds.none { it.isBlank() }) { "proposal capability IDs must not be blank" }
    require(summary.isNotBlank()) { "proposal summary must not be blank" }
    require(summary.length <= 500) { "proposal summary exceeds 500 characters" }
    require(expiresAt.isAfter(createdAt)) { "proposal expiry must be after creation" }
  }

  private fun requireDigest(value: String, field: String) {
    require(SHA_256.matches(value)) { "$field must be a lowercase SHA-256 digest" }
  }

  private companion object {
    val SHA_256 = Regex("[a-f0-9]{64}")
  }
}
