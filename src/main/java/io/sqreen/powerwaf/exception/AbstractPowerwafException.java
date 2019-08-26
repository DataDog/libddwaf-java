package io.sqreen.powerwaf.exception;

import com.google.common.base.Throwables;

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
                return new NoRulePowerwafException();
            case -2:
                return new InvalidFlowPowerwafException();
            case -3:
                return new InvalidRulePowerwafException();
            case -4:
                return new InvalidCallPowerwafException();
            case -5:
                return new TimeoutPowerwafException();
            case -6:
                return new InternalPowerwafException();
            default:
                return new UnclassifiedPowerwafException(errorCode);
        }
    }
}
