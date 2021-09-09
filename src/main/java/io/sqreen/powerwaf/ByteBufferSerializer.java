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
    private static final int SIZEOF_PWARGS = 16;
    private static final int SIZEOF_KVPAIR = 32;
    private static final int MAIN_MIN_SEGMENTS_SIZE = 512 * SIZEOF_KVPAIR;
    private static final int STRINGS_MIN_SEGMENTS_SIZE = 81920;
    private static final int MAX_SMALL_STRING_SIZE = 10;
    private static final byte NUL_TERMINATOR = 0;

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
            serializeMore(lease, this.limits, map);
        } catch (RuntimeException | Error rte) {
            lease.close();
            throw rte;
        }

        return lease;
    }

    public static ArenaLease getBlankLease() {
        return ArenaPool.INSTANCE.getLease();
    }

    private static ByteBuffer serializeMore(ArenaLease lease, Powerwaf.Limits limits, Map<?, ?> map) {
        Arena arena = lease.getArena();
        // limits apply per-serialization run
        int[] remainingElements = new int[]{limits.maxElements};

        PWArgsArrayBuffer pwArgsArrayBuffer = arena.allocatePWArgsBuffer((char)1);
        if (pwArgsArrayBuffer == null) {
            throw new OutOfMemoryError();
        }
        PWArgsBuffer initialValue = pwArgsArrayBuffer.get(0);
        doSerialize(arena, limits, initialValue, map, remainingElements, limits.maxDepth);
        return initialValue.buffer;

        // if it threw somewhere, the arena will have elements that are never used
        // they will only be released when the lease is closed
    }

    private static void doSerialize(Arena arena, Powerwaf.Limits limits,
                                    PWArgsBuffer pwargsSlot,
                                    Object value, int[] remainingElements,
                                    int depthRemaining) {
        remainingElements[0]--;

        if (remainingElements[0] < 0 || depthRemaining < 0) {
            if (LOGGER.isInfoEnabled()) {
                if (remainingElements[0] < 0) {
                    LOGGER.info("Ignoring element, for maxElements was exceeded");
                } else if (depthRemaining <= 0) {
                    LOGGER.info("Ignoring element, for maxDepth was exceeded");
                }
            }
            // write empty map
            pwargsSlot.writeMap(arena, (char) 0); // can't fail
            return;
        }

        if (value == null) {
            pwargsSlot.writeMap(arena, (char) 0); // can't fail
        } else if (value instanceof CharSequence) {
            CharSequence svalue = (CharSequence) value;
            if (svalue.length() > limits.maxStringSize) {
                LOGGER.debug("Truncating string from size {} to size {}",
                        svalue.length(), limits.maxStringSize);
                svalue = svalue.subSequence(0, limits.maxStringSize);
            }
            if (!pwargsSlot.writeString(arena, svalue)) {
                throw new RuntimeException("Could not write string");
            }
        } else if (value instanceof Number) {
            pwargsSlot.writeLong(((Number) value).longValue());
        } else if (value instanceof Collection) {
            int sizeInt = Math.min(((Collection<?>) value).size(), remainingElements[0]);
            if (sizeInt < 0) {
                throw new RuntimeException("Negative collection size");
            }
            char size = capToUnsignedShort(sizeInt);

            Iterator<?> iterator = ((Collection<?>) value).iterator();
            serializeArray(
                    arena, limits, pwargsSlot,
                    remainingElements, depthRemaining, iterator, size);
        } else if (value.getClass().isArray()) {
            int sizeInt = Math.min(Array.getLength(value), remainingElements[0]);
            char size = capToUnsignedShort(sizeInt);

            Iterator<?> iterator = new GenericArrayIterator(value);
            serializeArray(
                    arena, limits, pwargsSlot, remainingElements,
                    depthRemaining, iterator, size);
        } else if (value instanceof Iterable) {
            // we need to iterate twice
            Iterator<?> iterator = ((Iterable<?>) value).iterator();
            int maxSize = remainingElements[0];
            char size = 0;
            while (iterator.hasNext() && size < maxSize && size <= Character.MAX_VALUE) {
                iterator.next();
                size++;
            }

            iterator = ((Iterable<?>) value).iterator();
            serializeArray(
                    arena, limits, pwargsSlot,
                    remainingElements, depthRemaining, iterator, size);
        } else if (value instanceof Map) {
            serializeMap(arena, limits, pwargsSlot, remainingElements, depthRemaining, (Map)value);
        } else if (value instanceof Boolean) {
            String svalue = ((Boolean) value).toString();
            if (!pwargsSlot.writeString(arena, svalue)) {
                throw new RuntimeException("Could not write string");
            }
        } else {
            // unknown value; write empty map
            LOGGER.info("Unsupported type for serialization: {}", value.getClass().getTypeName());
            pwargsSlot.writeMap(arena, (char) 0); // can't fail
        }
    }

    private static char capToUnsignedShort(int sizeInt) {
        return sizeInt > Character.MAX_VALUE ? Character.MAX_VALUE : (char) sizeInt;
    }

    private static void serializeArray(Arena arena,
                                       Powerwaf.Limits limits,
                                       PWArgsBuffer pwArgsSlot,
                                       int[] remainingElements,
                                       int depthRemaining,
                                       Iterator<?> iterator,
                                       char size) {
        PWArgsArrayBuffer pwArgsArrayBuffer = pwArgsSlot.writeArray(arena, size);
        if (pwArgsArrayBuffer == null) {
            throw new RuntimeException("Could not allocate array for serializing iterable");
        }

        char i;
        for (i = 0; iterator.hasNext() && i < size; i++) {
            Object newObj = iterator.next();
            PWArgsBuffer newSlot = pwArgsArrayBuffer.get(i);
            doSerialize(arena, limits, newSlot, newObj, remainingElements, depthRemaining - 1);
        }
        if (i != size) {
            throw new ConcurrentModificationException("i=" + (int) i + ", size=" + (int) size);
        }
    }

    private static void serializeMap(Arena arena,
                                     Powerwaf.Limits limits,
                                     PWArgsBuffer pwArgsSlot,
                                     int[] remainingElements,
                                     int depthRemaining,
                                     Map<?, ?> map) {
        int sizeInt = Math.min(map.size(), remainingElements[0]);
        if (sizeInt < 0) {
            throw new RuntimeException("Negative map size");
        }
        char size = capToUnsignedShort(sizeInt);

        KVPairArrayBuffer kvPairArrayBuffer = pwArgsSlot.writeMap(arena, size);
        if (kvPairArrayBuffer == null) {
            throw new RuntimeException("Could not allocate array for serializing map");
        }

        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        char i;
        for (i = 0; iterator.hasNext() && i < size; i++) {
            Map.Entry<?, ?> entry = iterator.next();
            KVPairBuffer newSlot = kvPairArrayBuffer.get(i);
            // write value
            doSerialize(arena, limits, newSlot.getValuePWArgsBuffer(),
                    entry.getValue(), remainingElements, depthRemaining - 1);

            // write key
            Object key = entry.getKey();
            CharSequence svalue = key instanceof CharSequence ? (CharSequence) key : key.toString();
            if (svalue.length() > limits.maxStringSize) {
                LOGGER.debug("Truncating string from size {} to size {}",
                        svalue.length(), limits.maxStringSize);
                svalue = svalue.subSequence(0, limits.maxStringSize);
            }

            boolean success = newSlot.writeKey(arena, svalue);
            if (!success) {
                throw new RuntimeException("Could not write key string");
            }
        }
        if (i != size) {
            throw new ConcurrentModificationException("i=" + (int) i + ", size=" + (int) size);
        }
    }

    private static class Arena {
        private static final CharsetEncoder CHARSET_ENCODER =
                StandardCharsets.UTF_8.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE) // UTF-8 can represent all though
                        .replaceWith(new byte[] {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD});

        List<MainSegment> mainSegments = new ArrayList<>();
        int curMainSegment;
        int idxOfFirstUsedSegment = -1;
        List<StringsSegment> stringsSegments = new ArrayList<>();
        int curStringsSegment;

        Arena() {
            mainSegments.add(new MainSegment(MAIN_MIN_SEGMENTS_SIZE));
            stringsSegments.add(new StringsSegment(STRINGS_MIN_SEGMENTS_SIZE));
        }

        void reset() {
            for (MainSegment seg : mainSegments) {
                seg.clear();
            }
            for (StringsSegment seg : stringsSegments) {
                seg.clear();
            }
            curMainSegment = 0;
            curStringsSegment = 0;
            idxOfFirstUsedSegment = -1;
        }

        ByteBuffer getFirstUsedPWArgsBuffer() {
            if (idxOfFirstUsedSegment == -1) {
                throw new IllegalStateException("No PWArgs written");
            }
            return mainSegments.get(idxOfFirstUsedSegment).buffer;
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
         * @param smallStringBuffer the buffer where to write strings up to MAX_SMALL_STRING_SIZE
         *                          null if no such buffer exists
         * @param s the string to serialize
         * @return the native pointer to the string and its size in bytes,
         *         or null if the string is too large
         */
        WrittenString writeStringUnlimited(ByteBuffer smallStringBuffer, CharSequence s) {
            ByteBuffer bytes;

            CharBuffer cb = s instanceof CharBuffer ?
                    ((CharBuffer) s).duplicate() :
                    CharBuffer.wrap(s);

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

            if (smallStringBuffer != null && lenInBytes <= MAX_SMALL_STRING_SIZE) {
                smallStringBuffer.put(bytes).put(NUL_TERMINATOR);
                return new WrittenString(0L, lenInBytes);
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

        PWArgsArrayBuffer allocatePWArgsBuffer(char num) {
            MainSegment segment;
            segment = mainSegments.get(curMainSegment);
            PWArgsArrayBuffer array;
            while ((array = segment.allocatePWArgsArray(num)) == null) {
                segment = changeMainSegment(Math.max(MAIN_MIN_SEGMENTS_SIZE, num * SIZEOF_PWARGS));
            }
            if (idxOfFirstUsedSegment == -1) {
                idxOfFirstUsedSegment = curMainSegment;
            }
            return array;
        }

        KVPairArrayBuffer allocateKVPairArrayBuffer(char num) {
            MainSegment segment;
            segment = mainSegments.get(curMainSegment);
            KVPairArrayBuffer array;
            while ((array = segment.allocateKVPairArray(num)) == null) {
                segment = changeMainSegment(Math.max(MAIN_MIN_SEGMENTS_SIZE, num * SIZEOF_KVPAIR));
            }
            return array;
        }

        private MainSegment changeMainSegment(int capacity) {
            MainSegment e;
            if (curMainSegment == mainSegments.size() -1) {
                e = new MainSegment(capacity);
                mainSegments.add(e);
            } else {
                e = mainSegments.get(curMainSegment + 1);
            }
            curMainSegment++;
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

    public static class ArenaLease implements AutoCloseable, Closeable {
        private boolean closeCalled;
        private final Arena arena;

        ArenaLease(Arena arena) {
            this.arena = arena;
        }

        Arena getArena() {
            return this.arena;
        }

        public ByteBuffer getFirstPWArgsByteBuffer() {
            return this.arena.getFirstUsedPWArgsBuffer();
        }

        public ByteBuffer serializeMore(Powerwaf.Limits limits, Map<?, ?> map) {
            return ByteBufferSerializer.serializeMore(this, limits, map);
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


    static class MainSegment {
        ByteBuffer buffer;

        MainSegment(int capacity) {
            // assume this is 8-byte aligned
            this.buffer = ByteBuffer.allocateDirect(capacity);
        }

        @SuppressWarnings("Duplicates")
        PWArgsArrayBuffer allocatePWArgsArray(char num) {
            if (left() < num * SIZEOF_PWARGS) {
                return null;
            }
            int position = this.buffer.position();
            ByteBuffer slice = this.buffer.slice();
            slice.limit(num * SIZEOF_PWARGS);
            this.buffer.position(position + num * SIZEOF_PWARGS);
            return new PWArgsArrayBuffer(slice, num);
        }

        @SuppressWarnings("Duplicates")
        KVPairArrayBuffer allocateKVPairArray(char num) {
            if (left() < num * SIZEOF_KVPAIR) {
                return null;
            }
            int position = this.buffer.position();
            ByteBuffer slice = this.buffer.slice();
            slice.limit(num * SIZEOF_KVPAIR);
            this.buffer.position(position + num * SIZEOF_KVPAIR);
            return new KVPairArrayBuffer(slice, num);
        }

        void clear() {
            buffer.clear();
        }

        private int left() {
            return buffer.remaining();
        }
    }

    static class PWArgsArrayBuffer {
        private final ByteBuffer buffer;
        private final int num;

        static final PWArgsArrayBuffer EMPTY_BUFFER = new PWArgsArrayBuffer();

        PWArgsArrayBuffer(ByteBuffer buffer, int num) {
            if (num == 0 || buffer == null) {
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
            assert this.buffer != null;
            ByteBuffer slice = offsetBuffer(this.buffer, i * SIZEOF_PWARGS);
            slice.limit(SIZEOF_PWARGS);
            return new PWArgsBuffer(slice);
        }

        long getAddress() {
            if (buffer == null) {
                return NULLPTR;
            }
            return getByteBufferAddress(buffer);
        }
    }

    static class KVPairArrayBuffer {
        private final ByteBuffer buffer;
        private final int num;

        static final KVPairArrayBuffer EMPTY_BUFFER = new KVPairArrayBuffer();

        KVPairArrayBuffer(ByteBuffer buffer, int num) {
            if (num == 0 || buffer == null) {
                throw new IllegalArgumentException();
            }
            this.num = num;
            this.buffer = buffer;
        }

        private KVPairArrayBuffer() {
            this.num = 0;
            this.buffer = null;
        }

        KVPairBuffer get(int i) {
            if (i < 0 || i >= num) {
                throw new ArrayIndexOutOfBoundsException();
            }
            assert this.buffer != null;
            ByteBuffer slice = offsetBuffer(this.buffer, i * SIZEOF_KVPAIR);
            slice.limit(SIZEOF_KVPAIR);
            return new KVPairBuffer(slice);
        }

        long getAddress() {
            if (buffer == null) {
                return NULLPTR;
            }
            return getByteBufferAddress(buffer);
        }
    }

    /*
     * These are the structures:
     *
     *  struct _ddwaf_object
     *  {
     *      union __attribute__((packed))
     *      {
     *          uint64_t u64;
     *          int64_t i64;
     *          struct _ddwaf_object_kv *map;
     *          struct _ddwaf_object *array;
     *          char *str;
     *          char sstr[11];
     *      } via;
     *      uint8_t type;
     *      union __attribute__((packed))
     *      {
     *          struct __attribute__((packed))
     *          {
     *              uint16_t capacity;
     *              uint16_t size;
     *          };
     *          uint32_t length;
     *      };
     *  };
     *
     */
    static class PWArgsBuffer {
        private static final int OFFSET_OF_TYPE = 11;

        private final ByteBuffer buffer;

        PWArgsBuffer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        boolean writeString(Arena arena, CharSequence value) {
            Arena.WrittenString writtenString = arena.writeStringUnlimited(buffer, value);
            if (writtenString == null) { // string too large
                return false;
            }

            if (writtenString.ptr == 0) {
                this.buffer.position(OFFSET_OF_TYPE);
                this.buffer
                        .put(PWInputType.PWI_SMALL_STRING.value)
                        .putInt(writtenString.utf8len);
            } else {
                this.buffer
                        .putLong(writtenString.ptr)
                        .position(OFFSET_OF_TYPE);
                this.buffer
                        .put(PWInputType.PWI_STRING.value)
                        .putInt(writtenString.utf8len);
            }
            return true;
        }

        void writeLong(long value) {
            this.buffer
                    .putLong(value)
                    .position(OFFSET_OF_TYPE);
            this.buffer
                    .put(PWInputType.PWI_SIGNED_NUMBER.value);
        }

        PWArgsArrayBuffer writeArray(Arena arena, char numElements) {
            if (numElements == 0) {
                this.buffer.position(OFFSET_OF_TYPE);
                this.buffer
                        .put(PWInputType.PWI_ARRAY.value)
                        .putInt(0); // capacity & length
                return PWArgsArrayBuffer.EMPTY_BUFFER;
            }

            PWArgsArrayBuffer pwArgsArrayBuffer = arena.allocatePWArgsBuffer(numElements);
            if (pwArgsArrayBuffer == null) {
                // should not happen
                return null;
            }
            this.buffer
                    .putLong(pwArgsArrayBuffer.getAddress())
                    .position(OFFSET_OF_TYPE);
            this.buffer
                    .put(PWInputType.PWI_ARRAY.value)
                    .putChar(numElements)
                    .putChar(numElements);
            return pwArgsArrayBuffer;
        }

        KVPairArrayBuffer writeMap(Arena arena, char numElements) {
            if (numElements == 0) {
                this.buffer
                        .putLong(0L)
                        .position(OFFSET_OF_TYPE);
                this.buffer
                        .put(PWInputType.PWI_MAP.value)
                        .putInt(0); // capacity & length
                return KVPairArrayBuffer.EMPTY_BUFFER;
            }

            KVPairArrayBuffer kvPairArrayBuffer = arena.allocateKVPairArrayBuffer(numElements);
            if (kvPairArrayBuffer == null) {
                // should not happen
                return null;
            }
            this.buffer
                    .putLong(kvPairArrayBuffer.getAddress())
                    .position(OFFSET_OF_TYPE);
            this.buffer
                    .put(PWInputType.PWI_MAP.value)
                    .putChar(numElements)
                    .putChar(numElements);
            return kvPairArrayBuffer;
        }
    }

     /*
      *  struct _ddwaf_object_kv {
      *      struct _ddwaf_object value;
      *      char *key;
      *      uint32_t length;
      *  };
      */
    static class KVPairBuffer {
        private final static int OFFSET_OF_KEY = SIZEOF_PWARGS;

        private final ByteBuffer buffer;

        KVPairBuffer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        PWArgsBuffer getValuePWArgsBuffer() {
            ByteBuffer slice = offsetBuffer(this.buffer, 0);
            slice.limit(SIZEOF_PWARGS);
            return new PWArgsBuffer(slice);
        }

        boolean writeKey(Arena arena, CharSequence sequence) {
            Arena.WrittenString writtenString = arena.writeStringUnlimited(null, sequence);
            if (writtenString == null) {
                return false;
            }
            this.buffer.position(OFFSET_OF_KEY);
            this.buffer
                    .putLong(writtenString.ptr)
                    .putInt(writtenString.utf8len);
            return true;
        }
    }

    enum PWInputType {
        PWI_INVALID((byte) 0),
        PWI_SIGNED_NUMBER((byte) 1),
        PWI_UNSIGNED_NUMBER((byte) 2),
        PWI_STRING((byte) 4),
        PWI_ARRAY((byte) 8),
        PWI_MAP((byte) 16),
        PWI_SMALL_STRING((byte) 32);

        byte value;
        PWInputType(byte i) {
            this.value = i;
        }
    }

    static final class StringsSegment {
        ByteBuffer buffer;
        long base;

        StringsSegment(int capacity) {
            this.buffer = ByteBuffer.allocateDirect(capacity);
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
