package com.datadog.ddwaf.logging

import org.junit.Test
import org.slf4j.Logger

import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Covers the JNI entry points of InfoToDebugLogger: the DDWAF_LOG_LEVEL int received from native
 * code is mapped to an slf4j level (INFO logged as DEBUG, unknown levels as DEBUG).
 */
class InfoToDebugLoggerTest {

  // DDWAF_LOG_LEVEL values
  private static final int TRACE = 0
  private static final int DEBUG = 1
  private static final int INFO = 2
  private static final int WARN = 3
  private static final int ERROR = 4

  private static final List<String> ALL_LEVELS = ['trace', 'debug', 'info', 'warn', 'error']

  // delegate method reached for each DDWAF_LOG_LEVEL value, including unknown ones
  private static final Map<Integer, String> DELEGATE_METHOD = [
    (TRACE): 'trace',
    (DEBUG): 'debug',
    (INFO) : 'debug',
    (WARN) : 'warn',
    (ERROR): 'error',
    5      : 'debug',
    42     : 'debug',
  ]

  // LOGGING_PATTERN in logging.c and the arguments native code passes with it
  private static final String FMT = '%s (%s on %s:%s)'
  private static final Object[] ARGS = ['message', 'function', 'file.c', '12'] as Object[]
  private static final String FORMATTED = 'message (function on file.c:12)'

  private final List<List<Object>> calls = []

  @Test
  void isLoggableMapsInfoToDebug() {
    def debugOnly = new InfoToDebugLogger(recordingDelegate(['debug']))
    assert !debugOnly.jniIsLoggable(TRACE)
    assert debugOnly.jniIsLoggable(DEBUG)
    assert debugOnly.jniIsLoggable(INFO)
    assert !debugOnly.jniIsLoggable(WARN)
    assert !debugOnly.jniIsLoggable(ERROR)

    def allButDebug = new InfoToDebugLogger(recordingDelegate(ALL_LEVELS - 'debug'))
    assert allButDebug.jniIsLoggable(TRACE)
    assert !allButDebug.jniIsLoggable(DEBUG)
    assert !allButDebug.jniIsLoggable(INFO)
    assert allButDebug.jniIsLoggable(WARN)
    assert allButDebug.jniIsLoggable(ERROR)
  }

  @Test
  void isLoggableTreatsUnknownLevelsAsDebug() {
    def debugOnly = new InfoToDebugLogger(recordingDelegate(['debug']))
    assert debugOnly.jniIsLoggable(5)
    assert debugOnly.jniIsLoggable(42)

    def allButDebug = new InfoToDebugLogger(recordingDelegate(ALL_LEVELS - 'debug'))
    assert !allButDebug.jniIsLoggable(5)
    assert !allButDebug.jniIsLoggable(42)
  }

  @Test
  void logFormatsAndReachesTheMappedDelegateMethod() {
    def logger = new InfoToDebugLogger(recordingDelegate(ALL_LEVELS))
    DELEGATE_METHOD.each { level, method ->
      calls.clear()
      logger.jniLog(level, null, FMT, ARGS)
      assert calls == [[method, '{}', FORMATTED]] : "level ${level}"
    }
  }

  @Test
  void logWithThrowableReachesTheMappedDelegateMethod() {
    def logger = new InfoToDebugLogger(recordingDelegate(ALL_LEVELS))
    def t = new RuntimeException('boom')
    DELEGATE_METHOD.each { level, method ->
      calls.clear()
      logger.jniLog(level, t, FMT, ARGS)
      assert calls == [[method, FORMATTED, t]] : "level ${level}"
    }
  }

  @Test
  void logSkipsLevelsThatAreNotEnabled() {
    DELEGATE_METHOD.each { level, method ->
      // every other delegate level is enabled: INFO must not reach delegate.info
      def logger = new InfoToDebugLogger(recordingDelegate(ALL_LEVELS - method))
      calls.clear()
      logger.jniLog(level, null, FMT, ARGS)
      logger.jniLog(level, new RuntimeException('boom'), FMT, ARGS)
      assert calls.empty : "level ${level}"
    }
  }

  // Records every logging call; is*Enabled() answers from enabledLevels.
  private Logger recordingDelegate(Collection<String> enabledLevels) {
    (Logger) Proxy.newProxyInstance(Logger.classLoader, [Logger] as Class[]) { proxy, Method m, Object[] args ->
      def enabled = m.name =~ /^is(\w+)Enabled$/
      if (enabled) {
        return enabled.group(1).toLowerCase(Locale.ROOT) in enabledLevels
      }
      if (m.name in ['getName', 'toString']) {
        return 'recordingDelegate'
      }
      calls << ([m.name] + (args as List ?: []))
      null
    }
  }
}
