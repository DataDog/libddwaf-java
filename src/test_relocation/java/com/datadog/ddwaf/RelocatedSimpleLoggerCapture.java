/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2026 Datadog, Inc.
 */

package com.datadog.ddwaf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Configures the slf4j-simple copy shaded into a relocated binding jar (its system property keys
 * are relocated with the package, e.g. datadog.slf4j.simpleLogger.*) to log everything to a temp
 * file, so a test can check that native logs reach the relocated slf4j.
 */
final class RelocatedSimpleLoggerCapture implements AutoCloseable {
  private static final String NATIVE_LOGGER = "ddwaf_native";

  private final String prefix;
  private final File logFile;

  /** Must run before the relocated slf4j is first used: slf4j-simple reads its config once. */
  RelocatedSimpleLoggerCapture(String prefix) throws IOException {
    this.prefix = prefix;
    this.logFile = File.createTempFile("relocated-slf4j-simple", ".log");
    System.setProperty(prefix + ".simpleLogger.defaultLogLevel", "trace");
    System.setProperty(prefix + ".simpleLogger.logFile", logFile.getAbsolutePath());
  }

  /** Without a binding the relocated LoggerFactory returns a NOPLogger and nothing is forwarded. */
  void assertRelocatedLoggerIsSimpleLogger(ClassLoader cl) throws Exception {
    Object logger =
        Class.forName(prefix + ".LoggerFactory", true, cl)
            .getMethod("getLogger", String.class)
            .invoke(null, NATIVE_LOGGER);
    assertThat(
        "the relocated LoggerFactory must return the shaded slf4j-simple logger",
        logger.getClass().getName(),
        is(prefix + ".impl.SimpleLogger"));
  }

  /** Waf.deinitialize logs "Deinitializing JNI library" at DEBUG from native before teardown. */
  void assertDeinitializeLogIsForwarded(Class<?> waf) throws Exception {
    try {
      waf.getMethod("deinitialize").invoke(null);
    } catch (InvocationTargetException e) {
      throw new AssertionError("Waf.deinitialize failed", e.getCause());
    }
    List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
    String expected = "DEBUG " + NATIVE_LOGGER + " - Deinitializing JNI library";
    assertTrue(
        "no line containing '" + expected + "' in " + lines,
        lines.stream().anyMatch(l -> l.contains(expected)));
  }

  @Override
  public void close() {
    System.clearProperty(prefix + ".simpleLogger.defaultLogLevel");
    System.clearProperty(prefix + ".simpleLogger.logFile");
    logFile.delete();
  }
}
