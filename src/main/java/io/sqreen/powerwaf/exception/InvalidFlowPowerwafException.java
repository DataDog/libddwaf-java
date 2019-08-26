package io.sqreen.powerwaf.exception;

public class InvalidFlowPowerwafException extends AbstractPowerwafException {
    public InvalidFlowPowerwafException() {
        super("Invalid flow", -2);
    }
}
