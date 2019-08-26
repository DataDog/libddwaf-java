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
}
