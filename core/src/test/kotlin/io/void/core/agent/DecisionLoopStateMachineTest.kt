package io.void.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecisionLoopStateMachineTest {
  @Test fun actionProposalIsTerminalAndNeverAnExecutionState() {
    val machine = DecisionLoopStateMachine()

    val transition = machine.apply(AgentDecision.ProposeAction("Preparar proposta"))

    assertEquals(DecisionLoopState.DECIDING, transition.from)
    assertEquals(DecisionLoopState.ACTION_PROPOSED, transition.to)
    assertEquals("action_proposed", transition.reason)
    assertTrue(machine.state.isTerminal)
    assertFailsWith<IllegalArgumentException> { machine.apply(AgentDecision.Complete) }
  }

  @Test fun continuationRequiresANewObservationAndReturnsToDeciding() {
    val machine = DecisionLoopStateMachine()
    val decision = AgentDecision.Continue(AgentObservation("simulation_receipt", "receipt-1"))

    assertFailsWith<IllegalArgumentException> { machine.apply(decision) }
    assertEquals(DecisionLoopState.DECIDING, machine.state)

    val observed = machine.apply(decision, hasNewObservation = true)
    assertEquals(DecisionLoopState.OBSERVING, observed.to)
    assertFalse(machine.state.isTerminal)

    val resumed = machine.resumeDeciding()
    assertEquals(DecisionLoopState.DECIDING, resumed.to)
    assertEquals("observation_available", resumed.reason)
  }

  @Test fun guardsAndIterationLimitStopTheRunWithoutEffects() {
    val guarded = DecisionLoopStateMachine()
    val failed = guarded.stopForGuardFailure("repeated_observation")

    assertEquals(DecisionLoopState.BLOCKED, failed.to)
    assertTrue(guarded.state.isTerminal)
    assertFailsWith<IllegalArgumentException> { guarded.resumeDeciding() }

    val bounded = DecisionLoopStateMachine()
    val limit = bounded.stopAtIterationLimit()
    assertEquals(DecisionLoopState.ITERATION_LIMIT, limit.to)
    assertTrue(bounded.state.isTerminal)
  }

  @Test fun auditFieldsContainOnlyTransitionShape() {
    val transition = DecisionLoopStateMachine().apply(AgentDecision.WaitForReview("proposal-1"))

    assertEquals(
      mapOf(
        "state_from" to "deciding",
        "state_to" to "waiting_review",
        "transition_reason" to "review_wait"
      ),
      transition.auditFields()
    )
  }
}
