/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf.exception;

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
        switch (errorCode) {
            case -1:
                return new InvalidArgumentWafException(errorCode);
            case -2:
                return new InvalidObjectWafException(errorCode);
            case -3:
                return new InternalWafException(errorCode);
            default:
                return new UnclassifiedWafException(errorCode);
        }
    }
}
