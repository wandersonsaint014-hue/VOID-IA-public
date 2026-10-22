package io.void.core.execution

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Produces stable SHA-256 bindings without retaining raw arguments in audit data. */
object AuthorizationFingerprint {
  fun arguments(inputs: Map<String, Any>): String = digest(inputs)
  fun destination(destination: String?): String? = destination?.let(::digest)

  fun digest(value: Any?): String {
    val bytes = canonical(value).toByteArray(StandardCharsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
      String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
    }
  }

  private fun decimal(value: Double): String {
    require(value.isFinite()) { "authorization decimals must be finite" }
    val normalized = if (value == 0.0) 0.0 else value
    return "decimal:${java.lang.Double.toString(normalized)}"
  }

  private fun canonical(value: Any?): String = when (value) {
    null -> "null"
    is String -> "string:${value.length}:$value"
    is Boolean -> "boolean:$value"
    is Byte, is Short, is Int, is Long -> "integer:$value"
    is Float -> decimal(value.toDouble())
    is Double -> decimal(value)
    is Number -> throw IllegalArgumentException("unsupported authorization number type: ${value::class.qualifiedName}")
    is Map<*, *> -> {
      val entries = value.entries.map { entry ->
        val key = entry.key as? String ?: throw IllegalArgumentException("authorization maps require string keys")
        key to canonical(entry.value)
      }.sortedBy { it.first }
      entries.joinToString(prefix = "map:{", postfix = "}", separator = ",") { (key, nested) ->
        "${key.length}:$key=$nested"
      }
    }
    is Iterable<*> -> value.joinToString(prefix = "list:[", postfix = "]", separator = ",") { canonical(it) }
    is Array<*> -> value.joinToString(prefix = "array:[", postfix = "]", separator = ",") { canonical(it) }
    else -> throw IllegalArgumentException("unsupported authorization value type: ${value::class.qualifiedName}")
  }
}
