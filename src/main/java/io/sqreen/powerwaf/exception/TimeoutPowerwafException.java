package io.sqreen.powerwaf.exception;

public class TimeoutPowerwafException extends AbstractPowerwafException {
    public TimeoutPowerwafException() {
        super("Timeout", -1);
    }
}
