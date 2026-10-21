package io.void.core.model

import com.fasterxml.jackson.module.kotlin.readValue
import io.void.core.serialization.VoidObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SerializationContractTest {
 private val mapper = VoidObjectMapper.instance
 private val schema = mapper.readTree("{\"type\":\"object\"}")

 @Test fun taskRoundTripUsesSnakeCaseLowercaseEnumAndIsoInstant() {
  val task = Task("task-1", "user-1", Instant.parse("2026-01-02T03:04:05Z"), "Review", autonomyLevel = AutonomyLevel.HIGH)
  val json = mapper.writeValueAsString(task)
  assertEquals("high", mapper.readTree(json)["autonomy_level"].asText())
  assertEquals("planning", mapper.readTree(json)["status"].asText())
  assertEquals("2026-01-02T03:04:05Z", mapper.readTree(json)["created_at"].asText())
  assertEquals(task, mapper.readValue<Task>(json))
 }

 @Test fun planAndStepRoundTripOmitsOptionalNulls() {
  val plan = Plan("plan-1", "task-1", listOf(Step("step-1", "Inspect", "core")))
  val json = mapper.writeValueAsString(plan)
  assertFalse(mapper.readTree(json).has("created_by"))
  assertEquals("pending_review", mapper.readTree(json)["status"].asText())
  assertEquals("pending_review", mapper.readTree(json)["steps"][0]["status"].asText())
  assertEquals(plan, mapper.readValue<Plan>(json))
 }

 @Test fun toolDescriptorAndAuditLogRoundTrip() {
  val tool = ToolDescriptor("tool-1", "lookup", "1.0", schema, schema, riskLevel = RiskLevel.MEDIUM)
  assertEquals(tool, mapper.readValue<ToolDescriptor>(mapper.writeValueAsString(tool)))
  val audit = AuditLog("entry-1", "task-1", Instant.EPOCH, "user-1", "review")
  assertEquals(audit, mapper.readValue<AuditLog>(mapper.writeValueAsString(audit)))
 }

 @Test fun rejectsUnknownContractFields() {
  assertFailsWith<Exception> {
   mapper.readValue<Task>("{\"task_id\":\"t\",\"user_id\":\"u\",\"created_at\":\"2026-01-01T00:00:00Z\",\"objective\":\"o\",\"unknown\":true}")
  }
 }

 @Test fun stepRejectsInvalidExecutionValues() {
  assertFailsWith<IllegalArgumentException> { Step("s", "d", "r", retries = -1) }
  assertFailsWith<IllegalArgumentException> { Step("s", "d", "r", timeoutSeconds = 0) }
 }
}
