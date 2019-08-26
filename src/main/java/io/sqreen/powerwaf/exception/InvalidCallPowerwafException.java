package io.sqreen.powerwaf.exception;

public class InvalidCallPowerwafException extends AbstractPowerwafException {
    public InvalidCallPowerwafException() {
        super("Invalid call", -4);
    }
}
