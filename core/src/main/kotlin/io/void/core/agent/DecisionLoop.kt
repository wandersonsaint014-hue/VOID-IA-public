package io.void.core.agent

import io.void.core.contract.Agent
import io.void.core.contract.AgentContext
import io.void.core.contract.AgentResult
import io.void.core.model.AuditLog
import io.void.core.model.Task
import io.void.core.model.TaskStatus
import java.time.Clock
import java.util.UUID

private const val MAX_TEXT_LENGTH = 2_000
private const val MAX_ACTION_SUMMARY_LENGTH = 500
private const val MAX_OBSERVATION_KIND_LENGTH = 80
private const val MAX_REFERENCE_ID_LENGTH = 256
private const val MAX_METADATA_ENTRIES = 16
private const val MAX_METADATA_DEPTH = 3
private const val MAX_METADATA_VALUE_LENGTH = 512
private val SAFE_METADATA_KEY = Regex("[a-z][a-z0-9_]{0,63}")

private fun requireValidReferenceId(referenceId: String, label: String) {
  require(
    referenceId.isNotBlank() &&
      referenceId.length <= MAX_REFERENCE_ID_LENGTH &&
      referenceId.none(Char::isISOControl)
  ) { "$label is invalid" }
}

private fun requireSafeMetadata(metadata: Map<String, Any>) {
  require(metadata.size <= MAX_METADATA_ENTRIES) { "metadata has too many entries" }
  metadata.forEach { (key, value) ->
    require(SAFE_METADATA_KEY.matches(key)) { "metadata key is invalid" }
    requireSafeMetadataValue(value, depth = 1)
  }
}

private fun requireSafeMetadataValue(value: Any?, depth: Int) {
  require(depth <= MAX_METADATA_DEPTH) { "metadata is too deeply nested" }
  when (value) {
    is String -> require(value.length <= MAX_METADATA_VALUE_LENGTH) { "metadata string is too long" }
    is Boolean, is Byte, is Short, is Int, is Long -> Unit
    is Float -> require(value.isFinite()) { "metadata number must be finite" }
    is Double -> require(value.isFinite()) { "metadata number must be finite" }
    is Map<*, *> -> {
      require(value.size <= MAX_METADATA_ENTRIES) { "metadata has too many entries" }
      value.forEach { (key, nestedValue) ->
        require(key is String && SAFE_METADATA_KEY.matches(key)) { "metadata key is invalid" }
        requireSafeMetadataValue(nestedValue, depth + 1)
      }
    }
    is Iterable<*> -> {
      var itemCount = 0
      value.forEach { item ->
        itemCount += 1
        require(itemCount <= MAX_METADATA_ENTRIES) { "metadata has too many entries" }
        requireSafeMetadataValue(item, depth + 1)
      }
    }
    else -> throw IllegalArgumentException("metadata value type is not allowed")
  }
}

/**
 * A structured decision made by the agent loop. A decision is never an execution command.
 * In particular, PROPOSE_ACTION only hands a separately reviewed intent to ActionProposalService.
 */
sealed class AgentDecision {
  data class Respond(val message: String, val details: Map<String, Any> = emptyMap()) : AgentDecision() {
    init {
      require(message.isNotBlank() && message.length <= MAX_TEXT_LENGTH) { "response message is invalid" }
      requireSafeMetadata(details)
    }
  }
  data class AskClarification(val question: String) : AgentDecision() {
    init { require(question.isNotBlank() && question.length <= MAX_TEXT_LENGTH) { "clarification question is invalid" } }
  }
  object ProposePlan : AgentDecision()
  data class ProposeAction(val summary: String) : AgentDecision() {
    init { require(summary.isNotBlank() && summary.length <= MAX_ACTION_SUMMARY_LENGTH) { "action summary is invalid" } }
  }
  data class WaitForReview(val referenceId: String) : AgentDecision() {
    init { requireValidReferenceId(referenceId, "review referenceId") }
  }
  /** Records a new observation and asks the decider for the next step. */
  data class Continue(val observation: AgentObservation) : AgentDecision()
  object Complete : AgentDecision()
  data class Fail(val reason: String) : AgentDecision() {
    init { require(reason.isNotBlank() && reason.length <= MAX_TEXT_LENGTH) { "failure reason is invalid" } }
  }
}

/** Minimal redacted observation passed from the runtime back to the deciding component. */
data class AgentObservation(
  val kind: String,
  val referenceId: String? = null,
  val details: Map<String, Any> = emptyMap()
) {
  init {
    require(kind.isNotBlank() && kind.length <= MAX_OBSERVATION_KIND_LENGTH) { "observation kind is invalid" }
    if (referenceId != null) requireValidReferenceId(referenceId, "observation referenceId")
    requireSafeMetadata(details)
  }
}

