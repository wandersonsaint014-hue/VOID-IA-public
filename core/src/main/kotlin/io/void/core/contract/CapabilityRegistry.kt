package io.void.core.contract

import io.void.core.model.CapabilityDescriptor

/** Allowlist source for capabilities. Unknown capabilities are never executable. */
interface CapabilityRegistry {
  fun get(capabilityId: String): CapabilityDescriptor?
  fun listForTool(toolId: String): List<CapabilityDescriptor>
}
