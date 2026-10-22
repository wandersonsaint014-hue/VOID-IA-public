package io.void.core.execution

import com.fasterxml.jackson.databind.ObjectMapper
import io.void.core.contract.ExecutionAuthorization
import io.void.core.contract.ExecutionAuthorizationStore
import io.void.core.contract.ExecutionContext
import io.void.core.contract.Persistence
import io.void.core.contract.PolicyDecision
import io.void.core.contract.PolicyGate
import io.void.core.contract.PolicyRequest
import io.void.core.contract.ToolResult
import io.void.core.contract.ToolStatus
import io.void.core.contract.ValidationResult
import io.void.core.model.AuditLog
import io.void.core.model.Plan
import io.void.core.model.RiskLevel
import io.void.core.model.Task
import io.void.core.model.ToolCall
import io.void.core.model.ToolDescriptor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

class GuardedSimulationRunnerTest {
  private val now = Instant.parse("2026-10-22T03:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val descriptor = ToolDescriptor(
    toolId = "local-note",
    name = "Local note simulation",
    version = "1",
    schemaInput = ObjectMapper().createObjectNode(),
    schemaOutput = ObjectMapper().createObjectNode(),
    permissionsRequired = listOf("notes.write"),
    riskLevel = RiskLevel.LOW
  )
  private val call = ToolCall(
    callId = "call-1",
    toolId = descriptor.toolId,
    inputs = mapOf("title" to "Plano", "body" to "Conteúdo local"),
    caller = "untrusted-client",
    permissions = listOf("notes.write", "admin")
  )
  private val context = object : ExecutionContext {
    override val userId = "user-1"
    override val taskId = "task-1"
  }

  @Test fun executesOnlyASimulatedToolAfterExactAuthorizationAndFreshPolicy() = runTest {
    val tool = RecordingSimulationTool(descriptor)
    val store = FakeAuthorizationStore(authorize())
    val policy = RecordingPolicyGate(allowed = true)
    val persistence = FakePersistence()
    val result = runner(tool, store, policy, persistence).run(call, context, "auth-1", "local://notes")

    assertEquals(ToolStatus.OK, result.status)
    assertEquals(1, tool.executions)
    assertEquals(1, store.consumeCount)
    assertEquals("tool.execute/local-note", policy.requests.single().action)
    assertEquals(AuthorizationFingerprint.arguments(call.inputs), policy.requests.single().arguments["arguments_digest"])
    assertEquals("simulation_executed", persistence.audit.single().action)
    assertFalse(persistence.audit.single().outcome.values.any { it.toString().contains("Conteúdo") })
    assertEquals(AuthorizationFingerprint.digest(call.callId), persistence.audit.single().evidence.single()["call_digest"])
    assertFalse(persistence.audit.single().evidence.single().containsKey("call_id"))
  }

  @Test fun rejectsOversizedInputBeforeAuthorizationLookupOrExecution() = runTest {
    val tool = RecordingSimulationTool(descriptor)
    val store = FakeAuthorizationStore(authorize())
    val result = GuardedSimulationRunner(tool, RecordingPolicyGate(true), store, FakePersistence(), clock, InputLimits(maxStringLength = 3))
      .run(call.copy(inputs = mapOf("x" to "longo")), context, "auth-1", "local://notes")

    assertReason("input_string_too_long", result)
    assertEquals(0, tool.executions)
    assertEquals(0, store.consumeCount)
  }

  @Test fun rejectsOversizedDestinationBeforeAuthorizationLookupOrExecution() = runTest {
    val tool = RecordingSimulationTool(descriptor)
    val store = FakeAuthorizationStore(authorize())
    val result = GuardedSimulationRunner(tool, RecordingPolicyGate(true), store, FakePersistence(), clock, InputLimits(maxStringLength = 3))
      .run(call.copy(inputs = mapOf("x" to "ok")), context, "auth-1", "longo")

    assertReason("input_string_too_long", result)
    assertEquals(0, tool.executions)
    assertEquals(0, store.findCount)
    assertEquals(0, store.consumeCount)
  }

  @Test fun rejectsChangedArgumentsBeforePolicyConsumptionOrExecution() = runTest {
    val tool = RecordingSimulationTool(descriptor)
    val store = FakeAuthorizationStore(authorize())
    val policy = RecordingPolicyGate(allowed = true)
    val result = runner(tool, store, policy, FakePersistence()).run(call.copy(inputs = mapOf("title" to "Alterado")), context, "auth-1", "local://notes")

    assertReason("arguments_mismatch", result)
    assertEquals(0, tool.executions)
    assertEquals(0, policy.requests.size)
    assertEquals(0, store.consumeCount)
  }

  @Test fun rejectsExpiredRevokedAndAlreadyConsumedAuthorizations() = runTest {
    listOf(
      FakeAuthorizationStore(authorize(expiresAt = now)) to "authorization_expired",
      FakeAuthorizationStore(null) to "authorization_not_found",
      FakeAuthorizationStore(authorize(), initiallyConsumed = true) to "authorization_unavailable"
    ).forEach { (store, expected) ->
      val tool = RecordingSimulationTool(descriptor)
      val result = runner(tool, store, RecordingPolicyGate(true), FakePersistence()).run(call, context, "auth-1", "local://notes")
      assertReason(expected, result)
      assertEquals(0, tool.executions)
    }
  }

