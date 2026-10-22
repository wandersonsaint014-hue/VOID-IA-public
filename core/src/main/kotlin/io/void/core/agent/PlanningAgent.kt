package io.void.core.agent

import io.void.core.contract.Agent
import io.void.core.contract.AgentContext
import io.void.core.contract.AgentFailureKind
import io.void.core.contract.AgentResult
import io.void.core.contract.ModelRequestMeta
import io.void.core.contract.PlanGenerator
import io.void.core.model.AuditLog
import io.void.core.model.LifecycleTransitions
import io.void.core.model.Plan
import io.void.core.model.Step
import io.void.core.model.StepStatus
import io.void.core.model.Task
import io.void.core.model.TaskStatus
import java.time.Clock
import java.util.UUID

/**
 * First safe VOID agent: turns a task into a persisted, reviewable proposal.
 *
 * It intentionally never calls a tool. A generated plan is data, not an instruction to
 * execute. Tool execution and approvals remain a separate, policy-gated runtime concern.
 */
class PlanningAgent(
  private val generator: PlanGenerator,
  private val clock: Clock = Clock.systemUTC(),
  private val planIdFactory: () -> String = { "plan-${UUID.randomUUID()}" },
  private val auditIdFactory: () -> String = { "audit-${UUID.randomUUID()}" }
) : Agent {
  override val name: String = "void-planner"

  override suspend fun handleTask(task: Task, context: AgentContext): AgentResult {
    if (task.objective.isBlank()) {
      return failure("task objective must not be blank", AgentFailureKind.INVALID_INPUT)
    }
    if (task.status != TaskStatus.PLANNING) {
      return failure("planning agent requires a task in planning state", AgentFailureKind.INVALID_INPUT)
    }

    return try {
      val model = context.modelRouter.selectModel(
        ModelRequestMeta(
          taskId = task.taskId,
          purpose = "plan_task",
          privacyLabel = task.constraints["privacy_label"] as? String,
          latencyBudgetMs = (task.constraints["latency_budget_ms"] as? Number)?.toLong() ?: 5_000,
          costBudgetCents = (task.constraints["cost_budget_cents"] as? Number)?.toLong() ?: 0
        )
      )
      val proposedSteps = generator.propose(task, model)
      validateProposal(proposedSteps)

      val plan = Plan(
        planId = planIdFactory(),
        taskId = task.taskId,
        steps = proposedSteps,
        createdBy = name,
        metadata = mapOf(
          "proposal_only" to true,
          "model_provider" to model.providerId,
          "model_id" to model.modelId,
          "model_mode" to model.mode
        )
      )
      val reviewedTask = LifecycleTransitions.task(task, TaskStatus.PENDING_REVIEW)
      context.persistence.saveTask(reviewedTask)
      context.persistence.savePlan(plan)
      context.persistence.appendAudit(
        AuditLog(
          entryId = auditIdFactory(),
          taskId = task.taskId,
          timestamp = clock.instant(),
          actor = name,
          action = "plan_proposed",
          outcome = mapOf(
            "plan_id" to plan.planId,
            "step_count" to plan.steps.size,
            "task_status" to reviewedTask.status.toString(),
            "plan_status" to plan.status.toString()
          ),
          evidence = listOf(mapOf("model_provider" to model.providerId, "model_id" to model.modelId))
        )
      )
      AgentResult.Success(
        output = mapOf(
          "plan_id" to plan.planId,
          "status" to plan.status.toString(),
          "task_status" to reviewedTask.status.toString(),
          "step_count" to plan.steps.size
        ),
        evidence = listOf(mapOf("audit_action" to "plan_proposed", "model_id" to model.modelId))
      )
    } catch (error: IllegalArgumentException) {
      failure("invalid plan proposal", AgentFailureKind.INVALID_INPUT, "plan_proposal_invalid")
    } catch (error: Exception) {
      failure("unable to propose plan", AgentFailureKind.DEPENDENCY_FAILURE, "plan_dependency_unavailable")
    }
  }

  private fun failure(
    reason: String,
    kind: AgentFailureKind,
    errorCode: String? = null
  ): AgentResult.Failure = AgentResult.Failure(
    reason = reason,
    kind = kind,
    details = errorCode?.let { mapOf("error_code" to it) }
  )

  private fun validateProposal(steps: List<Step>) {
    require(steps.isNotEmpty()) { "plan must contain at least one step" }
    require(steps.all { it.stepId.isNotBlank() }) { "plan steps must have identifiers" }
    require(steps.map { it.stepId }.distinct().size == steps.size) { "plan step identifiers must be unique" }
    require(steps.all { it.description.isNotBlank() }) { "plan steps must have descriptions" }
    require(steps.all { it.responsible.isNotBlank() }) { "plan steps must have responsible parties" }
    require(steps.all { it.tool == null }) {
      "planning agent cannot propose executable tools in the first runtime"
    }
    require(steps.all { it.status == StepStatus.PENDING_REVIEW }) {
      "planning agent must propose steps pending review"
    }
  }
}
