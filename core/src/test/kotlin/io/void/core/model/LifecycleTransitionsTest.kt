package io.void.core.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LifecycleTransitionsTest {
  private val task = Task("task-1", "user-1", Instant.EPOCH, "Review")
  private val plan = Plan("plan-1", "task-1", listOf(Step("step-1", "Inspect", "core")))
  private val step = Step("step-1", "Inspect", "core")

  @Test fun planningTaskCanMoveOnlyToReviewOrCancellation() {
    assertEquals(TaskStatus.PENDING_REVIEW, LifecycleTransitions.task(task, TaskStatus.PENDING_REVIEW).status)
    assertEquals(TaskStatus.CANCELLED, LifecycleTransitions.task(task, TaskStatus.CANCELLED).status)
    assertFailsWith<IllegalArgumentException> { LifecycleTransitions.task(task, TaskStatus.APPROVED) }
  }

  @Test fun reviewStatesCanBeResolvedOnce() {
    val reviewedTask = LifecycleTransitions.task(task, TaskStatus.PENDING_REVIEW)
    val approvedTask = LifecycleTransitions.task(reviewedTask, TaskStatus.APPROVED)
    assertEquals(TaskStatus.APPROVED, approvedTask.status)
    assertEquals(PlanStatus.APPROVED, LifecycleTransitions.plan(plan, PlanStatus.APPROVED).status)
    assertEquals(StepStatus.REJECTED, LifecycleTransitions.step(step, StepStatus.REJECTED).status)
    assertFailsWith<IllegalArgumentException> { LifecycleTransitions.task(approvedTask, TaskStatus.CANCELLED) }
  }

  @Test fun terminalPlanAndStepStatesCannotTransitionAgain() {
    val approvedPlan = LifecycleTransitions.plan(plan, PlanStatus.APPROVED)
    val cancelledStep = LifecycleTransitions.step(step, StepStatus.CANCELLED)
    assertFailsWith<IllegalArgumentException> { LifecycleTransitions.plan(approvedPlan, PlanStatus.REJECTED) }
    assertFailsWith<IllegalArgumentException> { LifecycleTransitions.step(cancelledStep, StepStatus.APPROVED) }
  }
}
