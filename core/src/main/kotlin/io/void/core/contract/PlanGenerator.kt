package io.void.core.contract

import io.void.core.model.Step
import io.void.core.model.Task

/**
 * Boundary for a model-backed planner. Implementations may call an LLM, but must return
 * only a proposed plan: they never receive authority to execute tools.
 */
fun interface PlanGenerator {
  suspend fun propose(task: Task, model: ModelSelection): List<Step>
}
