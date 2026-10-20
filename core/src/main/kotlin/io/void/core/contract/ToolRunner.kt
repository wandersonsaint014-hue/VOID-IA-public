package io.void.core.contract

import io.void.core.model.ToolCall
import io.void.core.model.ToolDescriptor

interface ToolRunner {
  suspend fun run(call: ToolCall, descriptor: ToolDescriptor, execCtx: ExecutionContext): ToolResult
}
