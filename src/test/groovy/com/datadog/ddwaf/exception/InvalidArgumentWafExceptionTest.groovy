package com.datadog.ddwaf.exception

import com.datadog.ddwaf.WafErrorCode
import org.junit.Test

class InvalidArgumentWafExceptionTest {

    @Test
    void testConstructorWithErrorCode() {
        InvalidArgumentWafException exception = new InvalidArgumentWafException(-1)
        assert exception.message == 'Invalid argument'
        assert exception.code == -1
    }

    @Test
    void testErrorCodeFromWafErrorCode() {
        InvalidArgumentWafException exception = new InvalidArgumentWafException(WafErrorCode.INVALID_ARGUMENT.code)
        assert exception.message == 'Invalid argument'
        assert exception.code == WafErrorCode.INVALID_ARGUMENT.code
    }
}
