package com.datadog.ddwaf.exception

import com.datadog.ddwaf.WafErrorCode
import org.junit.Test

class UnclassifiedWafExceptionTest {

    @Test
    void testConstructorWithErrorCode() {
        def exception = new UnclassifiedWafException(999)
        assert exception.message == "Unclassified Waf exception with error code 999"
        assert exception.code == 999
    }

    @Test
    void testConstructorWithMessage() {
        def message = "Test error message"
        def exception = new UnclassifiedWafException(message)
        assert exception.message == message
        assert exception.code == WafErrorCode.BINDING_ERROR.getCode()
    }

    @Test
    void testConstructorWithMessageAndCause() {
        def message = "Test error message"
        def cause = new RuntimeException("Test cause")
        def exception = new UnclassifiedWafException(message, cause)
        
        assert exception.message == message
        assert exception.code == WafErrorCode.BINDING_ERROR.getCode()
        assert exception.cause == cause
    }

    @Test
    void testConstructorWithThrowable() {
        def cause = new RuntimeException("Test cause")
        def exception = new UnclassifiedWafException(cause)
        
        assert exception.message == "Test cause"
        assert exception.code == WafErrorCode.BINDING_ERROR.getCode()
        assert exception.cause == cause
    }
} 