package io.void.core.agent

import io.void.core.contract.Agent
import io.void.core.contract.AgentContext
import io.void.core.contract.AgentResult
import io.void.core.contract.ModelRequestMeta
import io.void.core.contract.PlanGenerator
import io.void.core.model.AuditLog
import io.void.core.model.Plan
import io.void.core.model.Step
import io.void.core.model.Task
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
      return AgentResult.Failure("task objective must not be blank")
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
      context.persistence.saveTask(task)
      context.persistence.savePlan(plan)
      context.persistence.appendAudit(
        AuditLog(
          entryId = auditIdFactory(),
          taskId = task.taskId,
          timestamp = clock.instant(),
          actor = name,
          action = "plan_proposed",
          outcome = mapOf("plan_id" to plan.planId, "step_count" to plan.steps.size),
          evidence = listOf(mapOf("model_provider" to model.providerId, "model_id" to model.modelId))
        )
      )
      AgentResult.Success(
        output = mapOf("plan_id" to plan.planId, "status" to "pending_review", "step_count" to plan.steps.size),
        evidence = listOf(mapOf("audit_action" to "plan_proposed", "model_id" to model.modelId))
      )
    } catch (error: IllegalArgumentException) {
      AgentResult.Failure(error.message ?: "invalid plan proposal")
    } catch (error: Exception) {
      AgentResult.Failure("unable to propose plan", mapOf("error_type" to error::class.simpleName.orEmpty()))
    }
  }

  private fun validateProposal(steps: List<Step>) {
    require(steps.isNotEmpty()) { "plan must contain at least one step" }
    require(steps.all { it.description.isNotBlank() }) { "plan steps must have descriptions" }
    require(steps.all { it.tool == null }) {
      "planning agent cannot propose executable tools in the first runtime"
    }
  }
}
