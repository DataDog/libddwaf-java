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
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

public final class Waf {
    public static final String LIB_VERSION = "1.22.0";

    private static final Logger LOGGER = LoggerFactory.getLogger(Waf.class);
    static final boolean EXIT_ON_LEAK;

    private static boolean triedInitializing;
    private static boolean initialized;

    static {
        String exl = System.getProperty("Waf_EXIT_ON_LEAK", "false");
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

    /**
     * Creates a new collection of rules with the default configuration.
     * @param uniqueId a unique id identifying the context. It better be unique!
     * @param ruleDefinitions a map rule name to rule definition
     * @return the new context
     */
    public static WafHandle createHandle(
            String uniqueId, Map<String, Object> ruleDefinitions)
            throws AbstractWafException {
        return new WafHandle(uniqueId, null, ruleDefinitions);
    }

    /**
     * Creates a new collection of rules.
     * @param uniqueId a unique id identifying the builder. It better be unique!
     * @param ruleDefinitions a map rule name to rule definition
     * @param config configuration settings or null for the default
     * @return the new builder
     */
    public static WafHandle createHandle(
            String uniqueId, WafConfig config, Map<String, Object> ruleDefinitions)
            throws AbstractWafException {
        return new WafHandle(uniqueId, config, ruleDefinitions);
    }

    /**
     * Creates a rule given its definition.
     *
     * See also pw_initH.
     *
     * @param definition map with keys version and events
     * @param config configuration for the obfuscator. Non-null.
     * @param rulesetInfoOut either a null or a 1-byte element holding an out
     *                       reference for a {@link RuleSetInfo}.
     * @return a non-null native handle
     * @throws IllegalArgumentException
     */
    static native NativeWafHandle addRules(
            Map<String, Object> definition, WafConfig config, RuleSetInfo[] rulesetInfoOut);

    /* pw_clearRuleH */
    static native void clearRules(NativeWafHandle handle);

    static native String[] getKnownAddresses(NativeWafHandle handle);

    static native String[] getKnownActions(NativeWafHandle handle);

    /**
     * Runs a rule with the parameters pre-serialized into direct
     * ByteBuffers. The initial PWArgs must be the object at offset 0
     * of <code>firstPWArgsBuffer</code>. This object will have pointers
     * to the remaining data, part of which can live in the buffers
     * listed in <code>otherBuffers</code>.
     *
     * See pw_runH.
     *
     * @param handle the Waf rule handle
     * @param firstPWArgsBuffer a buffer whose first object should be top PWArgs
     * @param limits the limits
     * @param metrics the metrics collector, or null
     * @return the resulting action (OK or MATCH) and associated details
     */
    static native ResultWithData runRules(NativeWafHandle handle,
                                          ByteBuffer firstPWArgsBuffer,
                                          Limits limits,
                                          WafMetrics metrics) throws AbstractWafException;

    static native String pwArgsBufferToString(ByteBuffer firstPWArgsBuffer);

    static native NativeWafHandle update(NativeWafHandle handle,
                                         Map<String, Object> specification,
                                         RuleSetInfo[] ruleSetInfoRef);

    public static native String getVersion();

    /**
     * Releases all JNI references, allowing the classloader that loaded the
     * native library to be garbage collected.
     *
     * WARNING: no locking is place to ensure that this deinitialization does
     * not occur while addRule, clearRule and runRule are running. If they are
     * running (in another thread), then a crash may ensue.
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
        public static final ResultWithData OK_NULL = new ResultWithData(Result.OK, null, EMPTY_ACTIONS, null);

        public final Result result;
        public final String data;
        public final Map<String, Map<String, Object>> actions;
        public final Map<String, String> derivatives;

        public ResultWithData(Result result, String data, Map<String, Map<String, Object>> actions, Map<String, String> derivatives) {
            this.result = result;
            this.data = data;
            this.actions = actions;
            this.derivatives = derivatives;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ResultWithData{");
            sb.append("result=").append(result);
            sb.append(", data='").append(data).append('\'');
            sb.append(", actions='").append(Arrays.asList(actions)).append('\'');
            sb.append(", derivatives='").append(derivatives).append('\'');
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


        public Limits(int maxDepth, int maxElements, int maxStringSize, long generalBudgetInUs, long runBudgetInUs) {
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
