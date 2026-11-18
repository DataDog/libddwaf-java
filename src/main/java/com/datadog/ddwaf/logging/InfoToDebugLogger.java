/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;

public class InfoToDebugLogger extends ForwardLogger {
  // Mirror DDWAF_LOG_LEVEL in libddwaf's ddwaf.h; a _Static_assert in src/main/c/logging.c
  // fails the native build if the values change.
  private static final int DDWAF_LOG_TRACE = 0;
  private static final int DDWAF_LOG_DEBUG = 1;
  private static final int DDWAF_LOG_INFO = 2;
  private static final int DDWAF_LOG_WARN = 3;
  private static final int DDWAF_LOG_ERROR = 4;

  public InfoToDebugLogger(Logger delegate) {
    super(delegate);
  }

  // The next three methods are called from JNI. Their signatures must not
  // mention slf4j types: the native side then needs no slf4j names, and
  // shading relocates every slf4j reference in this class with any prefix.
  static InfoToDebugLogger jniCreate(String name) {
    return new InfoToDebugLogger(LoggerFactory.getLogger(name));
  }

  void jniLog(int level, Throwable t, String fmt, Object[] args) {
    log(toLevel(level), t, fmt, args);
  }

  boolean jniIsLoggable(int level) {
    return isLoggable(toLevel(level));
  }

  private static Level toLevel(int ddwafLevel) {
    switch (ddwafLevel) {
      case DDWAF_LOG_TRACE:
        return Level.TRACE;
      case DDWAF_LOG_DEBUG:
        return Level.DEBUG;
      case DDWAF_LOG_INFO:
        return Level.INFO;
      case DDWAF_LOG_WARN:
        return Level.WARN;
      case DDWAF_LOG_ERROR:
        return Level.ERROR;
      default:
        // unknown levels were logged as DEBUG by the native side; keep that
        return Level.DEBUG;
    }
  }

  // these simplify calls from JNI
  public void log(Level level, Throwable t, String fmt, Object[] args) {
    if (!isLoggable(level)) {
      return;
    }
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