/**
 * Boundary for deterministic rules or a model-backed interpreter. Implementations decide only
 * the next structured step and receive no tool or authorization handle.
 */
fun interface AgentDecider {
  suspend fun decide(task: Task, observations: List<AgentObservation>, iteration: Int): AgentDecision
}

/**
 * Bounded orchestration layer for VOID.
 *
 * It interprets the task, selects the next safe outcome and records a redacted audit event.
 * It does not invoke tools, issue authorizations or treat client fields as identity. Action
 * proposals remain subject to ActionProposalService, capability policy and human review.
 */
class DecisionLoopAgent(
  private val decider: AgentDecider,
  private val planningAgent: Agent,
  private val clock: Clock = Clock.systemUTC(),
  private val maxIterations: Int = DEFAULT_MAX_ITERATIONS,
  private val auditIdFactory: () -> String = { "audit-${UUID.randomUUID()}" }
) : Agent {
  override val name: String = "void-decision-loop"

  init {
    require(maxIterations > 0) { "maxIterations must be positive" }
  }

  override suspend fun handleTask(task: Task, context: AgentContext): AgentResult =
    handle(task, context, emptyList())

  suspend fun handle(
    task: Task,
    context: AgentContext,
    observations: List<AgentObservation>
  ): AgentResult {
    if (task.status != TaskStatus.PLANNING) {
      return AgentResult.Failure("decision loop requires a task in planning state")
    }
    if (task.objective.isBlank()) {
      return AgentResult.Failure("task objective must not be blank")
    }
    if (observations.size > maxIterations) {
      return AgentResult.Failure("too many observations for decision loop")
    }

    return try {
      var currentObservations = observations.toList()
      repeat(maxIterations) { index ->
        val decision = decider.decide(task, currentObservations, index + 1)

        when (decision) {
          is AgentDecision.Respond -> {
            audit(context, task.taskId, "agent_responded", mapOf("iteration" to index + 1, "detail_count" to decision.details.size))
            return AgentResult.Success(mapOf("status" to "responded", "message" to decision.message, "details" to decision.details))
          }
          is AgentDecision.AskClarification -> {
            audit(context, task.taskId, "clarification_requested", mapOf("iteration" to index + 1))
            return AgentResult.Success(mapOf("status" to "needs_clarification", "question" to decision.question))
          }
          AgentDecision.ProposePlan -> {
            audit(context, task.taskId, "plan_decided", mapOf("iteration" to index + 1))
            return planningAgent.handleTask(task, context)
          }
          is AgentDecision.ProposeAction -> {
            audit(context, task.taskId, "action_proposal_decided", mapOf("iteration" to index + 1, "summary_length" to decision.summary.length))
            return AgentResult.Success(mapOf("status" to "action_proposal_required", "summary" to decision.summary))
          }
          is AgentDecision.WaitForReview -> {
            audit(context, task.taskId, "review_waiting", mapOf("iteration" to index + 1, "reference_present" to true))
            return AgentResult.Success(mapOf("status" to "awaiting_review", "reference_id" to decision.referenceId))
          }
          is AgentDecision.Continue -> {
            if (decision.observation in currentObservations) {
              audit(context, task.taskId, "decision_loop_stopped", mapOf("reason" to "repeated_observation", "iteration" to index + 1))
              return AgentResult.Failure("decision loop repeated without a new observation")
            }
            audit(context, task.taskId, "observation_recorded", mapOf("iteration" to index + 1, "kind" to decision.observation.kind))
            currentObservations = currentObservations + decision.observation
          }
          AgentDecision.Complete -> {
            audit(context, task.taskId, "agent_completed", mapOf("iteration" to index + 1))
            return AgentResult.Success(mapOf("status" to "completed"))
          }
          is AgentDecision.Fail -> {
            audit(context, task.taskId, "agent_failed", mapOf("iteration" to index + 1, "reason_length" to decision.reason.length))
            return AgentResult.Failure(decision.reason)
          }
        }
      }
      audit(context, task.taskId, "decision_loop_stopped", mapOf("reason" to "iteration_limit", "max_iterations" to maxIterations))
      AgentResult.Failure("decision loop iteration limit reached")
    } catch (error: IllegalArgumentException) {
      AgentResult.Failure(error.message ?: "invalid agent decision")
    } catch (error: Exception) {
      AgentResult.Failure("unable to decide next step", mapOf("error_type" to error::class.simpleName.orEmpty()))
    }
  }

  private suspend fun audit(context: AgentContext, taskId: String, action: String, outcome: Map<String, Any>) {
    context.persistence.appendAudit(
      AuditLog(
        entryId = auditIdFactory(),
        taskId = taskId,
        timestamp = clock.instant(),
        actor = name,
        action = action,
        outcome = outcome
      )
    )
  }


  private companion object {
    const val DEFAULT_MAX_ITERATIONS = 8
  }
}
