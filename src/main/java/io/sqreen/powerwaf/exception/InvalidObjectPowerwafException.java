package io.sqreen.powerwaf.exception;

public class InvalidObjectPowerwafException extends AbstractPowerwafException {
    public InvalidObjectPowerwafException() {
        super("Invalid object", -3);
    }
}
