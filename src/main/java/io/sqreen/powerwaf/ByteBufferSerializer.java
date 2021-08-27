package io.sqreen.powerwaf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.reflect.Array;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ByteBufferSerializer {
    private static final long NULLPTR = 0;
    private static final int SIZEOF_PWARGS = 40;
    private static final int PWARGS_MIN_SEGMENTS_SIZE = 512;
    private static final int STRINGS_MIN_SEGMENTS_SIZE = 81920;

    private static final Logger LOGGER = LoggerFactory.getLogger(ByteBufferSerializer.class);

    private final Powerwaf.Limits limits;

    public ByteBufferSerializer(Powerwaf.Limits limits) {
        this.limits = limits;
    }

    public ArenaLease serialize(Map<?, ?> map) {
        if (map == null) {
            throw new NullPointerException("map can't be null");
        }

        ArenaLease lease = ArenaPool.INSTANCE.getLease();
        try {
            Arena arena = lease.getArena();
            int[] remainingElements = new int[]{limits.maxElements};

            PWArgsArrayBuffer pwArgsArrayBuffer = arena.allocatePWArgsBuffer(1);
            if (pwArgsArrayBuffer == null) {
                throw new OutOfMemoryError();
            }
            PWArgsBuffer initialValue = pwArgsArrayBuffer.get(0);
            doSerialize(arena, initialValue, null, map, remainingElements, limits.maxDepth);
        } catch (RuntimeException rte) {
            lease.close();
            throw rte;
        } catch (Error error) {
            lease.close();
            throw error;
        }

        return lease;
    }

    private void doSerialize(Arena arena, PWArgsBuffer pwargsSlot, String parameterName,
                             Object value, int[] remainingElements, int depthRemaining) {
        if (parameterName != null && parameterName.length() > limits.maxStringSize) {
            LOGGER.debug("Truncating parameter string from size {} to size {}",
                    parameterName.length(), limits.maxStringSize);
            parameterName = parameterName.substring(0, limits.maxStringSize);
        }

        remainingElements[0]--;

        // RuntimeExceptions thrown should only happen if we get strings with
        // size Integer.MAX_VALUE and the limit size for strings is also
        // Integer.MAXVALUE

        if (remainingElements[0] < 0 || depthRemaining < 0) {
            if (LOGGER.isInfoEnabled()) {
                if (remainingElements[0] < 0) {
                    LOGGER.info("Ignoring element, for maxElements was exceeded");
                } else if (depthRemaining <= 0) {
                    LOGGER.info("Ignoring element, for maxDepth was exceeded");
                }
            }
            // write empty map
            if (pwargsSlot.writeMap(arena, parameterName, 0) == null) {
                throw new RuntimeException("Could not write map");
            }
            return;
        }

        if (value == null) {
            if (pwargsSlot.writeMap(arena, parameterName, 0) == null) {
                throw new RuntimeException("Error writing empty map for null value");
            }
        } else if (value instanceof CharSequence) {
            CharSequence svalue = (CharSequence) value;
            if (svalue.length() > limits.maxStringSize) {
                LOGGER.debug("Truncating string from size {} to size {}",
                        svalue.length(), limits.maxStringSize);
                svalue = svalue.subSequence(0, limits.maxStringSize);
            }
            if (!pwargsSlot.writeString(arena, parameterName, svalue)) {
                throw new RuntimeException("Could not write string");
            }
        } else if (value instanceof Number) {
            if (!pwargsSlot.writeLong(arena, parameterName, ((Number) value).longValue())) {
                throw new RuntimeException("Could not write number");
            }
        } else if (value instanceof Collection) {
            int size = Math.min(((Collection<?>) value).size(), remainingElements[0]);

            Iterator<?> iterator = ((Collection<?>) value).iterator();
            serializeIterable(
                    arena, pwargsSlot, parameterName, remainingElements, depthRemaining, iterator, size);
        } else if (value.getClass().isArray()) {
            int size = Math.min(Array.getLength(value), remainingElements[0]);
            Iterator<?> iterator = new GenericArrayIterator(value);
            serializeIterable(
                    arena, pwargsSlot, parameterName, remainingElements, depthRemaining, iterator, size);
        } else if (value instanceof Iterable) {
            // we need to iterate twice
            Iterator<?> iterator = ((Iterable<?>) value).iterator();
            int size = 0;
            int maxSize = remainingElements[0];
            while (iterator.hasNext() && size < maxSize) {
                iterator.next();
                size++;
            }

            iterator = ((Iterable<?>) value).iterator();
            serializeIterable(
                    arena, pwargsSlot, parameterName, remainingElements, depthRemaining, iterator, size);
        } else if (value instanceof Map) {
            int size = Math.min(((Map<?, ?>) value).size(), remainingElements[0]);

            PWArgsArrayBuffer pwArgsArrayBuffer = pwargsSlot.writeMap(arena, parameterName, size);
            if (pwArgsArrayBuffer == null) {
                throw new RuntimeException("Could not write map");
            }
            int i = 0;
            Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) value).entrySet().iterator();
            for (; iterator.hasNext() && i < size; i++) {
                Map.Entry<?, ?> entry = iterator.next();
                PWArgsBuffer newSlot = pwArgsArrayBuffer.get(i);
                Object key = entry.getKey();
                if (key == null) {
                    key = "";
                }
                doSerialize(arena, newSlot, key.toString(), entry.getValue(),
                        remainingElements, depthRemaining - 1);
            }
            if (i != size) {
                throw new ConcurrentModificationException("i=" + i + ", size=" + size);
            }
        } else if (value instanceof Boolean) {
            String svalue = ((Boolean) value).toString();
            if (!pwargsSlot.writeString(arena, parameterName, svalue)) {
                throw new RuntimeException("Could not write string");
            }
        } else {
            // unknown value; write empty map
            if (pwargsSlot.writeMap(arena, parameterName, 0) == null) {
                throw new RuntimeException("Error writing empty map for unknown type");
            }
        }
    }

    private void serializeIterable(Arena arena,
                                   PWArgsBuffer pwArgsSlot,
                                   String parameterName,
                                   int[] remainingElements,
                                   int depthRemaining,
                                   Iterator<?> iterator,
                                   int size) {
        PWArgsArrayBuffer pwArgsArrayBuffer = pwArgsSlot.writeArray(arena, parameterName, size);
        if (pwArgsArrayBuffer == null) {
            throw new RuntimeException("Error serializing iterable");
        }

        int i;
        for (i = 0; iterator.hasNext() && i < size; i++) {
            Object newObj = iterator.next();
            PWArgsBuffer newSlot = pwArgsArrayBuffer.get(i);
            doSerialize(arena, newSlot, null, newObj, remainingElements, depthRemaining - 1);
        }
        if (i != size) {
            throw new ConcurrentModificationException("i=" + i + ", size=" + size);
        }
    }

    private static class Arena {
        private static final CharsetEncoder CHARSET_ENCODER =
                StandardCharsets.UTF_8.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE) // UTF-8 can represent all though
                        .replaceWith(new byte[] {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD});

        List<PWArgsSegment> pwargsSegments = new ArrayList<>();
        int curPWArgsSegment;
        int idxOfFirstUsedPWArgsSegment = -1;
        List<StringsSegment> stringsSegments = new ArrayList<>();
        int curStringsSegment;

        Arena() {
            pwargsSegments.add(new PWArgsSegment(PWARGS_MIN_SEGMENTS_SIZE));
            stringsSegments.add(new StringsSegment(STRINGS_MIN_SEGMENTS_SIZE));
        }

        void reset() {
            for (PWArgsSegment seg : pwargsSegments) {
                seg.clear();
            }
            for (StringsSegment seg : stringsSegments) {
                seg.clear();
            }
            curPWArgsSegment = 0;
            curStringsSegment = 0;
            idxOfFirstUsedPWArgsSegment = -1;
        }

        ByteBuffer getFirstUsedPWArgsBuffer() {
            if (idxOfFirstUsedPWArgsSegment == -1) {
                // should not happen, because an ArenaLease should not
                // escape with nothing written
                throw new IllegalStateException("No PWArgs written");
            }
            return pwargsSegments.get(idxOfFirstUsedPWArgsSegment).buffer;
        }

        static class WrittenString {
            private final long ptr;
            private final int utf8len;

            WrittenString(long ptr, int utf8len) {
                this.ptr = ptr;
                this.utf8len = utf8len;
            }
        }

        /**
         * @param s the string to serialize
         * @return the native pointer to the string and its size in bytes,
         *         or null if the string is too large
         */
        WrittenString writeStringUnlimited(CharSequence s) {
            ByteBuffer bytes;

            CharBuffer cb = s instanceof CharBuffer ? (CharBuffer) s : CharBuffer.wrap(s);

            try {
                bytes = CHARSET_ENCODER.encode(cb);
            } catch (CharacterCodingException e) {
                // should not happen
                throw new UndeclaredThrowableException(e);
            }
            int lenInBytes = bytes.limit();
            if (lenInBytes == Integer.MAX_VALUE) {
                // overflow ahead
                return null;
            }

            StringsSegment segment;
            segment = stringsSegments.get(curStringsSegment);
            long ptr;
            while ((ptr = segment.writeNulTerminated(bytes)) == NULLPTR) {
                segment = changeStringsSegment(
                        Math.max(STRINGS_MIN_SEGMENTS_SIZE, lenInBytes + 1 /* NUL */));
            }
            return new WrittenString(ptr, lenInBytes);
        }

        PWArgsArrayBuffer allocatePWArgsBuffer(int num) {
            PWArgsSegment segment;
            segment = pwargsSegments.get(curPWArgsSegment);
            PWArgsArrayBuffer array;
            while ((array = segment.allocate(num)) == null) {
                segment = changePWArgsSegment(Math.max(PWARGS_MIN_SEGMENTS_SIZE, num));
            }
            if (idxOfFirstUsedPWArgsSegment == -1) {
                idxOfFirstUsedPWArgsSegment = curPWArgsSegment;
            }
            return array;
        }

        private PWArgsSegment changePWArgsSegment(int capacity) {
            PWArgsSegment e;
            if (curPWArgsSegment == pwargsSegments.size() -1) {
                e = new PWArgsSegment(capacity);
                pwargsSegments.add(e);
            } else {
                e = pwargsSegments.get(curPWArgsSegment + 1);
            }
            curPWArgsSegment++;
            return e;
        }

        private StringsSegment changeStringsSegment(int capacity) {
            StringsSegment s;
            if (curStringsSegment == stringsSegments.size() - 1) {
                s = new StringsSegment(capacity);
                stringsSegments.add(s);
            } else {
                s = stringsSegments.get(curStringsSegment + 1);
            }
            curStringsSegment++;
            return s;
        }
    }

    /* we want to reuse our ByteBuffers because the live off heap */
    enum ArenaPool {
        INSTANCE;

        Deque<Arena> arenas = new ConcurrentLinkedDeque<>();

        ArenaLease getLease() {
            Arena arena = arenas.pollFirst();
            if (arena == null) {
                return new ArenaLease(new Arena());
            } else {
                return new ArenaLease(arena);
            }
        }

    }

    static class ArenaLease implements AutoCloseable, Closeable {
        private boolean closeCalled;
        private final Arena arena;

        ArenaLease(Arena arena) {
            this.arena = arena;
        }

        public Arena getArena() {
            return this.arena;
        }

        public ByteBuffer getFirstPWArgsByteBuffer() {
            return this.arena.getFirstUsedPWArgsBuffer();
        }

        @Override
        public void close() {
            if (closeCalled) {
                return;
            }
            closeCalled = true;
            arena.reset();
            ArenaPool.INSTANCE.arenas.addFirst(arena);
        }
    }


    static class PWArgsSegment {
        ByteBuffer buffer;

        PWArgsSegment(int capacity) {
            // assume this is 8-byte aligned
            this.buffer = ByteBuffer.allocateDirect(SIZEOF_PWARGS * capacity);
            this.buffer.order(ByteOrder.nativeOrder());
        }

        PWArgsArrayBuffer allocate(int num) {
            if (left() < num) {
                return null;
            }
            int position = this.buffer.position();
            ByteBuffer slice = this.buffer.slice();
            slice.order(ByteOrder.nativeOrder()).limit(num * SIZEOF_PWARGS);
            this.buffer.position(position + num * SIZEOF_PWARGS);
            return new PWArgsArrayBuffer(slice, num);
        }

        void clear() {
            buffer.clear();
        }

        private int left() {
            return (buffer.capacity() - buffer.position()) / SIZEOF_PWARGS;
        }
    }

    static class PWArgsArrayBuffer {
        private final ByteBuffer buffer;
        private final int num;

        static final PWArgsArrayBuffer EMPTY_BUFFER = new PWArgsArrayBuffer();

        PWArgsArrayBuffer(ByteBuffer buffer, int num) {
            if (num == 0) {
                throw new IllegalArgumentException();
            }
            this.num = num;
            this.buffer = buffer;
        }

        private PWArgsArrayBuffer() {
            this.num = 0;
            this.buffer = null;
        }

        PWArgsBuffer get(int i) {
            if (i < 0 || i >= num) {
                throw new ArrayIndexOutOfBoundsException();
            }
            ByteBuffer slice = offsetBuffer(this.buffer, i * SIZEOF_PWARGS);
            slice.limit(SIZEOF_PWARGS);
            return new PWArgsBuffer( slice);
        }

        long getAddress() {
            if (buffer == null) {
                return NULLPTR;
            }
            return getByteBufferAddress(buffer);
        }
    }

    /*
     * This is the structure until we get improvements:
     *
     * https://github.com/sqreen/PowerWAF/issues/201
     *
     *  struct _PWArgs
     *  {
     *      const char* parameterName;
     *      uint64_t parameterNameLength;
     *      union
     *      {
     *          const char* stringValue;
     *          uint64_t uintValue;
     *          int64_t intValue;
     *          const PWArgs* array;
     *          const void* rawHandle;
     *      };
     *      uint64_t nbEntries;
     *      PW_INPUT_TYPE type;
     *  };
     */
    static class PWArgsBuffer {
        private final ByteBuffer buffer;

        PWArgsBuffer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        boolean writeString(Arena arena, String parameterName, CharSequence value) {
            if (!putParameterName(arena, parameterName)) { // string too large
                return false;
            }
            Arena.WrittenString writtenString = arena.writeStringUnlimited(value);
            if (writtenString == null) { // string too large
                return false;
            }
            this.buffer.putLong(writtenString.ptr).putLong(writtenString.utf8len)
                    .putInt(PWInputType.PWI_STRING.value);
            return true;
        }

        boolean writeLong(Arena arena, String parameterName, long value) {
            if (!putParameterName(arena, parameterName)) { // string too large
                return false;
            }
            this.buffer.putLong(value).putLong(0)
                    .putInt(PWInputType.PWI_SIGNED_NUMBER.value);
            return true;
        }

        PWArgsArrayBuffer writeArray(Arena arena, String parameterName, int numElements) {
            return writeArrayOrMap(arena, parameterName, numElements, PWInputType.PWI_ARRAY);
        }

        PWArgsArrayBuffer writeMap(Arena arena, String parameterName, int numElements) {
            return writeArrayOrMap(arena, parameterName, numElements, PWInputType.PWI_MAP);
        }

        private PWArgsArrayBuffer writeArrayOrMap(Arena arena, String parameterName, int numElements,
                                                  PWInputType type) {
            if (!putParameterName(arena, parameterName)) { // string too large
                return null;
            }
            if (numElements == 0) {
                this.buffer.putLong(0L).putLong(0L).putInt(type.value);
                return PWArgsArrayBuffer.EMPTY_BUFFER;
            }

            PWArgsArrayBuffer pwArgsArrayBuffer = arena.allocatePWArgsBuffer(numElements);
            if (pwArgsArrayBuffer == null) {
                // should not happen
                return null;
            }
            long address = pwArgsArrayBuffer.getAddress();
            if (address == NULLPTR) {
                // should not happen
                return null;
            }
            this.buffer.putLong(address)
                    .putLong(numElements)
                    .putInt(type.value);
            return pwArgsArrayBuffer;
        }


        private boolean putParameterName(Arena arena, String parameterName) {
            if (parameterName == null) {
                this.buffer.putLong(0L).putLong(0L);
            } else {
                Arena.WrittenString writtenString = arena.writeStringUnlimited(parameterName);
                if (writtenString == null) { // string too large
                    return false;
                }
                this.buffer.putLong(writtenString.ptr).putLong(writtenString.utf8len);
            }

            return true;
        }
    }

    enum PWInputType {
        PWI_INVALID(0),
        PWI_SIGNED_NUMBER(1),
        PWI_UNSIGNED_NUMBER(2),
        PWI_STRING(4),
        PWI_ARRAY(8),
        PWI_MAP(16);

        int value;
        PWInputType(int i) {
            this.value = i;
        }
    }

    static final class StringsSegment {
        private static final byte NUL_TERMINATOR = 0;

        ByteBuffer buffer;
        long base;

        StringsSegment(int capacity) {
            this.buffer = ByteBuffer.allocateDirect(capacity);
            this.buffer.order(ByteOrder.nativeOrder());
            this.base = getByteBufferAddress(this.buffer);
            if (this.base == NULLPTR) {
                throw new IllegalArgumentException("not a direct ByteBuffer");
            }
        }

        void clear() {
            buffer.clear();
        }

        /**
         * Writes a previously truncated UTF-8 string
         * @param stringBytes stringBytes with the truncated UTF-8 string, position 0, limit length
         * @return the pointer value of the written string or NULLPTR,
         *         if there is no space available in the segment
         */
        long writeNulTerminated(ByteBuffer stringBytes) {
            // assume length is not Integer.MAX_INT
            int size = stringBytes.limit() + 1;
            if (left() < size) {
                return NULLPTR;
            }
            long ptr = base + this.buffer.position();
            this.buffer.put(stringBytes).put(NUL_TERMINATOR);
            return ptr;
        }

        private int left() {
            return buffer.capacity() - buffer.position();
        }
    }

    private static native long getByteBufferAddress(ByteBuffer bb);

    private static ByteBuffer offsetBuffer(ByteBuffer buffer, int offsetInBytes) {
        buffer.mark().position(offsetInBytes);
        ByteBuffer slice = buffer.slice();
        slice.order(ByteOrder.nativeOrder());
        buffer.reset();
        return slice;
    }

    private static class GenericArrayIterator implements Iterator<Object> {
        final Object array;
        final int length;
        int pos = 0;

        private GenericArrayIterator(Object array) {
            this.array = array;
            this.length = Array.getLength(array);
        }


        @Override
        public boolean hasNext() {
            return pos < length;
        }

        @Override
        public Object next() {
            return Array.get(this.array, pos++);
        }
    }
}
