package io.void.core.contract

/**
 * Evaluates whether a future runtime may perform an action.
 *
 * A decision is not an instruction to execute. Implementations must not invoke tools.
 */
interface PolicyGate {
  suspend fun evaluate(request: PolicyRequest): PolicyDecision
}

data class PolicyRequest(
  val taskId: String,
  val action: String,
  val destination: String? = null,
  val arguments: Map<String, Any> = emptyMap()
) {
  init {
    require(taskId.isNotBlank()) { "policy request taskId must not be blank" }
    require(action.isNotBlank()) { "policy request action must not be blank" }
  }
}

data class PolicyDecision(
  val allowed: Boolean,
  val reasons: List<String>,
  val policyVersion: String
) {
  init {
    require(reasons.isNotEmpty()) { "policy decision must include at least one reason" }
    require(policyVersion.isNotBlank()) { "policy decision policyVersion must not be blank" }
  }
}
