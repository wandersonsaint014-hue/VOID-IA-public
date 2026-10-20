package io.void.core.contract

interface AgentContext {
  val modelRouter: ModelRouter
  val toolRegistry: ToolRegistry
  val persistence: Persistence
}
