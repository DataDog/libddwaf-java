package com.datadog.ddwaf.exception

import org.junit.Test

class UnsupportedVMExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = 'Unsupported VM environment'
        UnsupportedVMException exception = new UnsupportedVMException(message)

        assert exception.message == message
        assert exception.cause == null
    }
}
