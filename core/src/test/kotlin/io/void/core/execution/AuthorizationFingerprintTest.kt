package io.void.core.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AuthorizationFingerprintTest {
  @Test fun normalizesSignedZeroButKeepsDecimalTypesUnambiguous() {
    assertEquals(
      AuthorizationFingerprint.digest(mapOf("value" to 0.0)),
      AuthorizationFingerprint.digest(mapOf("value" to -0.0))
    )
    assertNotEquals(
      AuthorizationFingerprint.digest(mapOf("value" to 1)),
      AuthorizationFingerprint.digest(mapOf("value" to 1.0))
    )
  }

  @Test fun rejectsNonFiniteAndUnsupportedNumbers() {
    assertFailsWith<IllegalArgumentException> { AuthorizationFingerprint.digest(Double.NaN) }
    assertFailsWith<IllegalArgumentException> { AuthorizationFingerprint.digest(Double.POSITIVE_INFINITY) }
    assertFailsWith<IllegalArgumentException> { AuthorizationFingerprint.digest(java.math.BigDecimal.ONE) }
  }
}
