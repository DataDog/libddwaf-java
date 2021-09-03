package io.sqreen.powerwaf.exception;

public class InvalidArgumentPowerwafException extends AbstractPowerwafException {
    public InvalidArgumentPowerwafException() {
        super("Invalid argument", -2);
    }
}
