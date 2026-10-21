package io.void.core.agent

import io.void.core.contract.AgentContext
import io.void.core.contract.ModelRequestMeta
import io.void.core.contract.ModelRouter
import io.void.core.contract.ModelSelection
import io.void.core.contract.Persistence
import io.void.core.contract.ToolRegistry
import io.void.core.model.AuditLog
import io.void.core.model.AutonomyLevel
import io.void.core.model.Plan
import io.void.core.model.Step
import io.void.core.model.StepStatus
import io.void.core.model.Task
import io.void.core.model.TaskStatus
import io.void.core.model.ToolDescriptor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanningAgentTest {
  private val task = Task(
    taskId = "task-1",
    userId = "user-1",
    createdAt = Instant.EPOCH,
    objective = "Organizar estudos",
    constraints = mapOf("latency_budget_ms" to 800, "cost_budget_cents" to 3),
    autonomyLevel = AutonomyLevel.LOW
  )

  @Test fun createsReviewablePlanAndAuditWithoutRunningTools() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    var receivedMeta: ModelRequestMeta? = null
    val context = fakeContext(persistence) { receivedMeta = it }
    val agent = PlanningAgent(
      generator = { _, _ -> listOf(Step("step-1", "Reunir matérias", "planner")) },
      clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC),
      planIdFactory = { "plan-1" },
      auditIdFactory = { "audit-1" }
    )

    val result = agent.handleTask(task, context)

    val success = assertIs<io.void.core.contract.AgentResult.Success>(result)
    assertEquals("plan-1", success.output["plan_id"])
    assertEquals("pending_review", success.output["status"])
    assertEquals(800L, receivedMeta?.latencyBudgetMs)
    assertEquals(3L, receivedMeta?.costBudgetCents)
    assertEquals(TaskStatus.PENDING_REVIEW, persistence.tasks[task.taskId]?.status)
    assertEquals(TaskStatus.PENDING_REVIEW.toString(), success.output["task_status"])
    assertEquals("void-planner", persistence.plans["plan-1"]?.createdBy)
    assertEquals(StepStatus.PENDING_REVIEW, persistence.plans["plan-1"]?.steps?.single()?.status)
    assertEquals(true, persistence.plans["plan-1"]?.metadata?.get("proposal_only"))
    assertEquals("plan_proposed", persistence.audit.single().action)
    assertEquals(Instant.parse("2026-10-21T12:00:00Z"), persistence.audit.single().timestamp)
  }

  @Test fun rejectsEmptyObjectiveBeforeModelOrPersistence() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    var routerCalled = false
    val agent = PlanningAgent(generator = { _, _ -> error("must not generate") })
    val blankTask = task.copy(objective = "   ")

    val result = agent.handleTask(blankTask, fakeContext(persistence) { routerCalled = true })

    val failure = assertIs<io.void.core.contract.AgentResult.Failure>(result)
    assertEquals("task objective must not be blank", failure.reason)
    assertTrue(!routerCalled)
    assertTrue(persistence.tasks.isEmpty())
  }

  @Test fun rejectsTaskOutsidePlanningBeforeModelOrPersistence() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    var routerCalled = false
    val agent = PlanningAgent(generator = { _, _ -> error("must not generate") })

    val result = agent.handleTask(task.copy(status = TaskStatus.PENDING_REVIEW), fakeContext(persistence) { routerCalled = true })

    val failure = assertIs<io.void.core.contract.AgentResult.Failure>(result)
    assertEquals("planning agent requires a task in planning state", failure.reason)
    assertTrue(!routerCalled)
    assertTrue(persistence.tasks.isEmpty())
  }

  @Test fun rejectsStepOutsidePendingReviewBeforePersistence() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val agent = PlanningAgent(
      generator = { _, _ -> listOf(Step("step-1", "Review", "planner", status = StepStatus.APPROVED)) }
    )

    val result = agent.handleTask(task, fakeContext(persistence) {})

    val failure = assertIs<io.void.core.contract.AgentResult.Failure>(result)
    assertTrue(failure.reason.contains("pending review"))
    assertTrue(persistence.tasks.isEmpty())
  }

  @Test fun rejectsAmbiguousOrIncompleteStepsBeforePersistence() = kotlinx.coroutines.test.runTest {
    val invalidPlans = listOf(
      listOf(Step(" ", "Reunir matérias", "planner")) to "identifiers",
      listOf(Step("step-1", "Reunir matérias", "planner"), Step("step-1", "Revisar matérias", "planner")) to "unique",
      listOf(Step("step-1", "Reunir matérias", " ")) to "responsible"
    )

    invalidPlans.forEach { (steps, expectedReason) ->
      val persistence = FakePersistence()
      val agent = PlanningAgent(generator = { _, _ -> steps })
      val result = agent.handleTask(task, fakeContext(persistence) {})
      val failure = assertIs<io.void.core.contract.AgentResult.Failure>(result)
      assertTrue(failure.reason.contains(expectedReason))
      assertTrue(persistence.tasks.isEmpty())
      assertTrue(persistence.plans.isEmpty())
      assertTrue(persistence.audit.isEmpty())
    }
  }

  @Test fun rejectsToolBearingPlanAndDoesNotPersistIt() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val agent = PlanningAgent(generator = { _, _ -> listOf(Step("step-1", "Enviar", "planner", tool = "send-message")) })
    val result = agent.handleTask(task, fakeContext(persistence) {})
    val failure = assertIs<io.void.core.contract.AgentResult.Failure>(result)
    assertTrue(failure.reason.contains("cannot propose executable tools"))
    assertTrue(persistence.plans.isEmpty())
    assertTrue(persistence.audit.isEmpty())
  }

  private fun fakeContext(persistence: FakePersistence, onSelect: (ModelRequestMeta) -> Unit): AgentContext = object : AgentContext {
    override val modelRouter = object : ModelRouter {
      override fun selectModel(requestMeta: ModelRequestMeta): ModelSelection {
        onSelect(requestMeta)
        return ModelSelection("test-provider", "test-model", "planning", "test")
      }
    }
    override val toolRegistry = object : ToolRegistry {
      override fun get(toolId: String): ToolDescriptor? = null
      override fun list(): List<ToolDescriptor> = emptyList()
    }
    override val persistence: Persistence = persistence
  }

  private class FakePersistence : Persistence {
    val tasks = mutableMapOf<String, Task>()
    val plans = mutableMapOf<String, Plan>()
    val audit = mutableListOf<AuditLog>()
    override suspend fun saveTask(task: Task) { tasks[task.taskId] = task }
    override suspend fun getTask(taskId: String): Task? = tasks[taskId]
    override suspend fun savePlan(plan: Plan) { plans[plan.planId] = plan }
    override suspend fun getPlan(planId: String): Plan? = plans[planId]
    override suspend fun appendAudit(log: AuditLog) { audit += log }
    override suspend fun listAudit(taskId: String): List<AuditLog> = audit.filter { it.taskId == taskId }
  }
}
