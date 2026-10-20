package io.void.core.contract

import io.void.core.model.ToolDescriptor

interface ToolRegistry {
  fun get(toolId: String): ToolDescriptor?
  fun list(): List<ToolDescriptor>
}
