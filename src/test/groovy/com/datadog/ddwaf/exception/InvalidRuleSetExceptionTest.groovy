package com.datadog.ddwaf.exception

import com.datadog.ddwaf.WafDiagnostics
import com.datadog.ddwaf.WafErrorCode
import org.junit.Test

class InvalidRuleSetExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = 'Invalid ruleset error'
        // Using null for WafDiagnostics
        WafDiagnostics diagnostics = null

        InvalidRuleSetException exception = new InvalidRuleSetException(diagnostics, message)

        assert exception.message == message
        assert exception.code == WafErrorCode.BINDING_ERROR.code
        assert exception.wafDiagnostics == diagnostics
    }

    @Test
    void testConstructorWithThrowable() {
        RuntimeException cause = new RuntimeException('Test cause')
        // Using null for WafDiagnostics
        WafDiagnostics diagnostics = null

        InvalidRuleSetException exception = new InvalidRuleSetException(diagnostics, cause)

        assert exception.message == 'Test cause'
        assert exception.code == WafErrorCode.BINDING_ERROR.code
        assert exception.cause == cause
        assert exception.wafDiagnostics == diagnostics
    }
}
