/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf.exception;

public class UnclassifiedPowerwafException extends AbstractPowerwafException {
    public UnclassifiedPowerwafException(int errorCode) {
        super("Unclassified PowerWAF exception with error code " + errorCode, errorCode);
    }

    public UnclassifiedPowerwafException(String message) {
        super(message, Integer.MIN_VALUE);
    }

    public UnclassifiedPowerwafException(String message, Throwable cause) {
        super(message, Integer.MIN_VALUE, cause);
    }

    public UnclassifiedPowerwafException(Exception e) {
        this(e.getMessage(), e);
    }
}
