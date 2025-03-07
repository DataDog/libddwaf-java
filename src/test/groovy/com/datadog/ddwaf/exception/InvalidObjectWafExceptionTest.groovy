package com.datadog.ddwaf.exception

import com.datadog.ddwaf.WafErrorCode
import org.junit.Test

class InvalidObjectWafExceptionTest {

    @Test
    void testConstructorWithErrorCode() {
        InvalidObjectWafException exception = new InvalidObjectWafException(-2)
        assert exception.message == 'Invalid object'
        assert exception.code == -2
    }

    @Test
    void testErrorCodeFromWafErrorCode() {
        InvalidObjectWafException exception = new InvalidObjectWafException(WafErrorCode.INVALID_OBJECT.code)
        assert exception.message == 'Invalid object'
        assert exception.code == WafErrorCode.INVALID_OBJECT.code
    }
}
