package io.sqreen.powerwaf;

import io.sqreen.logging.Logger;
import io.sqreen.logging.LoggerFactory;
import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;
import io.sqreen.powerwaf.exception.UnsupportedVMException;

import java.io.IOException;
import java.util.Map;

public final class Powerwaf {
    private final static Logger LOGGER = LoggerFactory.get(Powerwaf.class);

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
                    "Previously loading attempt of powerwaf_jni failed; not retrying");
        }

        triedInitializing = true;
        try {
            if (simple) {
                System.loadLibrary("powerwaf_jni");
            } else {
                NativeLibLoader.load();
            }
        } catch (IOException e) {
            LOGGER.error(e, "Failure loading native library: %s", e.getMessage());
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
    native static boolean addRule(String ruleName, String definition);
    native static void clearRule(String ruleName);

    native static ActionWithData runRule(String ruleName,
                                         Map<String, Object> parameters,
                                         long timeLeftInUs) throws AbstractPowerwafException;

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
}
