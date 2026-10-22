package io.void.core.agent

import io.void.core.contract.Agent
import io.void.core.contract.AgentContext
import io.void.core.contract.AgentFailureKind
import io.void.core.contract.AgentResult
import io.void.core.contract.ModelRequestMeta
import io.void.core.contract.ModelRouter
import io.void.core.contract.ModelSelection
import io.void.core.contract.Persistence
import io.void.core.contract.ToolRegistry
import io.void.core.model.AuditLog
import io.void.core.model.AutonomyLevel
import io.void.core.model.Plan
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

class DecisionLoopAgentTest {
  private val task = Task(
    taskId = "task-loop-1",
    userId = "user-1",
    createdAt = Instant.EPOCH,
    objective = "Organizar os estudos",
    autonomyLevel = AutonomyLevel.LOW
  )

  @Test fun returnsClarificationWithoutCallingPlannerOrTools() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    var plannerCalled = false
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> AgentDecision.AskClarification("Qual a data da prova?") },
      planningAgent = recordingPlanner { plannerCalled = true; AgentResult.NoOp },
      clock = fixedClock(),
      auditIdFactory = { "audit-1" }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    val success = assertIs<AgentResult.Success>(result)
    assertEquals("needs_clarification", success.output["status"])
    assertEquals("Qual a data da prova?", success.output["question"])
    assertTrue(!plannerCalled)
    assertEquals("clarification_requested", persistence.audit.single().action)
  }

  @Test fun delegatesPlanProposalToPlannerOnly() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    var plannerCalled = false
    val expected = AgentResult.Success(mapOf("status" to "pending_review", "plan_id" to "plan-1"))
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> AgentDecision.ProposePlan },
      planningAgent = recordingPlanner { plannerCalled = true; expected },
      clock = fixedClock(),
      auditIdFactory = { "audit-1" }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    assertEquals(expected, result)
    assertTrue(plannerCalled)
    assertEquals("plan_decided", persistence.audit.single().action)
  }

  @Test fun returnsActionProposalRequirementInsteadOfExecutingAnything() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    var plannerCalled = false
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> AgentDecision.ProposeAction("Preparar envio para revisão") },
      planningAgent = recordingPlanner { plannerCalled = true; AgentResult.NoOp },
      clock = fixedClock(),
      auditIdFactory = { "audit-1" }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    val success = assertIs<AgentResult.Success>(result)
    assertEquals("action_proposal_required", success.output["status"])
    assertEquals("Preparar envio para revisão", success.output["summary"])
    assertTrue(!plannerCalled)
    assertEquals("action_proposal_decided", persistence.audit.single().action)
  }

  @Test fun observesThenCompletesWithinBoundedLoop() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, observations, _ ->
        if (observations.isEmpty()) AgentDecision.Continue(AgentObservation("simulation_receipt", "receipt-1"))
        else AgentDecision.Complete
      },
      planningAgent = recordingPlanner { AgentResult.NoOp },
      clock = fixedClock(),
      auditIdFactory = { "audit-${persistence.audit.size + 1}" }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    val success = assertIs<AgentResult.Success>(result)
    assertEquals("completed", success.output["status"])
    assertEquals(listOf("observation_recorded", "decision_resumed", "agent_completed"), persistence.audit.map { it.action })
    assertEquals("observing", persistence.audit[0].outcome["state_to"])
    assertEquals("deciding", persistence.audit[1].outcome["state_to"])
    assertEquals("completed", persistence.audit[2].outcome["state_to"])
  }

  @Test fun stopsRepeatedDecisionWithoutNewObservation() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> AgentDecision.Continue(AgentObservation("unchanged")) },
      planningAgent = recordingPlanner { AgentResult.NoOp },
      clock = fixedClock(),
      auditIdFactory = { "audit-${persistence.audit.size + 1}" }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    val failure = assertIs<AgentResult.Failure>(result)
    assertEquals("decision loop repeated without a new observation", failure.reason)
    assertEquals(AgentFailureKind.GUARDRAIL, failure.kind)
    assertEquals(listOf("observation_recorded", "decision_resumed", "decision_loop_stopped"), persistence.audit.map { it.action })
    assertEquals("blocked", persistence.audit.last().outcome["state_to"])
    assertEquals("repeated_observation", persistence.audit.last().outcome["transition_reason"])
  }

  @Test fun stopsWhenAnEarlierObservationRepeats() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val observations = listOf(
      AgentObservation("first", "receipt-1"),
      AgentObservation("second", "receipt-2")
    )
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> AgentDecision.Continue(AgentObservation("first", "receipt-1")) },
      planningAgent = recordingPlanner { AgentResult.NoOp },
      clock = fixedClock(),
      auditIdFactory = { "audit-1" }
    )

    val result = agent.handle(task, fakeContext(persistence), observations)

    val failure = assertIs<AgentResult.Failure>(result)
    assertEquals("decision loop repeated without a new observation", failure.reason)
    assertEquals(AgentFailureKind.GUARDRAIL, failure.kind)
    assertEquals(listOf("decision_loop_stopped"), persistence.audit.map { it.action })
    assertEquals("blocked", persistence.audit.single().outcome["state_to"])
    assertEquals("repeated_observation", persistence.audit.single().outcome["transition_reason"])
  }

  @Test fun stopsAtIterationLimit() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, iteration -> AgentDecision.Continue(AgentObservation("tick", "receipt-$iteration")) },
      planningAgent = recordingPlanner { AgentResult.NoOp },
      clock = fixedClock(),
      maxIterations = 2,
      auditIdFactory = { "audit-${persistence.audit.size + 1}" }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    val failure = assertIs<AgentResult.Failure>(result)
    assertEquals("decision loop iteration limit reached", failure.reason)
    assertEquals(AgentFailureKind.GUARDRAIL, failure.kind)
    assertEquals(
      listOf("observation_recorded", "decision_resumed", "observation_recorded", "decision_resumed", "decision_loop_stopped"),
      persistence.audit.map { it.action }
    )
    assertEquals("iteration_limit", persistence.audit.last().outcome["state_to"])
  }

  @Test fun classifiesDeliberateAgentStopWithoutLeakingItsReasonToAudit() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> AgentDecision.Fail("missing local context") },
      planningAgent = recordingPlanner { AgentResult.NoOp },
      clock = fixedClock(),
      auditIdFactory = { "audit-1" }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    val failure = assertIs<AgentResult.Failure>(result)
    assertEquals("missing local context", failure.reason)
    assertEquals(AgentFailureKind.AGENT_STOPPED, failure.kind)
    assertEquals("missing local context".length, persistence.audit.single().outcome["reason_length"])
    assertTrue("missing local context" !in persistence.audit.single().outcome.values)
  }

  @Test fun classifiesUnexpectedDeciderErrorWithoutLeakingExceptionMessage() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> error("private endpoint unavailable") },
      planningAgent = recordingPlanner { AgentResult.NoOp }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    val failure = assertIs<AgentResult.Failure>(result)
    assertEquals("unable to decide next step", failure.reason)
    assertEquals(AgentFailureKind.INTERNAL_ERROR, failure.kind)
    assertEquals("IllegalStateException", failure.details?.get("error_type"))
  }

  @Test fun rejectsUnsafeDecisionMetadataBeforeTheLoopRuns() {
    val tooMany = (1..17).associate { "field_$it" to it }

    val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
      AgentDecision.Respond("Resposta", tooMany)
    }

    assertEquals("metadata has too many entries", error.message)
  }

  @Test fun redactsReviewReferenceAndFailureReasonFromAudit() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    val reference = "proposal-private-123"
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> AgentDecision.WaitForReview(reference) },
      planningAgent = recordingPlanner { AgentResult.NoOp },
      clock = fixedClock(),
      auditIdFactory = { "audit-1" }
    )

    val result = agent.handleTask(task, fakeContext(persistence))

    val success = assertIs<AgentResult.Success>(result)
    assertEquals(reference, success.output["reference_id"])
    assertEquals(
      mapOf(
        "iteration" to 1,
        "reference_present" to true,
        "state_from" to "deciding",
        "state_to" to "waiting_review",
        "transition_reason" to "review_wait"
      ),
      persistence.audit.single().outcome
    )
  }

  @Test fun rejectsNonPlanningTasksBeforeDeciding() = kotlinx.coroutines.test.runTest {
    val persistence = FakePersistence()
    var decided = false
    val agent = DecisionLoopAgent(
      decider = AgentDecider { _, _, _ -> decided = true; AgentDecision.Complete },
      planningAgent = recordingPlanner { AgentResult.NoOp }
    )

    val result = agent.handleTask(task.copy(status = TaskStatus.PENDING_REVIEW), fakeContext(persistence))

    val failure = assertIs<AgentResult.Failure>(result)
    assertEquals("decision loop requires a task in planning state", failure.reason)
    assertEquals(AgentFailureKind.INVALID_INPUT, failure.kind)
    assertTrue(!decided)
    assertTrue(persistence.audit.isEmpty())
  }

  private fun recordingPlanner(block: () -> AgentResult = { AgentResult.NoOp }): Agent = object : Agent {
    override val name: String = "test-planner"
    override suspend fun handleTask(task: Task, context: AgentContext): AgentResult = block()
  }

  private fun fakeContext(persistence: FakePersistence): AgentContext = object : AgentContext {
    override val modelRouter = object : ModelRouter {
      override fun selectModel(requestMeta: ModelRequestMeta) = ModelSelection("test", "test", "local", "test")
    }
    override val toolRegistry = object : ToolRegistry {
      override fun get(toolId: String): ToolDescriptor? = null
      override fun list(): List<ToolDescriptor> = emptyList()
    }
    override val persistence: Persistence = persistence
  }

  private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"), ZoneOffset.UTC)

  private class FakePersistence : Persistence {
    val audit = mutableListOf<AuditLog>()
    override suspend fun saveTask(task: Task) = Unit
    override suspend fun getTask(taskId: String): Task? = null
    override suspend fun savePlan(plan: Plan) = Unit
    override suspend fun getPlan(planId: String): Plan? = null
    override suspend fun appendAudit(log: AuditLog) { audit += log }
    override suspend fun listAudit(taskId: String): List<AuditLog> = audit.filter { it.taskId == taskId }
  }
}
