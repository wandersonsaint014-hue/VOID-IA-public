package io.void.core.contract

import java.time.Instant

/**
 * A trusted-boundary-minted, single-use authorization for one simulated tool invocation.
 *
 * This is a capability checked by the runtime, not data accepted from a client. The
 * issuer is responsible for authenticating the principal before creating it.
 */
data class ExecutionAuthorization(
  val authorizationId: String,
  val taskId: String,
  val toolId: String,
  val principalId: String,
  val action: String,
  val argumentsDigest: String,
  val destinationDigest: String? = null,
  val grantedPermissions: Set<String> = emptySet(),
  val policyVersion: String,
  val issuedAt: Instant,
  val expiresAt: Instant
) {
  init {
    require(authorizationId.isNotBlank()) { "authorizationId must not be blank" }
    require(taskId.isNotBlank()) { "authorization taskId must not be blank" }
    require(toolId.isNotBlank()) { "authorization toolId must not be blank" }
    require(principalId.isNotBlank()) { "authorization principalId must not be blank" }
    require(action.isNotBlank()) { "authorization action must not be blank" }
    requireDigest(argumentsDigest, "authorization argumentsDigest")
    destinationDigest?.let { requireDigest(it, "authorization destinationDigest") }
    require(grantedPermissions.none { it.isBlank() }) { "authorization grantedPermissions must not contain blanks" }
    require(policyVersion.isNotBlank()) { "authorization policyVersion must not be blank" }
    require(expiresAt.isAfter(issuedAt)) { "authorization expiry must be after issuance" }
  }

  private fun requireDigest(value: String, field: String) {
    require(SHA_256.matches(value)) { "$field must be a lowercase SHA-256 digest" }
  }

  private companion object {
    val SHA_256 = Regex("[a-f0-9]{64}")
  }
}

/**
 * A trusted persistence boundary. consumeIfActive must be atomic so two requests cannot
 * consume the same authorization.
 */
interface ExecutionAuthorizationStore {
  suspend fun find(authorizationId: String): ExecutionAuthorization?
  suspend fun consumeIfActive(authorizationId: String, now: Instant): Boolean
}
