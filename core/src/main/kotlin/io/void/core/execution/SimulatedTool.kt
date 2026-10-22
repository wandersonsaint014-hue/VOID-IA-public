package io.void.core.execution

import io.void.core.contract.Tool

/**
 * Explicit marker for tools with no external side effects.
 *
 * The first runtime only accepts this type. A Tool implementation alone is never enough
 * to make network, account, file-system or device effects available.
 */
interface SimulatedTool : Tool
