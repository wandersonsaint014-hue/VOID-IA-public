package io.void.core.execution

/** Bounds data accepted from a model or UI before it is canonicalized or handed to a tool. */
data class InputLimits(
  val maxDepth: Int = 8,
  val maxEntriesPerMap: Int = 64,
  val maxItemsPerCollection: Int = 64,
  val maxStringLength: Int = 4_096
) {
  init {
    require(maxDepth > 0 && maxEntriesPerMap > 0 && maxItemsPerCollection > 0 && maxStringLength > 0)
  }
}

object BoundedInputValidator {
  fun validate(value: Any?, limits: InputLimits = InputLimits()): String? = visit(value, limits, 0)

  private fun visit(value: Any?, limits: InputLimits, depth: Int): String? {
    if (depth > limits.maxDepth) return "input_depth_exceeded"
    return when (value) {
      null, is Boolean, is Byte, is Short, is Int, is Long -> null
      is Float -> if (value.isFinite()) null else "input_number_not_finite"
      is Double -> if (value.isFinite()) null else "input_number_not_finite"
      is String -> if (value.length <= limits.maxStringLength) null else "input_string_too_long"
      is Map<*, *> -> {
        if (value.size > limits.maxEntriesPerMap) return "input_map_too_large"
        value.entries.firstNotNullOfOrNull { entry ->
          val key = entry.key
          when {
            key !is String || key.isBlank() -> "input_map_key_invalid"
            key.length > limits.maxStringLength -> "input_map_key_too_long"
            else -> visit(entry.value, limits, depth + 1)
          }
        }
      }
      is List<*> -> {
        if (value.size > limits.maxItemsPerCollection) return "input_collection_too_large"
        value.firstNotNullOfOrNull { visit(it, limits, depth + 1) }
      }
      is Array<*> -> {
        if (value.size > limits.maxItemsPerCollection) return "input_collection_too_large"
        value.firstNotNullOfOrNull { visit(it, limits, depth + 1) }
      }
      else -> "input_type_unsupported"
    }
  }
}
