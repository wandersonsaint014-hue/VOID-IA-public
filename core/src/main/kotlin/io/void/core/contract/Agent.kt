package io.void.core.contract

import io.void.core.model.Task

interface Agent {
  val name: String
  suspend fun handleTask(task: Task, context: AgentContext): AgentResult
}
