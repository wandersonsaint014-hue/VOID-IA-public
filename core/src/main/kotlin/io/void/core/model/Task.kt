package io.void.core.model

import java.time.Instant

enum class AutonomyLevel {
 NONE, LOW, MEDIUM, HIGH;
 override fun toString(): String = name.lowercase()
}

data class Task(
 val taskId: String,
 val userId: String,
 val createdAt: Instant,
 val objective: String,
 val context: Map<String, Any> = emptyMap(),
 val constraints: Map<String, Any> = emptyMap(),
 val autonomyLevel: AutonomyLevel = AutonomyLevel.LOW
)
