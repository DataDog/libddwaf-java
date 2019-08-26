package io.sqreen.powerwaf.exception;

public class InternalPowerwafException extends AbstractPowerwafException {
    public InternalPowerwafException() {
        super("Internal error", -6);
    }
}
