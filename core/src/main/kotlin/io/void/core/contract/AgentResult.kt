package io.void.core.contract

sealed class AgentResult {
  data class Success(val output: Map<String, Any>, val evidence: List<Map<String,Any>> = emptyList()) : AgentResult()
  data class Failure(val reason: String, val details: Map<String,Any>? = null) : AgentResult()
  object NoOp : AgentResult()
}
