package io.void.core.agent

import io.void.core.contract.ActionReviewStore
import io.void.core.contract.CapabilityDecision
import io.void.core.contract.CapabilityPolicy
import io.void.core.contract.CapabilityRegistry
import io.void.core.contract.ExecutionAuthorization
import io.void.core.contract.VerifiedPrincipal
import io.void.core.model.ActionProposal
import io.void.core.model.ActionProposalStatus
import io.void.core.model.CapabilityDescriptor
import io.void.core.model.CapabilityOperation
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class ActionProposalServiceTest {
  private val now = Instant.parse("2026-10-22T03:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val principal = VerifiedPrincipal("user-1", now)
  private val capability = CapabilityDescriptor(
    capabilityId = "notes.create",
    toolId = "local-note",
    operation = CapabilityOperation.WRITE,
    authorizationPermissions = setOf("notes.write")
  )

  @Test fun createsReviewableProposalThenIssuesOneBoundAuthorization() = runTest {
    val store = FakeReviewStore()
    val service = service(store)
    val proposed = service.propose(principal, "task-1", "local-note", "tool.execute/local-note", mapOf("title" to "Plano"), "local://notes", setOf("notes.create"), "Criar nota local")

    assertEquals("awaiting_human_review", proposed.reason)
    val proposal = assertNotNull(store.proposal)
    assertEquals(ActionProposalStatus.AWAITING_REVIEW, proposal.status)
    assertEquals("user-1", proposal.principalId)

    val approved = service.approve(principal, proposal.proposalId)
    assertEquals("authorization_issued", approved.reason)
    val authorization = assertNotNull(store.authorization)
    assertEquals(proposal.argumentsDigest, authorization.argumentsDigest)
    assertEquals(proposal.destinationDigest, authorization.destinationDigest)
    assertEquals(setOf("notes.write"), authorization.grantedPermissions)
    assertEquals("policy-v1", authorization.policyVersion)
  }

  @Test fun rejectsUnregisteredCapabilityAndNeverPersistsProposal() = runTest {
    val store = FakeReviewStore()
    val result = service(store).propose(principal, "task-1", "local-note", "tool.execute/local-note", emptyMap(), null, setOf("missing"), "Criar nota")
    assertEquals("capability_not_found", result.reason)
    assertEquals(null, store.proposal)
  }

  @Test fun rejectsOversizedDestinationBeforeProposalPersistence() = runTest {
    val store = FakeReviewStore()
    val service = ActionProposalService(
      registry(), AllowPolicy(), store, clock, inputLimits = io.void.core.execution.InputLimits(maxStringLength = 3),
      proposalIdFactory = { "proposal-1" }, authorizationIdFactory = { "auth-1" }
    )

    val result = service.propose(principal, "task-1", "local-note", "tool.execute/local-note", emptyMap(), "longo", setOf("notes.create"), "Criar nota")
    assertEquals("input_string_too_long", result.reason)
    assertEquals(null, store.proposal)
  }

  @Test fun rejectsInvalidSummaryBeforeProposalPersistence() = runTest {
    val store = FakeReviewStore()
    val result = service(store).propose(principal, "task-1", "local-note", "tool.execute/local-note", emptyMap(), null, setOf("notes.create"), " ")
    assertEquals("invalid_summary", result.reason)
    assertEquals(null, store.proposal)
  }

  @Test fun rejectsProposalWithoutIssuingAuthorization() = runTest {
    val store = FakeReviewStore()
    val service = service(store)
    service.propose(principal, "task-1", "local-note", "tool.execute/local-note", emptyMap(), null, setOf("notes.create"), "Criar nota")

    val result = service.reject(principal, "proposal-1")
    assertEquals("proposal_rejected", result.reason)
    assertEquals(ActionProposalStatus.REJECTED, store.proposal?.status)
    assertEquals(null, store.authorization)
  }

  @Test fun rejectsExpiredProposalWithoutIssuingAuthorization() = runTest {
    val store = FakeReviewStore()
    val service = ActionProposalService(
      registry(), AllowPolicy(), store, clock,
      proposalTtl = java.time.Duration.ofSeconds(1), authorizationTtl = java.time.Duration.ofMinutes(1),
      proposalIdFactory = { "proposal-1" }, authorizationIdFactory = { "auth-1" }
    )
    val proposal = ActionProposal("proposal-1", "task-1", "user-1", "local-note", "tool.execute/local-note", "a".repeat(64), capabilityIds = setOf("notes.create"), summary = "Criar nota", createdAt = now.minusSeconds(10), expiresAt = now.minusSeconds(1))
    store.proposal = proposal
    val result = service.approve(principal, proposal.proposalId)
    assertEquals("proposal_expired", result.reason)
    assertEquals(null, store.authorization)
  }

  private fun service(store: FakeReviewStore) = ActionProposalService(
    registry(), AllowPolicy(), store, clock,
    proposalIdFactory = { "proposal-1" }, authorizationIdFactory = { "auth-1" }
  )

  private fun registry() = object : CapabilityRegistry {
    override fun get(capabilityId: String) = capability.takeIf { it.capabilityId == capabilityId }
    override fun listForTool(toolId: String) = listOf(capability).filter { it.toolId == toolId }
  }

  private class AllowPolicy : CapabilityPolicy {
    override suspend fun evaluate(proposal: ActionProposal, capabilities: List<CapabilityDescriptor>) =
      CapabilityDecision(true, listOf("simulated_local_only"), "policy-v1")
  }

  private class FakeReviewStore : ActionReviewStore {
    var proposal: ActionProposal? = null
    var authorization: ExecutionAuthorization? = null
    override suspend fun save(proposal: ActionProposal) { this.proposal = proposal }
    override suspend fun find(proposalId: String) = proposal?.takeIf { it.proposalId == proposalId }
    override suspend fun approveAndIssue(proposalId: String, principalId: String, authorization: ExecutionAuthorization, now: Instant): Boolean {
      val current = proposal ?: return false
      if (current.proposalId != proposalId || current.principalId != principalId || current.status != ActionProposalStatus.AWAITING_REVIEW || !current.expiresAt.isAfter(now)) return false
      proposal = current.copy(status = ActionProposalStatus.APPROVED)
      this.authorization = authorization
      return true
    }
    override suspend fun rejectIfAwaitingReview(proposalId: String, principalId: String, now: Instant): Boolean {
      val current = proposal ?: return false
      if (current.proposalId != proposalId || current.principalId != principalId || current.status != ActionProposalStatus.AWAITING_REVIEW || !current.expiresAt.isAfter(now)) return false
      proposal = current.copy(status = ActionProposalStatus.REJECTED)
      return true
    }
    override suspend fun expireIfDue(proposalId: String, now: Instant): Boolean {
      val current = proposal ?: return false
      if (current.proposalId != proposalId || current.expiresAt.isAfter(now)) return false
      proposal = current.copy(status = ActionProposalStatus.EXPIRED)
      return true
    }
  }
}
