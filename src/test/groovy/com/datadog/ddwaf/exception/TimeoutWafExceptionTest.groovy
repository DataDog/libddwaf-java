package com.datadog.ddwaf.exception

import org.junit.Test

class TimeoutWafExceptionTest {

    @Test
    void testConstructor() {
        TimeoutWafException exception = new TimeoutWafException()
        assert exception.message == 'Timeout'
        assert exception.code == 0
    }
}
