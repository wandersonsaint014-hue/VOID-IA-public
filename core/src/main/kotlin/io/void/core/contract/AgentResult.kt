package io.void.core.contract

/**
 * Classification for an agent run that did not produce an outcome.
 *
 * These values describe how a caller may recover. They never authorize, execute or persist
 * anything: VOID remains responsible for interpreting the next task or observation.
 */
enum class AgentFailureKind {
  /** A caller supplied data that cannot be used as the input to this run. */
  INVALID_INPUT,
  /** A loop or lifecycle invariant deliberately stopped this run safely. */
  GUARDRAIL,
  /** The deciding component deliberately ended this run and supplied a redacted reason. */
  AGENT_STOPPED,
  /** A required dependency such as model routing or persistence was unavailable. */
  DEPENDENCY_FAILURE,
  /** An unexpected implementation failure; details must contain no raw exception message. */
  INTERNAL_ERROR
}

sealed class AgentResult {
  data class Success(val output: Map<String, Any>, val evidence: List<Map<String,Any>> = emptyList()) : AgentResult()
  data class Failure(
    val reason: String,
    val details: Map<String, Any>? = null,
    val kind: AgentFailureKind = AgentFailureKind.INTERNAL_ERROR
  ) : AgentResult()
  object NoOp : AgentResult()
}
