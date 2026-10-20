package io.void.core.contract

import io.void.core.model.ToolCall
import io.void.core.model.ToolDescriptor

interface Tool {
  val descriptor: io.void.core.model.ToolDescriptor
  suspend fun validateInputs(inputs: Map<String,Any>): ValidationResult
  suspend fun execute(call: ToolCall, context: ExecutionContext): ToolResult
}

data class ValidationResult(val valid: Boolean, val errors: List<String> = emptyList())
data class ToolResult(val status: ToolStatus, val output: Map<String,Any>?, val evidence: List<Map<String,Any>> = emptyList())
enum class ToolStatus { OK, ERROR, PARTIAL, REQUIRES_APPROVAL }
