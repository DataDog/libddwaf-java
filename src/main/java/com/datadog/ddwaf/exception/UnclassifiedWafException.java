/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf.exception;

public class UnclassifiedWafException extends AbstractWafException {
    public UnclassifiedWafException(int errorCode) {
        super("Unclassified Waf exception with error code " + errorCode, errorCode);
    }

    public UnclassifiedWafException(String message) {
        super(message, Integer.MIN_VALUE);
    }

    public UnclassifiedWafException(String message, Throwable cause) {
        super(message, Integer.MIN_VALUE, cause);
    }

    public UnclassifiedWafException(Throwable e) {
        this(e.getMessage(), e);
    }
}
