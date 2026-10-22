package io.void.core.model

/**
 * A closed, inspectable capability a mobile connector may expose. It is descriptive only:
 * it never grants a platform permission or executes an effect.
 */
enum class CapabilityOperation {
  READ, WRITE, DELETE, SHARE, EXECUTE;
  override fun toString(): String = name.lowercase()
}

data class CapabilityDescriptor(
  val capabilityId: String,
  val toolId: String,
  val operation: CapabilityOperation,
  /** Internal permissions bound into an execution authorization, not Android grants. */
  val authorizationPermissions: Set<String> = emptySet(),
  val requiredAndroidPermissions: Set<String> = emptySet(),
  val requiresNetwork: Boolean = false,
  val requiresUserReview: Boolean = true,
  val riskLevel: RiskLevel = RiskLevel.LOW
) {
  init {
    require(capabilityId.isNotBlank()) { "capabilityId must not be blank" }
    require(toolId.isNotBlank()) { "capability toolId must not be blank" }
    require(authorizationPermissions.none { it.isBlank() }) { "authorization permission names must not be blank" }
    require(requiredAndroidPermissions.none { it.isBlank() }) { "Android permission names must not be blank" }
  }
}