  @Test fun refusesPolicyDenialEvenWithAValidAuthorization() = runTest {
    val tool = RecordingSimulationTool(descriptor)
    val store = FakeAuthorizationStore(authorize())
    val persistence = FakePersistence()
    val result = runner(tool, store, RecordingPolicyGate(allowed = false), persistence).run(call, context, "auth-1", "local://notes")

    assertReason("policy_denied", result)
    assertEquals(0, tool.executions)
    assertEquals(0, store.consumeCount)
    assertEquals("simulation_policy_denied", persistence.audit.single().action)
  }

  @Test fun ignoresClientClaimedPermissionsAndRequiresTrustedGrant() = runTest {
    val tool = RecordingSimulationTool(descriptor)
    val result = runner(tool, FakeAuthorizationStore(authorize(permissions = emptySet())), RecordingPolicyGate(true), FakePersistence())
      .run(call, context, "auth-1", "local://notes")

    assertReason("required_permission_missing", result)
    assertEquals(0, tool.executions)
  }

  @Test fun bindsPrincipalTaskToolActionAndDestination() = runTest {
    val variants = listOf(
      authorize(principalId = "another-user") to context,
      authorize(taskId = "another-task") to context,
      authorize(toolId = "another-tool") to context,
      authorize(action = "tool.execute/another-tool") to context,
      authorize(destination = "local://elsewhere") to context
    )
    val expected = listOf("principal_mismatch", "task_mismatch", "tool_mismatch", "action_mismatch", "destination_mismatch")

    variants.zip(expected).forEach { (pair, reason) ->
      val tool = RecordingSimulationTool(descriptor)
      val result = runner(tool, FakeAuthorizationStore(pair.first), RecordingPolicyGate(true), FakePersistence())
        .run(call, pair.second, "auth-1", "local://notes")
      assertReason(reason, result)
      assertEquals(0, tool.executions)
    }
  }

  @Test fun acceptsEquivalentMapsWithDifferentInsertionOrder() = runTest {
    val left = linkedMapOf<String, Any>("z" to listOf(3, 2), "a" to linkedMapOf("b" to true))
    val right = linkedMapOf<String, Any>("a" to linkedMapOf("b" to true), "z" to listOf(3, 2))
    assertEquals(AuthorizationFingerprint.arguments(left), AuthorizationFingerprint.arguments(right))
  }

  private fun runner(
    tool: RecordingSimulationTool,
    store: FakeAuthorizationStore,
    policy: RecordingPolicyGate,
    persistence: FakePersistence
  ) = GuardedSimulationRunner(tool, policy, store, persistence, clock) { "audit-${persistence.audit.size + 1}" }

  private fun authorize(
    taskId: String = "task-1",
    toolId: String = descriptor.toolId,
    principalId: String = "user-1",
    action: String = "tool.execute/${descriptor.toolId}",
    destination: String = "local://notes",
    permissions: Set<String> = setOf("notes.write"),
    expiresAt: Instant = now.plusSeconds(60)
  ) = ExecutionAuthorization(
    authorizationId = "auth-1",
    taskId = taskId,
    toolId = toolId,
    principalId = principalId,
    action = action,
    argumentsDigest = AuthorizationFingerprint.arguments(call.inputs),
    destinationDigest = AuthorizationFingerprint.destination(destination),
    grantedPermissions = permissions,
    policyVersion = "test-policy-v1",
    issuedAt = now.minusSeconds(60),
    expiresAt = expiresAt
  )

  private fun assertReason(expected: String, result: ToolResult) {
    assertEquals(ToolStatus.REQUIRES_APPROVAL, result.status)
    assertEquals(expected, result.output?.get("reason"))
  }

  private class RecordingSimulationTool(override val descriptor: ToolDescriptor) : SimulatedTool {
    var executions = 0
    override suspend fun validateInputs(inputs: Map<String, Any>) = ValidationResult(valid = true)
    override suspend fun execute(call: ToolCall, context: ExecutionContext): ToolResult {
      executions += 1
      return ToolResult(ToolStatus.OK, mapOf("simulated" to true))
    }
  }

  private class FakeAuthorizationStore(
    private val authorization: ExecutionAuthorization?,
    initiallyConsumed: Boolean = false
  ) : ExecutionAuthorizationStore {
    private var consumed = initiallyConsumed
    var findCount = 0
    var consumeCount = 0
    override suspend fun find(authorizationId: String): ExecutionAuthorization? {
      findCount += 1
      return authorization?.takeIf { it.authorizationId == authorizationId }
    }
    override suspend fun consumeIfActive(authorizationId: String, now: Instant): Boolean {
      consumeCount += 1
      if (consumed || authorization == null || authorization.authorizationId != authorizationId || authorization.expiresAt <= now) return false
      consumed = true
      return true
    }
  }

  private class RecordingPolicyGate(private val allowed: Boolean) : PolicyGate {
    val requests = mutableListOf<PolicyRequest>()
    override suspend fun evaluate(request: PolicyRequest): PolicyDecision {
      requests += request
      return PolicyDecision(allowed, listOf(if (allowed) "simulation_allowed" else "simulation_disabled"), "test-policy-v1")
    }
  }

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
