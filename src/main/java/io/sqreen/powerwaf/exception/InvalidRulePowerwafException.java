package io.sqreen.powerwaf.exception;

public class InvalidRulePowerwafException extends AbstractPowerwafException {
    public InvalidRulePowerwafException() {
        super("Invalid rule", -3);
    }
}
