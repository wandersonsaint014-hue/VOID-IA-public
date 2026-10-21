package io.void.core.model

import com.fasterxml.jackson.databind.JsonNode

enum class RiskLevel {
 LOW, MEDIUM, HIGH;
 override fun toString(): String = name.lowercase()
}

data class ToolDescriptor(
 val toolId: String,
 val name: String,
 val version: String,
 val schemaInput: JsonNode,
 val schemaOutput: JsonNode,
 val permissionsRequired: List<String> = emptyList(),
 val riskLevel: RiskLevel = RiskLevel.LOW,
 val idempotent: Boolean = true
) {
 init {
  require(toolId.isNotBlank()) { "toolId must not be blank" }
  require(name.isNotBlank()) { "tool name must not be blank" }
  require(version.isNotBlank()) { "tool version must not be blank" }
 }
}
