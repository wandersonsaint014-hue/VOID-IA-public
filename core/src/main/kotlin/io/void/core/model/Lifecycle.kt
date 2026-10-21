package io.void.core.model

/**
 * Lifecycle states are proposal and review states only. None represents tool execution.
 */
enum class TaskStatus {
  PLANNING, PENDING_REVIEW, APPROVED, REJECTED, CANCELLED;
  override fun toString(): String = name.lowercase()
}

enum class PlanStatus {
  PENDING_REVIEW, APPROVED, REJECTED, CANCELLED;
  override fun toString(): String = name.lowercase()
}

enum class StepStatus {
  PENDING_REVIEW, APPROVED, REJECTED, CANCELLED;
  override fun toString(): String = name.lowercase()
}

/** Structural transition rules only; authorization and approval verification remain separate. */
object LifecycleTransitions {
  fun task(task: Task, target: TaskStatus): Task = task.copy(status = taskStatus(task.status, target))
  fun plan(plan: Plan, target: PlanStatus): Plan = plan.copy(status = planStatus(plan.status, target))
  fun step(step: Step, target: StepStatus): Step = step.copy(status = stepStatus(step.status, target))

  fun taskStatus(current: TaskStatus, target: TaskStatus): TaskStatus {
    require(target in taskTargets(current)) { "invalid task transition: $current -> $target" }
    return target
  }

  fun planStatus(current: PlanStatus, target: PlanStatus): PlanStatus {
    require(target in planTargets(current)) { "invalid plan transition: $current -> $target" }
    return target
  }

  fun stepStatus(current: StepStatus, target: StepStatus): StepStatus {
    require(target in stepTargets(current)) { "invalid step transition: $current -> $target" }
    return target
  }

  private fun taskTargets(current: TaskStatus): Set<TaskStatus> = when (current) {
    TaskStatus.PLANNING -> setOf(TaskStatus.PENDING_REVIEW, TaskStatus.CANCELLED)
    TaskStatus.PENDING_REVIEW -> setOf(TaskStatus.APPROVED, TaskStatus.REJECTED, TaskStatus.CANCELLED)
    TaskStatus.APPROVED, TaskStatus.REJECTED, TaskStatus.CANCELLED -> emptySet()
  }

  private fun planTargets(current: PlanStatus): Set<PlanStatus> = when (current) {
    PlanStatus.PENDING_REVIEW -> setOf(PlanStatus.APPROVED, PlanStatus.REJECTED, PlanStatus.CANCELLED)
    PlanStatus.APPROVED, PlanStatus.REJECTED, PlanStatus.CANCELLED -> emptySet()
  }

  private fun stepTargets(current: StepStatus): Set<StepStatus> = when (current) {
    StepStatus.PENDING_REVIEW -> setOf(StepStatus.APPROVED, StepStatus.REJECTED, StepStatus.CANCELLED)
    StepStatus.APPROVED, StepStatus.REJECTED, StepStatus.CANCELLED -> emptySet()
  }
}
