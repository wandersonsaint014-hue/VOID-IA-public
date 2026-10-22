package io.void.core.execution

import io.void.core.agent.ActionProposalService
import io.void.core.contract.CapabilityDecision
import io.void.core.contract.CapabilityPolicy
import io.void.core.contract.CapabilityRegistry
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest

class InMemorySimulationStoresTest {
  private val now = Instant.parse("2026-10-22T03:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val principal = VerifiedPrincipal("user-1", now)
  private val capability = CapabilityDescriptor(
    capabilityId = "notes.create",
    toolId = "local-note",
    operation = CapabilityOperation.WRITE,
    authorizationPermissions = setOf("notes.write")
  )

  @Test fun approvesOnlyOnceAcrossConcurrentReviewsAndConsumesOnlyOnce() = runTest {
    val state = InMemorySimulationState()
    val reviewStore = InMemoryActionReviewStore(state)
    val authorizationStore = InMemoryExecutionAuthorizationStore(state)
    val service = ActionProposalService(
      capabilities = registry(),
      policy = AllowPolicy(),
      reviewStore = reviewStore,
      clock = clock,
      proposalIdFactory = { "proposal-1" },
      authorizationIdFactory = { "auth-1" }
    )
    service.propose(principal, "task-1", "local-note", "tool.execute/local-note", emptyMap(), null, setOf("notes.create"), "Criar nota")

    val approvals = coroutineScope {
      listOf(async { service.approve(principal, "proposal-1") }, async { service.approve(principal, "proposal-1") }).awaitAll()
    }
    assertEquals(1, approvals.count { it.reason == "authorization_issued" })
    assertEquals(ActionProposalStatus.APPROVED, assertNotNull(reviewStore.find("proposal-1")).status)

    val consumptions = coroutineScope {
      listOf(
        async { authorizationStore.consumeIfActive("auth-1", now) },
        async { authorizationStore.consumeIfActive("auth-1", now) }
      ).awaitAll()
    }
    assertEquals(1, consumptions.count { it })
  }

  private fun registry() = object : CapabilityRegistry {
    override fun get(capabilityId: String) = capability.takeIf { it.capabilityId == capabilityId }
    override fun listForTool(toolId: String) = listOf(capability).filter { it.toolId == toolId }
  }

  private class AllowPolicy : CapabilityPolicy {
    override suspend fun evaluate(proposal: ActionProposal, capabilities: List<CapabilityDescriptor>) =
      CapabilityDecision(true, listOf("simulation_only"), "policy-v1")
  }
}
