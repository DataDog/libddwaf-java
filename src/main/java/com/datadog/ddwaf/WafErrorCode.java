package com.datadog.ddwaf;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum WafErrorCode {
    INVALID_ARGUMENT(-1),
    INVALID_OBJECT(-2),
    INTERNAL_ERROR(-3),
    BINDING_ERROR(-127); // This is a special error code that is not returned by the WAF, is used to signal a binding error

    private final int code;

    private static final Map<Integer, WafErrorCode> CODE_MAP;

    static {
        Map<Integer, WafErrorCode> map = new HashMap<>();
        for (WafErrorCode errorCode : values()) {
            map.put(errorCode.code, errorCode);
        }
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    WafErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static WafErrorCode fromCode(int code) {
        return CODE_MAP.get(code);
    }

    public static Map<Integer, WafErrorCode> getDefinedCodes() {
        return CODE_MAP;
    }
}

