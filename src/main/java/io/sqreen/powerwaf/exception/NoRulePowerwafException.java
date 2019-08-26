package io.sqreen.powerwaf.exception;

public class NoRulePowerwafException extends AbstractPowerwafException {
    public NoRulePowerwafException() {
        super("No rule", -1);
    }
}
