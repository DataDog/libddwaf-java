/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import java.io.Closeable;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    static void release() {
        ArenaPool.INSTANCE.arenas.clear();
    }

    public ArenaLease serialize(Object map) {
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

    public static void debugDump(PrintWriter pw) {
        ArenaPool.INSTANCE.debugDump(pw);
    }

    private static ByteBuffer serializeMore(ArenaLease lease, Powerwaf.Limits limits, Object map) {
        Arena arena = lease.getArena();
        // limits apply per-serialization run
        int[] remainingElements = new int[]{limits.maxElements};

        // The address of this ByteBuffer will be accessed from native code via GetDirectBufferAddress
        PWArgsBuffer initialValue = arena.getRoot();
        doSerialize(arena, limits, initialValue, null, map, remainingElements, limits.maxDepth);
        return initialValue.buffer;

        // if it threw somewhere, the arena will have elements that are never used
        // they will only be released when the lease is closed
    }

    private static void doSerialize(Arena arena, Powerwaf.Limits limits, PWArgsBuffer pwargsSlot,
                                    CharSequence parameterName, Object value, int[] remainingElements,
                                    int depthRemaining) {
        if (parameterName != null && parameterName.length() > limits.maxStringSize) {
            LOGGER.debug("Truncating parameter string from size {} to size {}",
                    parameterName.length(), limits.maxStringSize);
            parameterName = parameterName.subSequence(0, limits.maxStringSize);
        }

        remainingElements[0]--;

        // RuntimeExceptions thrown should only happen if we get strings with
        // size Integer.MAX_VALUE and the limit size for strings is also
        // Integer.MAXVALUE

        if (remainingElements[0] < 0 || depthRemaining < 0) {
            if (LOGGER.isDebugEnabled()) {
                if (remainingElements[0] < 0) {
                    LOGGER.debug("Ignoring element, for maxElements was exceeded");
                } else if (depthRemaining <= 0) {
                    LOGGER.debug("Ignoring element, for maxDepth was exceeded");
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
        } else if (value instanceof MapIterableWithSize) {
            MapIterableWithSize<?> mpis = (MapIterableWithSize<?>) value;
            int size = Math.min(mpis.size(), remainingElements[0]);
            PWArgsArrayBuffer pwArgsArrayBuffer = pwargsSlot.writeMap(arena, parameterName, size);

            Iterator<? extends Map.Entry<?, ?>> iterator = mpis.iterator();
            PWArgsBuffer pwArgsBuffer = new PWArgsBuffer();
            int i;
            for (i = 0; iterator.hasNext() && i < size; i++) {
                Map.Entry<?, ?> entry = iterator.next();
                PWArgsBuffer newSlot = pwArgsArrayBuffer.get(pwArgsBuffer, i);
                Object key = entry.getKey();
                if (key == null) {
                    key = "";
                } else if (!(key instanceof CharSequence)) {
                    key = key.toString();
                }
                doSerialize(arena, limits, newSlot, (CharSequence) key, entry.getValue(),
                        remainingElements, depthRemaining - 1);
            }
            if (i != size) {
                throw new ConcurrentModificationException("i=" + i + ", size=" + size);
            }
        } else if (value instanceof Collection) {
            int size = Math.min(((Collection<?>) value).size(), remainingElements[0]);

            Iterator<?> iterator = ((Collection<?>) value).iterator();
            serializeIterable(
                    arena, limits, pwargsSlot, parameterName,
                    remainingElements, depthRemaining, iterator, size);
        } else if (value.getClass().isArray()) {
            int size = Math.min(Array.getLength(value), remainingElements[0]);
            Iterator<?> iterator = new GenericArrayIterator(value);
            serializeIterable(
                    arena, limits, pwargsSlot, parameterName, remainingElements,
                    depthRemaining, iterator, size);
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
                    arena, limits, pwargsSlot, parameterName,
                    remainingElements, depthRemaining, iterator, size);
        } else if (value instanceof Map) {
            int size = Math.min(((Map<?, ?>) value).size(), remainingElements[0]);

            PWArgsArrayBuffer pwArgsArrayBuffer = pwargsSlot.writeMap(arena, parameterName, size);
            if (pwArgsArrayBuffer == null) {
                throw new RuntimeException("Could not write map");
            }
            int i = 0;
            Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) value).entrySet().iterator();
            PWArgsBuffer pwArgsBuffer = new PWArgsBuffer();
            for (; iterator.hasNext() && i < size; i++) {
                Map.Entry<?, ?> entry = iterator.next();
                PWArgsBuffer newSlot = pwArgsArrayBuffer.get(pwArgsBuffer, i);
                Object key = entry.getKey();
                if (key == null) {
                    key = "";
                } else if (!(key instanceof CharSequence)) {
                    key = key.toString();
                }
                doSerialize(arena, limits, newSlot, (CharSequence) key, entry.getValue(),
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

    private static void serializeIterable(Arena arena,
                                          Powerwaf.Limits limits,
                                          PWArgsBuffer pwArgsSlot,
                                          CharSequence parameterName,
                                          int[] remainingElements,
                                          int depthRemaining,
                                          Iterator<?> iterator,
                                          int size) {
        PWArgsArrayBuffer pwArgsArrayBuffer = pwArgsSlot.writeArray(arena, parameterName, size);
        if (pwArgsArrayBuffer == null) {
            throw new RuntimeException("Error serializing iterable");
        }

        int i;
        PWArgsBuffer pwArgsBuffer = new PWArgsBuffer();
        for (i = 0; iterator.hasNext() && i < size; i++) {
            Object newObj = iterator.next();
            PWArgsBuffer newSlot = pwArgsArrayBuffer.get(pwArgsBuffer, i);
            doSerialize(arena, limits, newSlot, null, newObj, remainingElements, depthRemaining - 1);
        }
        if (i != size) {
            throw new ConcurrentModificationException("i=" + i + ", size=" + size);
        }
    }

    private static class Arena {
        private static final int MAX_BYTES_PER_CHAR_UTF8 =
                (int) StandardCharsets.UTF_8.newEncoder().maxBytesPerChar();

        private final CharsetEncoder utf8Encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE) // UTF-8 can represent all though
                .replaceWith(new byte[]{(byte) 0xEF, (byte) 0xBF, (byte) 0xBD});

        List<PWArgsSegment> pwargsSegments = new ArrayList<>();
        PWArgsBuffer root;
        int curPWArgsSegment;
        boolean gotRoot;
        List<StringsSegment> stringsSegments = new ArrayList<>();
        int curStringsSegment;
        CharBuffer currentWrapper = null;
        WrittenString cachedWS = null;

        public final CharsetEncoder getCharsetEncoder() {
            CharsetEncoder charsetEncoder = utf8Encoder;
            charsetEncoder.reset();
            return charsetEncoder;
        }

        Arena() {
            pwargsSegments.add(new PWArgsSegment(PWARGS_MIN_SEGMENTS_SIZE));
            stringsSegments.add(new StringsSegment(STRINGS_MIN_SEGMENTS_SIZE));
            root = new PWArgsBuffer();
        }

        PWArgsBuffer getRoot() {
            pwargsSegments.get(0).allocateRoot(this.root);
            gotRoot = true;
            return this.root;
        }

        void reset() {
            int size = pwargsSegments.size();
            for (int pos = 0; pos < size; pos++) {
                pwargsSegments.get(pos).clear();
            }
            size = stringsSegments.size();
            for (int pos = 0; pos < size; pos++) {
                stringsSegments.get(pos).clear();
            }
            curPWArgsSegment = 0;
            curStringsSegment = 0;
            gotRoot = false;
        }

        ByteBuffer getFirstUsedPWArgsBuffer() {
            if (!gotRoot) {
                throw new IllegalStateException("No PWArgs written");
            }
            return root.buffer;
        }

        static class WrittenString {
            private final Arena arena;
            private long ptr;
            private int utf8len;

            WrittenString(Arena arena) {
                this.arena = arena;
            }

            public void release() {
                if (arena != null) {
                    arena.cachedWS = this;
                }
            }

            public WrittenString update(long ptr, int utf8len) {
                this.ptr = ptr;
                this.utf8len = utf8len;
                return this;
            }
        }

        private static final WeakConcurrentMap<ByteBuffer, Arena.WrittenString> CACHED_BB_ADDRESSES =
                new WeakConcurrentMap.WithInlinedExpunction<ByteBuffer, Arena.WrittenString>() {
                    @Override
                    protected Arena.WrittenString defaultValue(ByteBuffer bb) {
                        if (!bb.isDirect()) {
                            throw new IllegalArgumentException();
                        }
                        int limit = bb.limit();
                        if (limit == 0 || bb.get(limit - 1) != 0x00) {
                            throw new IllegalArgumentException();
                        }
                        Arena.WrittenString writtenString = new Arena.WrittenString(null);
                        writtenString.ptr = getByteBufferAddress(bb);
                        writtenString.utf8len = limit - 1;
                        return writtenString;
                    }
                };

        /**
         * @param s the string to serialize
         * @return the native pointer to the string and its size in bytes,
         * or null if the string is too large
         */
        WrittenString writeStringUnlimited(CharSequence s) {
            if (s instanceof NativeStringAddressable) {
                ByteBuffer bb = ((NativeStringAddressable) s).getNativeStringBuffer();
                return CACHED_BB_ADDRESSES.get(bb);
            }

            CharBuffer cb;
            if (s instanceof CharBuffer) {
                cb = ((CharBuffer) s).duplicate();
            } else {
                cb = currentWrapper = CharSequenceWrapper.wrap(s, currentWrapper);
            }

            long tmp = (long) s.length() * MAX_BYTES_PER_CHAR_UTF8 + 1; // 0 terminated
            if (tmp > Integer.MAX_VALUE) {
                // overflow ahead
                return null;
            }
            int maxBytes = (int) tmp;

            StringsSegment segment;
            segment = stringsSegments.get(curStringsSegment);
            WrittenString str = cachedWS;
            if (str == null) {
                cachedWS = str = new WrittenString(this);
            }
            while ((str = segment.writeNulTerminated(str, getCharsetEncoder(), cb, maxBytes)) == null) {
                segment = changeStringsSegment(
                        Math.max(STRINGS_MIN_SEGMENTS_SIZE, maxBytes));
                str = cachedWS;
            }
            cachedWS = null;
            return str;
        }

        private PWArgsArrayBuffer allocatePWArgsBuffer(int num) {
            PWArgsSegment segment;
            segment = pwargsSegments.get(curPWArgsSegment);
            PWArgsArrayBuffer array;
            while ((array = segment.allocate(num)) == null) {
                segment = changePWArgsSegment(Math.max(PWARGS_MIN_SEGMENTS_SIZE, num));
            }
            return array;
        }

        private PWArgsSegment changePWArgsSegment(int capacity) {
            PWArgsSegment e;
            if (curPWArgsSegment == pwargsSegments.size() - 1) {
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

        private Map<String, Integer> debugStats() {
            Map<String, Integer> result = new HashMap<>();
            result.put("num_pwargs_seg", pwargsSegments.size());
            result.put("num_str_seg", stringsSegments.size());

            int totPWArgsNatMem = pwargsSegments.stream()
                    .map(s -> s.buffer.capacity()).reduce(0, Integer::sum);
            result.put("total_pwargs_mem", totPWArgsNatMem);
            int totStrNatMem = stringsSegments.stream()
                    .map(s -> s.buffer.capacity()).reduce(0, Integer::sum);
            result.put("total_str_mem", totStrNatMem);
            int totPWArgsBufPooled = pwargsSegments.stream()
                            .map(s -> s.pwargsArrays.size()).reduce(0, Integer::sum);
            result.put("total_pwargs_buf_pooled", totPWArgsBufPooled);
            return result;
        }
    }

    /* we want to reuse our ByteBuffers because they live off heap */
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

        void debugDump(PrintWriter w) {
            w.format("Number of parked arenas: %d\n", arenas.size());
            long totalNatMemory = 0;
            long totalPooled = 0;
            int i = 0;
            for (Arena arena : arenas) {
                Map<String, Integer> d = arena.debugStats();
                totalNatMemory += d.get("total_pwargs_mem") + d.get("total_str_mem");
                totalPooled += d.get("total_pwargs_buf_pooled");
                w.format("Arena %d: %s\n", ++i, d);
            }
            w.format("Total native memory: %d\n", totalNatMemory);
            w.format("Total pooled PWArgsArrayBuffer objects: %d\n", totalPooled);
            w.flush();
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

        public ByteBuffer serializeMore(Powerwaf.Limits limits, Object mapOrMpwz) {
            return ByteBufferSerializer.serializeMore(this, limits, mapOrMpwz);
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
        List<PWArgsArrayBuffer> pwargsArrays = new ArrayList<>();
        int idxOfNextUnusedPWArgsArrayBuffer = 0;

        PWArgsSegment(int capacity) {
            // assume this is 8-byte aligned
            this.buffer = ByteBuffer.allocateDirect(SIZEOF_PWARGS * capacity);
            this.buffer.order(ByteOrder.nativeOrder());
        }

        // Replace the first element at the start of the segment. This should
        // ONLY be done for the first segment.
        // libddwaf expects the data from previous runs to be available. The
        // exception is the very first element, the root, which we can replace
        // freely.
        void allocateRoot(PWArgsBuffer pwArgs) {
            pwArgs.reset(this.buffer, 0);
            int curPosition = this.buffer.position();
            // we do not need to advance the position if we have already arrays
            // in the segment, because we are inserting/replacing the 1st
            // element
            if (curPosition == 0) {
                this.buffer.position(curPosition + SIZEOF_PWARGS);
            }
        }

        PWArgsArrayBuffer allocate(int num) {
            if (left() < num) {
                return null;
            }
            int position = this.buffer.position();
            PWArgsArrayBuffer arrayBuffer;
            if (idxOfNextUnusedPWArgsArrayBuffer >= pwargsArrays.size()) {
                // do not slice() so we can reuse the object later without
                // replacing the buffer
                ByteBuffer duplicate = this.buffer.duplicate().order(ByteOrder.nativeOrder());
                arrayBuffer = new PWArgsArrayBuffer(duplicate, position, num);
                pwargsArrays.add(arrayBuffer);
            } else {
                arrayBuffer = pwargsArrays.get(idxOfNextUnusedPWArgsArrayBuffer);
                arrayBuffer.reset(position, num);
            }
            idxOfNextUnusedPWArgsArrayBuffer++;
            this.buffer.position(position + num * SIZEOF_PWARGS);
            return arrayBuffer;
        }

        void clear() {
            buffer.clear();
            idxOfNextUnusedPWArgsArrayBuffer = 0;
        }

        private int left() {
            return (buffer.capacity() - buffer.position()) / SIZEOF_PWARGS;
        }
    }

    static class PWArgsArrayBuffer {
        private final ByteBuffer buffer;
        private long base;
        private int start;
        private int num;

        static final PWArgsArrayBuffer EMPTY_BUFFER = new PWArgsArrayBuffer();

        PWArgsArrayBuffer(ByteBuffer buffer, int start, int num) {
            if (num == 0 || buffer == null) {
                throw new IllegalArgumentException();
            }
            this.buffer = buffer;
            this.base = getByteBufferAddress(buffer);
            this.start = start;
            this.num = num;
            buffer.limit(start + num * SIZEOF_PWARGS);
            buffer.position(start);
        }

        private PWArgsArrayBuffer() {
            this.buffer = null;
            this.start = 0;
            this.num = 0;
        }

        void reset(int start, int num) {
            this.start = start;
            this.num = num;
            this.buffer.limit(start + num * SIZEOF_PWARGS);
            this.buffer.position(start);
        }

        PWArgsBuffer get(PWArgsBuffer pwArgsBuffer, int i) {
            if (i < 0 || i >= num) {
                throw new ArrayIndexOutOfBoundsException();
            }
            pwArgsBuffer.reset(this.buffer, start + i * SIZEOF_PWARGS);
            return pwArgsBuffer;
        }

        long getAddress() {
            if (buffer == null) {
                return NULLPTR;
            }
            return this.base + start;
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
        private ByteBuffer origBuffer;
        private ByteBuffer buffer;

        PWArgsBuffer() {}

        void reset(ByteBuffer buffer, int offset) {
            if (this.buffer == null || buffer != this.origBuffer) {
                this.origBuffer = buffer;
                this.buffer = buffer.duplicate().order(ByteOrder.nativeOrder());
            }
            this.buffer.position(offset);
            this.buffer.limit(offset + SIZEOF_PWARGS);
        }

        boolean writeString(Arena arena, CharSequence parameterName, CharSequence value) {
            if (!putParameterName(arena, parameterName)) { // string too large
                return false;
            }
            Arena.WrittenString writtenString = arena.writeStringUnlimited(value);
            if (writtenString == null) { // string too large
                return false;
            }
            this.buffer.putLong(writtenString.ptr).putLong(writtenString.utf8len)
                    .putInt(PWInputType.PWI_STRING.value);
            writtenString.release();
            return true;
        }

        boolean writeLong(Arena arena, CharSequence parameterName, long value) {
            if (!putParameterName(arena, parameterName)) { // string too large
                return false;
            }
            this.buffer.putLong(value).putLong(0)
                    .putInt(PWInputType.PWI_SIGNED_NUMBER.value);
            return true;
        }

        PWArgsArrayBuffer writeArray(Arena arena, CharSequence parameterName, int numElements) {
            return writeArrayOrMap(arena, parameterName, numElements, PWInputType.PWI_ARRAY);
        }

        PWArgsArrayBuffer writeMap(Arena arena, CharSequence parameterName, int numElements) {
            return writeArrayOrMap(arena, parameterName, numElements, PWInputType.PWI_MAP);
        }

        private PWArgsArrayBuffer writeArrayOrMap(Arena arena, CharSequence parameterName, int numElements,
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


        private boolean putParameterName(Arena arena, CharSequence parameterName) {
            if (parameterName == null) {
                this.buffer.putLong(0L).putLong(0L);
            } else {
                Arena.WrittenString writtenString = arena.writeStringUnlimited(parameterName);
                if (writtenString == null) { // string too large
                    return false;
                }
                this.buffer.putLong(writtenString.ptr).putLong(writtenString.utf8len);
                writtenString.release();
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

        Arena.WrittenString writeNulTerminated(Arena.WrittenString writtenString,
                                               CharsetEncoder encoder,
                                               CharBuffer in,
                                               int maxBytes) {
            if (left() < maxBytes) {
                return null;
            }
            int position = this.buffer.position();
            long ptr = base + position;
            if (maxBytes > 1 && in.hasRemaining()) {
                try {
                    CoderResult cr = encoder.encode(in, this.buffer, true);
                    if (cr.isUnderflow())
                        cr = encoder.flush(this.buffer);
                    if (!cr.isUnderflow()) {
                        cr.throwException();
                    }
                } catch (CharacterCodingException e) {
                    // should not happen
                    throw new UndeclaredThrowableException(e);
                }
            }
            int bytesLen = this.buffer.position() - position;
            this.buffer.put(NUL_TERMINATOR);

            return writtenString.update(ptr, bytesLen);
        }

        private int left() {
            return buffer.capacity() - buffer.position();
        }
    }

    private static native long getByteBufferAddress(ByteBuffer bb);

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
