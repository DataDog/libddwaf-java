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
                return new TimeoutPowerwafException();
            case -2:
                return new InvalidArgumentPowerwafException();
            case -3:
                return new InvalidObjectPowerwafException();
            case -4:
                return new InternalPowerwafException();
            default:
                return new UnclassifiedPowerwafException(errorCode);
        }
    }
}
