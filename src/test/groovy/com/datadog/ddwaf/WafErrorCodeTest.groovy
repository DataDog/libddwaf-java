package com.datadog.ddwaf

import org.junit.Test
import static org.junit.Assert.assertNull

class WafErrorCodeTest {

    @Test
    void testCorrectCodeForEachErrorCode() {
        assert WafErrorCode.INVALID_ARGUMENT.code == -1
        assert WafErrorCode.INVALID_OBJECT.code == -2
        assert WafErrorCode.INTERNAL_ERROR.code == -3
        assert WafErrorCode.BINDING_ERROR.code == -127
    }

    @Test
    void testCorrectWafErrorCodeFromCode() {
        assert WafErrorCode.fromCode(-1) == WafErrorCode.INVALID_ARGUMENT
        assert WafErrorCode.fromCode(-2) == WafErrorCode.INVALID_OBJECT
        assert WafErrorCode.fromCode(-3) == WafErrorCode.INTERNAL_ERROR
        assert WafErrorCode.fromCode(-127) == WafErrorCode.BINDING_ERROR
        assertNull(WafErrorCode.fromCode(999)) // Unknown code should return null
    }

    @Test
    void testDefinedCodesMap() {
        def codes = WafErrorCode.getDefinedCodes()

        assert codes.size() == 4
        assert codes[-1] == WafErrorCode.INVALID_ARGUMENT
        assert codes[-2] == WafErrorCode.INVALID_OBJECT
        assert codes[-3] == WafErrorCode.INTERNAL_ERROR
        assert codes[-127] == WafErrorCode.BINDING_ERROR
        assert codes instanceof java.util.Collections.UnmodifiableMap
    }
} 