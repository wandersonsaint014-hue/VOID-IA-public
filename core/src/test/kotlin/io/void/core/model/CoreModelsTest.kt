package io.void.core.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreModelsTest {
  @Test fun taskDefaultsAreConservative() {
    val task = Task("task-1", "user-1", Instant.EPOCH, "Review")
    assertEquals(AutonomyLevel.LOW, task.autonomyLevel)
    assertTrue(task.context.isEmpty())
    assertTrue(task.constraints.isEmpty())
  }

  @Test fun auditDefaultsContainNoEvidence() {
    val log = AuditLog("entry-1", "task-1", Instant.EPOCH, "user-1", "review")
    assertTrue(log.outcome.isEmpty())
    assertTrue(log.evidence.isEmpty())
    assertEquals(Instant.EPOCH, log.timestamp)
  }

  @Test fun toolCallDoesNotGrantPermissionsByDefault() {
    val call = ToolCall("call-1", "tool-1", emptyMap(), "user-1")
    assertTrue(call.permissions.isEmpty())
  }
}
