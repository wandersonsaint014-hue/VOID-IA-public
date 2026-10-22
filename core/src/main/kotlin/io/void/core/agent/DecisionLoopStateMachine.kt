package io.void.core.agent

/**
 * Explicit control state for one bounded decision-loop run.
 *
 * This machine coordinates interpretation outcomes only. It has no access to tools,
 * authorizations, persistence, model routing, or execution handles. In particular,
 * [DecisionLoopState.ACTION_PROPOSED] is terminal for this run and is not execution.
 */
enum class DecisionLoopState {
  DECIDING, OBSERVING, PLANNING, RESPONDED, AWAITING_CLARIFICATION, ACTION_PROPOSED,
  WAITING_REVIEW, COMPLETED,
  /** A guardrail deliberately blocked an unsafe or non-progressing continuation. */
  BLOCKED,
  /** The deciding component deliberately ended this run without a usable outcome. */
  FAILED,
  ITERATION_LIMIT;
  override fun toString(): String = name.lowercase()
  val isTerminal: Boolean get() = this != DECIDING && this != OBSERVING
}

/** A compact, non-sensitive record of a state transition. */
data class DecisionLoopTransition(val from: DecisionLoopState, val to: DecisionLoopState, val reason: String) {
  init { require(reason.isNotBlank()) { "transition reason must not be blank" } }
  fun auditFields(): Map<String, Any> = mapOf(
    "state_from" to from.toString(), "state_to" to to.toString(), "transition_reason" to reason
  )
}

/** Dependency-free state-machine adapter that never executes a decision. */
class DecisionLoopStateMachine {
  var state: DecisionLoopState = DecisionLoopState.DECIDING
    private set

  fun apply(decision: AgentDecision, hasNewObservation: Boolean = false): DecisionLoopTransition {
    require(state == DecisionLoopState.DECIDING) { "decision loop cannot accept a decision while in $state" }
    return when (decision) {
      is AgentDecision.Respond -> move(DecisionLoopState.RESPONDED, "respond")
      is AgentDecision.AskClarification -> move(DecisionLoopState.AWAITING_CLARIFICATION, "clarification_requested")
      AgentDecision.ProposePlan -> move(DecisionLoopState.PLANNING, "plan_proposed")
      is AgentDecision.ProposeAction -> move(DecisionLoopState.ACTION_PROPOSED, "action_proposed")
      is AgentDecision.WaitForReview -> move(DecisionLoopState.WAITING_REVIEW, "review_wait")
      is AgentDecision.Continue -> {
        require(hasNewObservation) { "decision loop requires a new observation to continue" }
        move(DecisionLoopState.OBSERVING, "observation_recorded")
      }
      AgentDecision.Complete -> move(DecisionLoopState.COMPLETED, "complete")
      is AgentDecision.Fail -> move(DecisionLoopState.FAILED, "fail")
    }
  }

  fun resumeDeciding(): DecisionLoopTransition {
    require(state == DecisionLoopState.OBSERVING) { "decision loop can resume only after observing" }
    return move(DecisionLoopState.DECIDING, "observation_available")
  }

  fun stopAtIterationLimit(): DecisionLoopTransition {
    require(state == DecisionLoopState.DECIDING) { "decision loop can stop for iteration limit only while deciding" }
    return move(DecisionLoopState.ITERATION_LIMIT, "iteration_limit")
  }

  /** Blocks a run whose continuation guard was not met, without invoking any external effect. */
  fun stopForGuardFailure(reason: String): DecisionLoopTransition {
    require(state == DecisionLoopState.DECIDING) { "decision loop can stop for a guard failure only while deciding" }
    return move(DecisionLoopState.BLOCKED, reason)
  }

  private fun move(target: DecisionLoopState, reason: String): DecisionLoopTransition {
    val transition = DecisionLoopTransition(state, target, reason)
    state = target
    return transition
  }
}
