/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf.exception;

public class UnclassifiedWafException extends AbstractWafException {

    private static final int code = -127;

    public UnclassifiedWafException(int errorCode) {
        super("Unclassified Waf exception with error code " + errorCode, code);
    }

    public UnclassifiedWafException(String message) {
        super(message, code);
    }

    public UnclassifiedWafException(String message, Throwable cause) {
        super(message, code, cause);
    }

    public UnclassifiedWafException(Throwable e) {
        this(e.getMessage(), e);
    }
}
