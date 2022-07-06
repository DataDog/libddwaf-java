package io.sqreen.powerwaf;

import java.nio.CharBuffer;

public class CharSequenceWrapper {
    private static boolean resetBuffer = true;

    private static native void resetState(CharBuffer previous, CharSequence cs);

    public static CharBuffer wrap(CharSequence cs, CharBuffer previous) {
        if (!resetBuffer || previous == null || cs == null) {
            return CharBuffer.wrap(cs);
        } else {
            // Since StringCharBuffer is package private, we can't do an instanceof check above, so we're
            // assuming that this is a StringCharBuffer and catch any exceptions, and don't try again
            try {
                resetState(previous, cs);
                return previous;
            } catch (Throwable t) {
                resetBuffer = false;
                return CharBuffer.wrap(cs);
            }
        }
    }
}
