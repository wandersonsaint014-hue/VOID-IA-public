package io.void.core.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoundedInputValidatorTest {
  @Test fun acceptsSmallStructuredInput() {
    assertNull(BoundedInputValidator.validate(mapOf("title" to "Nota", "tags" to listOf("local"))))
  }

  @Test fun rejectsDepthSizeLengthAndUnsupportedObjects() {
    val limits = InputLimits(maxDepth = 1, maxEntriesPerMap = 1, maxItemsPerCollection = 1, maxStringLength = 3)
    assertEquals("input_string_too_long", BoundedInputValidator.validate(mapOf("x" to "long"), limits))
    assertEquals("input_map_too_large", BoundedInputValidator.validate(mapOf("x" to 1, "y" to 2), limits))
    assertEquals("input_collection_too_large", BoundedInputValidator.validate(mapOf("x" to listOf(1, 2)), limits))
    assertEquals("input_depth_exceeded", BoundedInputValidator.validate(mapOf("x" to mapOf("y" to mapOf("z" to 1))), limits))
    assertEquals("input_map_key_too_long", BoundedInputValidator.validate(mapOf("long" to 1), limits))
    assertEquals("input_number_not_finite", BoundedInputValidator.validate(mapOf("x" to Double.NaN), limits))
    assertEquals("input_type_unsupported", BoundedInputValidator.validate(mapOf("x" to Any()), limits))
  }
}
