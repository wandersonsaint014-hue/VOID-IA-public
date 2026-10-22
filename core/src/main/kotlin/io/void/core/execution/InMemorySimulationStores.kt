package io.void.core.execution

import io.void.core.contract.ActionReviewStore
import io.void.core.contract.ExecutionAuthorization
import io.void.core.contract.ExecutionAuthorizationStore
import io.void.core.model.ActionProposal
import io.void.core.model.ActionProposalStatus
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared, process-local state for deterministic simulations and tests.
 *
 * It deliberately is not durable, authenticated, encrypted or revocable. Android production
 * storage must replace it with transactional persistence before any non-simulated connector exists.
 */
class InMemorySimulationState {
  internal val mutex = Mutex()
  internal val proposals = mutableMapOf<String, ActionProposal>()
  internal val authorizations = mutableMapOf<String, ExecutionAuthorization>()
  internal val consumedAuthorizations = mutableSetOf<String>()
}

/** Transactional review store backed by [InMemorySimulationState]. */
class InMemoryActionReviewStore(
  private val state: InMemorySimulationState = InMemorySimulationState()
) : ActionReviewStore {
  fun authorizationStore(): ExecutionAuthorizationStore = InMemoryExecutionAuthorizationStore(state)
  override suspend fun save(proposal: ActionProposal) = state.mutex.withLock {
    require(proposal.status == ActionProposalStatus.AWAITING_REVIEW) {
      "new proposals must await review"
    }
    require(state.proposals.putIfAbsent(proposal.proposalId, proposal) == null) {
      "proposal already exists"
    }
  }

  override suspend fun find(proposalId: String): ActionProposal? = state.mutex.withLock {
    state.proposals[proposalId]
  }

  override suspend fun approveAndIssue(
    proposalId: String,
    principalId: String,
    authorization: ExecutionAuthorization,
    now: Instant
  ): Boolean = state.mutex.withLock {
    val proposal = state.proposals[proposalId] ?: return@withLock false
    if (proposal.principalId != principalId ||
      proposal.status != ActionProposalStatus.AWAITING_REVIEW ||
      !proposal.expiresAt.isAfter(now) ||
      state.authorizations.containsKey(authorization.authorizationId)
    ) return@withLock false

    state.proposals[proposalId] = proposal.copy(status = ActionProposalStatus.APPROVED)
    state.authorizations[authorization.authorizationId] = authorization
    true
  }

  override suspend fun rejectIfAwaitingReview(proposalId: String, principalId: String, now: Instant): Boolean =
    state.mutex.withLock {
      val proposal = state.proposals[proposalId] ?: return@withLock false
      if (proposal.principalId != principalId ||
        proposal.status != ActionProposalStatus.AWAITING_REVIEW ||
        !proposal.expiresAt.isAfter(now)
      ) return@withLock false
      state.proposals[proposalId] = proposal.copy(status = ActionProposalStatus.REJECTED)
      true
    }

  override suspend fun expireIfDue(proposalId: String, now: Instant): Boolean = state.mutex.withLock {
    val proposal = state.proposals[proposalId] ?: return@withLock false
    if (proposal.status != ActionProposalStatus.AWAITING_REVIEW || proposal.expiresAt.isAfter(now)) {
      return@withLock false
    }
    state.proposals[proposalId] = proposal.copy(status = ActionProposalStatus.EXPIRED)
    true
  }
}

/** Single-use authorization ledger sharing the same lock as review decisions. */
class InMemoryExecutionAuthorizationStore(
  private val state: InMemorySimulationState
) : ExecutionAuthorizationStore {
  override suspend fun find(authorizationId: String): ExecutionAuthorization? = state.mutex.withLock {
    state.authorizations[authorizationId]
  }

  override suspend fun consumeIfActive(authorizationId: String, now: Instant): Boolean = state.mutex.withLock {
    val authorization = state.authorizations[authorizationId] ?: return@withLock false
    if (!authorization.expiresAt.isAfter(now) || authorizationId in state.consumedAuthorizations) {
      return@withLock false
    }
    state.consumedAuthorizations += authorizationId
    true
  }
}
