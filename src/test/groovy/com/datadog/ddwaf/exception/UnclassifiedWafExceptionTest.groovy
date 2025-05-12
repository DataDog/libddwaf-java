package com.datadog.ddwaf.exception

import com.datadog.ddwaf.WafErrorCode
import org.junit.Test

class UnclassifiedWafExceptionTest {

  @Test
  void testConstructorWithErrorCode() {
    UnclassifiedWafException exception = new UnclassifiedWafException(999)
    assert exception.message == 'Unclassified Waf exception with error code 999'
    assert exception.code == 999
  }

  @Test
  void testConstructorWithMessage() {
    String message = 'Test error message'
    UnclassifiedWafException exception = new UnclassifiedWafException(message)
    assert exception.message == message
    assert exception.code == WafErrorCode.BINDING_ERROR.code
  }

  @Test
  void testConstructorWithMessageAndCause() {
    String message = 'Test error message'
    RuntimeException cause = new RuntimeException('Test cause')
    UnclassifiedWafException exception = new UnclassifiedWafException(message, cause)
    assert exception.message == message
    assert exception.code == WafErrorCode.BINDING_ERROR.code
    assert exception.cause == cause
  }

  @Test
  void testConstructorWithThrowable() {
    RuntimeException cause = new RuntimeException('Test cause')
    UnclassifiedWafException exception = new UnclassifiedWafException(cause)
    assert exception.message == 'Test cause'
    assert exception.code == WafErrorCode.BINDING_ERROR.code
    assert exception.cause == cause
  }
}
