package io.sqreen.powerwaf;

import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;
import io.sqreen.powerwaf.exception.UnsupportedVMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public final class Powerwaf {
    public static final String LIB_VERSION = "1.0.6";

    private static final Logger LOGGER = LoggerFactory.getLogger(Powerwaf.class);

    private static boolean triedInitializing;
    private static boolean initialized;

    private Powerwaf() {}

    public static synchronized void initialize(boolean simple)
            throws AbstractPowerwafException, UnsupportedVMException {
        if (initialized) {
            return;
        }

        if (triedInitializing) {
            throw new UnclassifiedPowerwafException(
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
     * Creates a new collection of rules.
     * @param uniqueId a unique id identifying the context. It better be unique!
     * @param ruleDefinitions a map rule name => rule definition
     * @return the new context
     */
    public static PowerwafContext createContext(
            String uniqueId, Map<String, String> ruleDefinitions) throws AbstractPowerwafException {
        return new PowerwafContext(uniqueId, ruleDefinitions);
    }

    // maps to powerwaf_initializePowerWAF
    static native boolean addRule(String ruleName, String definition);
    static native void clearRule(String ruleName);

    static native ActionWithData runRule(String ruleName,
                                         Map<String, Object> parameters,
                                         Limits limits) throws AbstractPowerwafException;

    /**
     * Runs a rule with the parameters pre-serialized into direct
     * ByteBuffers. The initial PWArgs must be the object at offset 0
     * of <code>firstPWArgsBuffer</code>. This object will have pointers
     * to the remaining data, part of which can live in the buffers
     * listed in <code>otherBuffers</code>.
     *
     * @param ruleName the rule name
     * @param firstPWArgsBuffer a buffer whose first object should be top PWArgs
     * @param limits the limits
     * @return the resulting action (OK, MONITOR, BLOCK) and associated details
     */
    static native ActionWithData runRule(String ruleName,
                                         ByteBuffer firstPWArgsBuffer,
                                         Limits limits);

    static native String pwArgsBufferToString(ByteBuffer firstPWArgsBuffer);

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

    private static AbstractPowerwafException createException(int retCode) {
        return AbstractPowerwafException.createFromErrorCode(retCode);
    }

    public enum Action {
        // there references to these static fields on native code
        OK(0),
        MONITOR(1),
        BLOCK(2);

        public final int code;

        Action(int code) {
            this.code = code;
        }
    }

    public static class ActionWithData {
        public final Action action;
        public final String data;


        public ActionWithData(Action action, String data) {
            this.action = action;
            this.data = data;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ActionWithData{");
            sb.append("action=").append(action);
            sb.append(", data='").append(data).append('\'');
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
