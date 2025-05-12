package com.datadog.ddwaf.exception

import com.datadog.ddwaf.WafErrorCode
import org.junit.Test
import static org.junit.Assert.fail

class AbstractWafExceptionTest {

  @Test
  void testCreateCorrectExceptionTypesFromErrorCodes() {
    assert AbstractWafException.createFromErrorCode(-1) instanceof InvalidArgumentWafException
    assert AbstractWafException.createFromErrorCode(-2) instanceof InvalidObjectWafException
    assert AbstractWafException.createFromErrorCode(-3) instanceof InternalWafException
    assert AbstractWafException.createFromErrorCode(999) instanceof UnclassifiedWafException
  }

  @Test
  void testSetCorrectErrorCodeInCreatedExceptions() {
    assert AbstractWafException.createFromErrorCode(-1).code == -1
    assert AbstractWafException.createFromErrorCode(-2).code == -2
    assert AbstractWafException.createFromErrorCode(-3).code == -3
    assert AbstractWafException.createFromErrorCode(999).code == 999
  }

  @Test
  void testSetCorrectMessageInCreatedExceptions() {
    assert AbstractWafException.createFromErrorCode(-1).message == 'Invalid argument'
    assert AbstractWafException.createFromErrorCode(-2).message == 'Invalid object'
    assert AbstractWafException.createFromErrorCode(-3).message == 'Internal error'
    assert AbstractWafException.createFromErrorCode(999).message == 'Unclassified Waf exception with error code 999'
  }

  @Test
  void testThrowIllegalStateExceptionForUnhandledWafErrorCode() {
    /*
     * We use BINDING_ERROR here as our test case because:
     * 1. It's a special error code (-127) that by design is never returned by the WAF itself
     * 2. It's only used to signal JNI binding-related issues
     * 3. It's intentionally not handled in AbstractWafException.createFromErrorCode's switch statement
     * This makes it perfect for testing the unhandled enum case without risking interference
     * with actual WAF error scenarios.
     */
    try {
      AbstractWafException.createFromErrorCode(WafErrorCode.BINDING_ERROR.code)
      fail('Expected IllegalStateException to be thrown')
    } catch (IllegalStateException e) {
      assert e.message == 'Unhandled WafErrorCode: BINDING_ERROR'
    }
  }
}
