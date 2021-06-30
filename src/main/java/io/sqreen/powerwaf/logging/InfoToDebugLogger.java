package io.sqreen.powerwaf.logging;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;

public class InfoToDebugLogger extends ForwardLogger {
    public InfoToDebugLogger(Logger delegate) {
        super(delegate);
    }

    // these simplify calls from JNI
    public void log(Level level, Throwable t, String fmt, Object[] args) {
        String msg = String.format(fmt, args);
        switch (level) {
            case ERROR:
                if (t != null) {
                    error(msg, t);
                } else {
                    error("{}", msg);
                }
                break;
            case WARN:
                if (t != null) {
                    warn(msg, t);
                } else {
                    warn("{}", msg);
                }
                break;
            case INFO:
                if (t != null) {
                    info(msg, t);
                } else {
                    info("{}", msg);
                }
                break;
            case DEBUG:
                if (t != null) {
                    debug(msg, t);
                } else {
                    debug("{}", msg);
                }
                break;
            case TRACE:
                if (t != null) {
                    trace(msg, t);
                } else {
                    trace("{}", msg);
                }
                break;
        }
    }

    public boolean isLoggable(Level level) {
        switch (level) {
            case ERROR:
                return isErrorEnabled();
            case WARN:
                return isWarnEnabled();
            case INFO:
                return isInfoEnabled();
            case DEBUG:
                return isDebugEnabled();
            case TRACE:
                return isTraceEnabled();
        }
        return false; // unreachable
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return delegate.isDebugEnabled(marker);
    }

    @Override
    public void info(String msg) {
        delegate.debug(msg);
    }

    @Override
    public void info(String format, Object arg) {
        delegate.debug(format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        delegate.debug(format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        delegate.debug(format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        delegate.debug(msg, t);
    }

    @Override
    public void info(Marker marker, String msg) {
        delegate.debug(marker, msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        delegate.debug(marker, format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        delegate.debug(marker, format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        delegate.debug(marker, format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        delegate.debug(marker, msg, t);
    }
}
