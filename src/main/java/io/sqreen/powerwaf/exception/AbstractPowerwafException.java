/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf.exception;

public abstract class AbstractPowerwafException extends Exception {
    public final int code;

    public AbstractPowerwafException(String message, int code) {
        super(message);
        this.code = code;
    }

    public AbstractPowerwafException(String message, int code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static AbstractPowerwafException createFromErrorCode(int errorCode) {
        switch (errorCode) {
            case -1:
                return new InvalidArgumentPowerwafException();
            case -2:
                return new InvalidObjectPowerwafException();
            case -3:
                return new InternalPowerwafException();
            default:
                return new UnclassifiedPowerwafException(errorCode);
        }
    }
}
