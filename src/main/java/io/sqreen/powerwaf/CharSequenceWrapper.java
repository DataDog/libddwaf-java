package io.sqreen.powerwaf;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.CharBuffer;

public class CharSequenceWrapper {
    private static final Field str;
    private static final Field offset;
    private static final Field mark;
    private static final Field position;
    private static final Field limit;
    private static final Field capacity;
    private static boolean resetBuffer;

    static {
        // Get the package private class StringCharBuffer by calling wrap
        str = getField(CharBuffer.wrap("").getClass(), "str");
        offset = getField(CharBuffer.class, "offset");
        mark = getField(Buffer.class, "mark");
        position = getField(Buffer.class, "position");
        limit = getField(Buffer.class, "limit");
        capacity = getField(Buffer.class, "capacity");
        resetBuffer = str != null && offset != null && mark != null && position != null
                && limit != null && capacity != null;
    }

    private static Field getField(Class<?> klass, String name) {
        try {
            Field f = klass.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    public static CharBuffer wrap(CharSequence cs, CharBuffer previous) {
        if (!resetBuffer || previous == null || cs == null) {
            return CharBuffer.wrap(cs);
        } else {
            // Since StringCharBuffer is package private, we can't do an instanceof check above, so we're
            // assuming that this is a StringCharBuffer and catch any exceptions, and don't try again
            try {
                str.set(previous, cs);
                offset.setInt(previous, 0);
                mark.setInt(previous, -1);
                position.setInt(previous, 0);
                int len = cs.length();
                limit.setInt(previous, len);
                capacity.setInt(previous, len);
                return previous;
            } catch (Throwable t) {
                resetBuffer = false;
                return CharBuffer.wrap(cs);
            }
        }
    }
}
