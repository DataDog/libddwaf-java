package com.datadog.ddwaf.exception

import com.datadog.ddwaf.WafErrorCode
import org.junit.Test

class InternalWafExceptionTest {

    @Test
    void testConstructorWithErrorCode() {
        InternalWafException exception = new InternalWafException(-3)
        assert exception.message == 'Internal error'
        assert exception.code == -3
    }

    @Test
    void testErrorCodeFromWafErrorCode() {
        InternalWafException exception = new InternalWafException(WafErrorCode.INTERNAL_ERROR.code)
        assert exception.message == 'Internal error'
        assert exception.code == WafErrorCode.INTERNAL_ERROR.code
    }
}
