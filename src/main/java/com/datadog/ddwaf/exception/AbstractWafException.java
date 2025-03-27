/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf.exception;

import com.datadog.ddwaf.WafErrorCode;

public abstract class AbstractWafException extends Exception {
    public final int code;

    public AbstractWafException(String message, int code) {
        super(message);
        this.code = code;
    }

    public AbstractWafException(String message, int code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static AbstractWafException createFromErrorCode(int errorCode) {
        WafErrorCode wafErrorCode = WafErrorCode.fromCode(errorCode);

        // If the error code is not defined in the enum, return a generic exception
        if (wafErrorCode == null) {
            return new UnclassifiedWafException(errorCode);
        }

        switch (wafErrorCode) {
            case INVALID_ARGUMENT:
                return new InvalidArgumentWafException(errorCode);
            case INVALID_OBJECT:
                return new InvalidObjectWafException(errorCode);
            case INTERNAL_ERROR:
                return new InternalWafException(errorCode);
        }

        // This point should never be reached unless a new enum value is added and not handled above
        throw new IllegalStateException("Unhandled WafErrorCode: " + wafErrorCode);
    }
}
