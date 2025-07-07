/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf;

import com.datadog.ddwaf.exception.AbstractWafException;
import com.datadog.ddwaf.exception.UnclassifiedWafException;
import com.datadog.ddwaf.exception.UnsupportedVMException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Waf {
  public static final String LIB_VERSION = "1.26.0";

  private static final Logger LOGGER = LoggerFactory.getLogger(Waf.class);
  static final boolean EXIT_ON_LEAK;

  private static boolean triedInitializing;
  private static boolean initialized;

  static {
    String exl = System.getProperty("DD_APPSEC_DDWAF_EXIT_ON_LEAK", "false");
    EXIT_ON_LEAK = !exl.equalsIgnoreCase("false");
  }

  private Waf() {}

  public static synchronized void initialize(boolean simple)
      throws AbstractWafException, UnsupportedVMException {
    if (initialized) {
      return;
    }

    if (triedInitializing) {
      throw new UnclassifiedWafException(
          "Previously loading attempt of sqreen_jni failed; not retrying");
    }

    triedInitializing = true;
    try {
      if (simple) {
        System.loadLibrary("sqreen_jni");
      } else {
        NativeLibLoader.load();
      }
    } catch (IOException e) {
      LOGGER.error("Failure loading native library", e);
      throw new RuntimeException("Error loading native lib", e);
    }
    initialized = true;
  }

  /** (FOR TESTING PURPOSES ONLY) Converts a ByteBuffer to a String. */
  static native String pwArgsBufferToString(ByteBuffer firstPWArgsBuffer);

  public static native String getVersion();

  /**
   * Releases all JNI references, allowing the classloader that loaded the native library to be
   * garbage collected.
   *
   * <p>WARNING: no locking is place to ensure that this deinitialization does not occur while
   * addRule, clearRule and runRule are running. If they are running (in another thread), then a
   * crash may ensue.
   */
  public static native void deinitialize();

  // called from JNI
  private static AbstractWafException createException(int retCode) {
    return AbstractWafException.createFromErrorCode(retCode);
  }

  public enum Result {
    // there references to these static fields on native code
    OK(0),
    MATCH(1);

    public final int code;

    Result(int code) {
      this.code = code;
    }
  }

  public static class ResultWithData {
    // used also from JNI
    private static final Map<String, Map<String, Object>> EMPTY_ACTIONS = Collections.emptyMap();

    // reuse this from JNI when there is no actions or data
    public static final ResultWithData OK_NULL =
        new ResultWithData(Result.OK, null, EMPTY_ACTIONS, null, false, 0, false);

    public final Result result;
    public final String data;
    public final Map<String, Map<String, Object>> actions;
    public final Map<String, Object> attributes;
    public final boolean keep;
    public final long duration; // in nanoseconds
    public final boolean events;

    public ResultWithData(
        Result result,
        String data,
        Map<String, Map<String, Object>> actions,
        Map<String, Object> attributes,
        boolean keep,
        long duration,
        boolean events) {
      this.result = result;
      this.data = data;
      this.actions = actions;
      this.attributes = attributes;
      this.keep = keep;
      this.duration = duration;
      this.events = events;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("ResultWithData{");
      sb.append("result=").append(result);
      sb.append(", keep=").append(keep);
      sb.append(", data='").append(data).append('\'');
      sb.append(", actions='").append(Arrays.asList(actions)).append('\'');
      sb.append(", attributes='").append(attributes).append('\'');
      sb.append(", events=").append(events);
      sb.append('}');
      return sb.toString();
    }
  }

  public static class Limits {
    public final int maxDepth;
    public final int maxElements;
    public final int maxStringSize;
    public final long generalBudgetInUs;
    public final long runBudgetInUs; // <= 0

    public Limits(
        int maxDepth,
        int maxElements,
        int maxStringSize,
        long generalBudgetInUs,
        long runBudgetInUs) {
      this.maxDepth = maxDepth;
      this.maxElements = maxElements;
      this.maxStringSize = maxStringSize;
      this.generalBudgetInUs = generalBudgetInUs;
      this.runBudgetInUs = runBudgetInUs;
    }

    public Limits reduceBudget(long amountInUs) {
      long newBudget = generalBudgetInUs - amountInUs;
      if (newBudget < 0) {
        newBudget = 0;
      }
      return new Limits(maxDepth, maxElements, maxStringSize, newBudget, runBudgetInUs);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Limits{");
      sb.append("maxDepth=").append(maxDepth);
      sb.append(", maxElements=").append(maxElements);
      sb.append(", maxStringSize=").append(maxStringSize);
      sb.append(", generalBudgetInUs=").append(generalBudgetInUs);
      sb.append(", runBudgetInUs=").append(runBudgetInUs);
      sb.append('}');
      return sb.toString();
    }
  }
}
