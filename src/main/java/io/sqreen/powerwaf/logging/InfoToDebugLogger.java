package io.sqreen.powerwaf.logging;

import io.sqreen.logging.ForwardLogger;
import io.sqreen.logging.Level;
import io.sqreen.logging.Logger;

public class InfoToDebugLogger extends ForwardLogger {
    public InfoToDebugLogger(Logger delegate) {
        super(delegate);
    }

    @Override
    public boolean isLoggable(Level level) {
        Level newLevel = level == Level.INFO ? level.DEBUG : level;
        return this.delegate.isLoggable(newLevel);
    }

    @Override
    public void log(Level level, Throwable t, String format, Object... args) {
        Level newLevel = level == Level.INFO ? level.DEBUG : level;
        this.delegate.log(newLevel, t, format, args);
    }
}
