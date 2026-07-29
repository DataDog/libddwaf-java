/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf;

import java.io.Closeable;
import java.lang.reflect.Array;
import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializes Java objects into the raw binary representation of {@code ddwaf_object} (libddwaf 2.x)
 * inside direct {@link ByteBuffer}s, so that libddwaf can consume them without any copy.
 *
 * <p>The binary layout replicated here is the one declared in {@code ddwaf.h}:
 *
 * <pre>
 *   union _ddwaf_object {                     // 16 bytes, 8-byte aligned
 *       uint8_t type;                         // +0
 *       union {
 *           struct { uint8_t type; bool val; }                          b8;   // val   +1
 *           struct { uint8_t type; int64_t val; }                       i64;  // val   +8
 *           struct { uint8_t type; uint64_t val; }                      u64;  // val   +8
 *           struct { uint8_t type; double val; }                        f64;  // val   +8
 *           struct { uint8_t type; uint32_t size; char *ptr; }          str;  // size  +4, ptr +8
 *           struct { uint8_t type; uint8_t size; char data[14]; }       sstr; // size  +1, data +2
 *           struct { uint8_t type; uint16_t size, capacity;
 *                    ddwaf_object *ptr; }                               array;// size +2, cap +4,
 *                                                                             // ptr  +8
 *           struct { uint8_t type; uint16_t size, capacity;
 *                    ddwaf_object_kv *ptr; }                            map;  // idem
 *       } via;
 *   };
 *
 *   struct _ddwaf_object_kv { ddwaf_object key; ddwaf_object val; };  // 32 bytes, key +0, val +16
 * </pre>
 *
 * <p>Array children are plain 16-byte {@code ddwaf_object}s; map entries are 32-byte {@code
 * ddwaf_object_kv}s. Both are allocated out of the same arena, which hands out 16-byte slots.
 *
 * <p>Since these constants are hand-rolled, {@link #checkNativeLayout()} cross-checks every one of
 * them against the real C struct through {@link #getNativeObjectLayout()}; it is called from {@link
 * Waf#initialize(boolean)} right after the native library is loaded, so a libddwaf layout change
 * can never silently corrupt memory.
 */
public class ByteBufferSerializer {
  private static final long NULLPTR = 0;

  /** {@code sizeof(ddwaf_object)}. Also the arena allocation unit. */
  private static final int SIZEOF_PWARGS = 16;

  /** {@code sizeof(ddwaf_object_kv)}: a key object followed by a value object. */
  private static final int SIZEOF_PWARGS_KV = 32;

  /** {@code offsetof(ddwaf_object_kv, val)}. */
  private static final int OFF_KV_VALUE = 16;

  private static final int OFF_TYPE = 0;
  private static final int OFF_BOOL_VAL = 1;
  private static final int OFF_NUM_VAL = 8;
  private static final int OFF_STR_SIZE = 4;
  private static final int OFF_STR_PTR = 8;
  private static final int OFF_SSTR_SIZE = 1;
  private static final int OFF_SSTR_DATA = 2;
  private static final int OFF_CONTAINER_SIZE = 2;
  private static final int OFF_CONTAINER_CAPACITY = 4;
  private static final int OFF_CONTAINER_PTR = 8;

  /** {@code DDWAF_OBJ_SSTR_SIZE}: strings up to this length are stored inline in the object. */
  private static final int MAX_SMALL_STRING_SIZE = 14;

  /** Container {@code size}/{@code capacity} are {@code uint16_t} in libddwaf 2.x. */
  private static final int MAX_CONTAINER_SIZE = 65535;

  private static final int PWARGS_MIN_SEGMENTS_SIZE = 512;
  private static final int STRINGS_MIN_SEGMENTS_SIZE = 81920;

  private static final Logger LOGGER = LoggerFactory.getLogger(ByteBufferSerializer.class);

  private final Waf.Limits limits;

  public ByteBufferSerializer(Waf.Limits limits) {
    this.limits = limits;
  }

  public ArenaLease serialize(Map<?, ?> map, WafMetrics metrics) {
    if (map == null) {
      throw new NullPointerException("map can't be null");
    }

    ArenaLease lease = ArenaPool.INSTANCE.getLease();
    try {
      serializeMore(lease, this.limits, map, metrics);
    } catch (RuntimeException | Error rte) {
      lease.close();
      throw rte;
    }

    return lease;
  }

  public static ArenaLease getBlankLease() {
    return ArenaPool.INSTANCE.getLease();
  }

  /**
   * Returns the layout of {@code ddwaf_object}/{@code ddwaf_object_kv} as seen by the C compiler.
   * See {@code Java_com_datadog_ddwaf_ByteBufferSerializer_getNativeObjectLayout} in {@code
   * byte_buffer.c} for the meaning of each position.
   */
  private static native int[] getNativeObjectLayout();

  /**
   * Verifies the hardcoded layout constants of this class against the actual C structs.
   *
   * @throws IllegalStateException if libddwaf's object layout is not the one assumed here
   */
  static void checkNativeLayout() {
    int[] expected =
        new int[] {
          SIZEOF_PWARGS,
          SIZEOF_PWARGS_KV,
          0, // offsetof(ddwaf_object_kv, key)
          OFF_KV_VALUE,
          OFF_TYPE,
          OFF_BOOL_VAL,
          OFF_NUM_VAL,
          OFF_STR_SIZE,
          OFF_STR_PTR,
          OFF_SSTR_SIZE,
          OFF_SSTR_DATA,
          MAX_SMALL_STRING_SIZE,
          OFF_CONTAINER_SIZE,
          OFF_CONTAINER_CAPACITY,
          OFF_CONTAINER_PTR,
          PWInputType.PWI_INVALID.value,
          PWInputType.PWI_NULL.value,
          PWInputType.PWI_BOOL.value,
          PWInputType.PWI_SIGNED.value,
          PWInputType.PWI_UNSIGNED.value,
          PWInputType.PWI_FLOAT.value,
          PWInputType.PWI_STRING.value,
          PWInputType.PWI_LITERAL_STRING.value,
          PWInputType.PWI_SMALL_STRING.value,
          PWInputType.PWI_ARRAY.value,
          PWInputType.PWI_MAP.value,
        };
    int[] actual = getNativeObjectLayout();
    if (!Arrays.equals(expected, actual)) {
      throw new IllegalStateException(
          "libddwaf ddwaf_object layout mismatch: ByteBufferSerializer assumes "
              + Arrays.toString(expected)
              + " but the native library reports "
              + Arrays.toString(actual));
    }
  }

  private static ByteBuffer serializeMore(
      ArenaLease lease, Waf.Limits limits, Map<?, ?> map, WafMetrics metrics) {
    Arena arena = lease.getArena();
    // limits apply per-serialization run
    int[] remainingElements = new int[] {limits.maxElements};

    // The address of this ByteBuffer will be accessed from native code via GetDirectBufferAddress
    PWArgsArrayBuffer pwArgsArrayBuffer = arena.allocateGetAddressCompatiblePWArgsBuffer(1);
    if (pwArgsArrayBuffer == null) {
      throw new OutOfMemoryError();
    }
    PWArgsBuffer initialValue = pwArgsArrayBuffer.get(0);
    doSerialize(
        arena, limits, initialValue, null, map, remainingElements, limits.maxDepth, metrics);
    return initialValue.buffer;

    // if it threw somewhere, the arena will have elements that are never used
    // they will only be released when the lease is closed
  }

  private static void doSerialize(
      Arena arena,
      Waf.Limits limits,
      PWArgsBuffer pwargsSlot,
      String parameterName,
      Object value,
      int[] remainingElements,
      int depthRemaining,
      WafMetrics metrics) {
    if (parameterName != null && parameterName.length() > limits.maxStringSize) {
      LOGGER.debug(
          "Truncating parameter string from size {} to size {}",
          parameterName.length(),
          limits.maxStringSize);
      parameterName = parameterName.substring(0, limits.maxStringSize);
      if (metrics != null) {
        metrics.incrementTruncatedStringTooLongCount();
        // TODO - ADD METRIC FOR UNTRUNCATED SIZE
      }
    }

    remainingElements[0]--;

    // RuntimeExceptions thrown should only happen if we get strings with
    // size Integer.MAX_VALUE and the limit size for strings is also
    // Integer.MAXVALUE

    if (remainingElements[0] < 0 || depthRemaining < 0) {
      if (remainingElements[0] < 0) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Ignoring element, for maxElements was exceeded");
        }
        if (metrics != null) {
          metrics.incrementTruncatedListMapTooLargeCount();
        }
      } else if (depthRemaining <= 0) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Ignoring element, for maxDepth was exceeded");
        }
        if (metrics != null) {
          metrics.incrementTruncatedObjectTooDeepCount();
        }
      }
      // write empty map
      if (pwargsSlot.writeMap(arena, parameterName, 0) == null) {
        throw new RuntimeException("Could not write map");
      }
      return;
    }

    if (value == null) {
      if (!pwargsSlot.writeNull(arena, parameterName)) {
        throw new RuntimeException("Error writing null value");
      }
    } else if (value instanceof CharSequence) {
      CharSequence svalue = (CharSequence) value;
      if (svalue.length() > limits.maxStringSize) {
        LOGGER.debug(
            "Truncating string from size {} to size {}", svalue.length(), limits.maxStringSize);
        svalue = svalue.subSequence(0, limits.maxStringSize);
        if (metrics != null) {
          metrics.incrementTruncatedStringTooLongCount();
          // TODO - ADD METRIC FOR UNTRUNCATED SIZE
        }
      }
      if (!pwargsSlot.writeString(arena, parameterName, svalue)) {
        throw new RuntimeException("Could not write string");
      }
    } else if (value instanceof Number) {
      boolean res;
      if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
        res = pwargsSlot.writeDouble(arena, parameterName, ((Number) value).doubleValue());
      } else {
        res = pwargsSlot.writeLong(arena, parameterName, ((Number) value).longValue());
      }
      if (!res) {
        throw new RuntimeException("Could not write number");
      }
    } else if (value instanceof Collection) {
      int size = clampContainerSize(((Collection<?>) value).size(), remainingElements, metrics);

      // TODO - ADD METRIC FOR UNTRUNCATED SIZE
      Iterator<?> iterator = ((Collection<?>) value).iterator();
      serializeIterable(
          arena,
          limits,
          pwargsSlot,
          parameterName,
          remainingElements,
          depthRemaining,
          metrics,
          iterator,
          size);
    } else if (value.getClass().isArray()) {
      int size = clampContainerSize(Array.getLength(value), remainingElements, metrics);

      // TODO - ADD METRIC FOR UNTRUNCATED SIZE
      Iterator<?> iterator = new GenericArrayIterator(value);
      serializeIterable(
          arena,
          limits,
          pwargsSlot,
          parameterName,
          remainingElements,
          depthRemaining,
          metrics,
          iterator,
          size);
    } else if (value instanceof Iterable) {
      // we need to iterate twice
      Iterator<?> iterator = ((Iterable<?>) value).iterator();
      int size = 0;
      while (iterator.hasNext() && size < remainingElements[0]) {
        iterator.next();
        size++;
      }
      size = clampContainerSize(size, remainingElements, metrics);

      // TODO - ADD METRIC FOR UNTRUNCATED SIZE
      iterator = ((Iterable<?>) value).iterator();
      serializeIterable(
          arena,
          limits,
          pwargsSlot,
          parameterName,
          remainingElements,
          depthRemaining,
          metrics,
          iterator,
          size);
    } else if (value instanceof Map) {
      int size = clampContainerSize(((Map<?, ?>) value).size(), remainingElements, metrics);

      // TODO - ADD METRIC FOR UNTRUNCATED SIZE
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
        doSerialize(
            arena,
            limits,
            newSlot,
            key.toString(),
            entry.getValue(),
            remainingElements,
            depthRemaining - 1,
            metrics);
      }
      if (i != size) {
        throw new ConcurrentModificationException("i=" + i + ", size=" + size);
      }
    } else if (value instanceof Boolean) {
      if (!pwargsSlot.writeBool(arena, parameterName, (Boolean) value)) {
        throw new RuntimeException("Could not write boolean");
      }
    } else {
      // unknown value; write null
      LOGGER.info("Do not know how to serialize value of type {}", value.getClass());
      if (!pwargsSlot.writeNull(arena, parameterName)) {
        throw new RuntimeException("Error writing null for unknown type");
      }
    }
  }

  /**
   * Caps the number of children of a container to what the remaining element budget allows and to
   * what libddwaf's {@code uint16_t} size field can represent.
   */
  private static int clampContainerSize(int size, int[] remainingElements, WafMetrics metrics) {
    int capped = Math.min(size, remainingElements[0]);
    if (capped > MAX_CONTAINER_SIZE) {
      LOGGER.debug(
          "Truncating container from size {} to size {} (libddwaf limit)",
          capped,
          MAX_CONTAINER_SIZE);
      capped = MAX_CONTAINER_SIZE;
      if (metrics != null) {
        metrics.incrementTruncatedListMapTooLargeCount();
      }
    }
    return capped;
  }

  private static void serializeIterable(
      Arena arena,
      Waf.Limits limits,
      PWArgsBuffer pwArgsSlot,
      String parameterName,
      int[] remainingElements,
      int depthRemaining,
      WafMetrics metrics,
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
      doSerialize(
          arena, limits, newSlot, null, newObj, remainingElements, depthRemaining - 1, metrics);
    }
    if (i != size) {
      throw new ConcurrentModificationException("i=" + i + ", size=" + size);
    }
  }

  private static class Arena {
    private static final int MAX_BYTES_PER_CHAR_UTF8 =
        (int) StandardCharsets.UTF_8.newEncoder().maxBytesPerChar();

    private final CharsetEncoder utf8Encoder =
        StandardCharsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE) // UTF-8 can represent all though
            .replaceWith(new byte[] {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD});

    List<PWArgsSegment> pwargsSegments = new ArrayList<>();
    int curPWArgsSegment;
    int idxOfFirstUsedPWArgsSegment = -1;
    List<StringsSegment> stringsSegments = new ArrayList<>();
    int curStringsSegment;
    CharBuffer currentWrapper = null;

    public final CharsetEncoder getCharsetEncoder() {
      CharsetEncoder charsetEncoder = utf8Encoder;
      charsetEncoder.reset();
      return charsetEncoder;
    }

    Arena() {
      pwargsSegments.add(new PWArgsSegment(PWARGS_MIN_SEGMENTS_SIZE));
      stringsSegments.add(new StringsSegment(STRINGS_MIN_SEGMENTS_SIZE));
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
      idxOfFirstUsedPWArgsSegment = -1;
    }

    ByteBuffer getFirstUsedPWArgsBuffer() {
      if (idxOfFirstUsedPWArgsSegment == -1) {
        throw new IllegalStateException("No PWArgs written");
      }
      return pwargsSegments.get(idxOfFirstUsedPWArgsSegment).buffer;
    }

    /**
     * Writes a string object (either a small string, stored inline, or a regular string pointing
     * into the strings arena) into {@code dest} at offset {@code off}.
     *
     * @return false if the string is too large to be serialized
     */
    boolean writeStringObject(ByteBuffer dest, int off, CharSequence s) {
      CharBuffer cb;
      if (s instanceof CharBuffer) {
        cb = ((CharBuffer) s).duplicate();
      } else {
        cb = currentWrapper = CharSequenceWrapper.wrap(s, currentWrapper);
      }

      long tmp = (long) s.length() * MAX_BYTES_PER_CHAR_UTF8 + 1; // 0 terminated
      if (tmp > Integer.MAX_VALUE) {
        // overflow ahead
        return false;
      }
      int maxBytes = (int) tmp;

      StringsSegment segment = stringsSegments.get(curStringsSegment);
      int utf8len;
      while ((utf8len = segment.write(getCharsetEncoder(), cb, maxBytes)) < 0) {
        segment = changeStringsSegment(Math.max(STRINGS_MIN_SEGMENTS_SIZE, maxBytes));
      }

      // write() leaves the buffer positioned right after the encoded bytes plus the
      // NUL terminator it appends, so the start of the string it just wrote is always
      // (current position - encoded length - 1 NUL byte) bytes back.
      int start = segment.buffer.position() - utf8len - 1;
      PWArgsBuffer.clearObject(dest, off);
      if (utf8len <= MAX_SMALL_STRING_SIZE) {
        // small strings live inside the object itself; give the arena space back
        dest.put(off + OFF_TYPE, (byte) PWInputType.PWI_SMALL_STRING.value);
        dest.put(off + OFF_SSTR_SIZE, (byte) utf8len);
        for (int i = 0; i < utf8len; i++) {
          dest.put(off + OFF_SSTR_DATA + i, segment.buffer.get(start + i));
        }
        segment.rollbackTo(start);
      } else {
        dest.put(off + OFF_TYPE, (byte) PWInputType.PWI_STRING.value);
        dest.putInt(off + OFF_STR_SIZE, utf8len);
        dest.putLong(off + OFF_STR_PTR, segment.base + start);
      }
      return true;
    }

    PWArgsArrayBuffer allocateGetAddressCompatiblePWArgsBuffer(int num) {
      return allocatePWArgsBuffer(num, SIZEOF_PWARGS, true);
    }

    private PWArgsArrayBuffer allocatePWArgsBuffer(
        int num, int stride, boolean getAddressCompatible) {
      int slots = num * (stride / SIZEOF_PWARGS);
      PWArgsSegment segment;
      segment = pwargsSegments.get(curPWArgsSegment);
      PWArgsArrayBuffer array;
      while ((array = segment.allocate(slots, num, stride, getAddressCompatible)) == null) {
        segment = changePWArgsSegment(Math.max(PWARGS_MIN_SEGMENTS_SIZE, slots));
      }
      if (idxOfFirstUsedPWArgsSegment == -1) {
        idxOfFirstUsedPWArgsSegment = curPWArgsSegment;
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

    public ByteBuffer serializeMore(Waf.Limits limits, Map<?, ?> map, WafMetrics metrics) {
      return ByteBufferSerializer.serializeMore(this, limits, map, metrics);
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

  /**
   * A chunk of direct memory handing out 16-byte slots ({@code sizeof(ddwaf_object)}). Map entries
   * take two consecutive slots each ({@code sizeof(ddwaf_object_kv)}).
   */
  static class PWArgsSegment {
    ByteBuffer buffer;
    List<PWArgsArrayBuffer> pwargsArrays = new ArrayList<>();
    int idxOfNextUnusedPWArgsArrayBuffer = 0;

    PWArgsSegment(int capacity) {
      // assume this is 8-byte aligned
      this.buffer = ByteBuffer.allocateDirect(SIZEOF_PWARGS * capacity);
      this.buffer.order(ByteOrder.nativeOrder());
    }

    PWArgsArrayBuffer allocate(int slots, int num, int stride, boolean getAddressCompatible) {
      if (left() < slots) {
        return null;
      }
      int position = this.buffer.position();
      PWArgsArrayBuffer arrayBuffer;
      if (getAddressCompatible) {
        ByteBuffer slice = this.buffer.slice().order(ByteOrder.nativeOrder());
        arrayBuffer = new PWArgsArrayBuffer(slice, 0, num, stride);
      } else if (idxOfNextUnusedPWArgsArrayBuffer >= pwargsArrays.size()) {
        arrayBuffer = new PWArgsArrayBuffer(this.buffer, position, num, stride);
        pwargsArrays.add(arrayBuffer);
        idxOfNextUnusedPWArgsArrayBuffer++;
      } else {
        arrayBuffer = pwargsArrays.get(idxOfNextUnusedPWArgsArrayBuffer);
        arrayBuffer.reset(position, num, stride);
        idxOfNextUnusedPWArgsArrayBuffer++;
      }
      this.buffer.position(position + slots * SIZEOF_PWARGS);
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

  /**
   * The children of a container: {@code num} entries of {@code stride} bytes each, either 16 bytes
   * ({@code ddwaf_object}, for arrays) or 32 bytes ({@code ddwaf_object_kv}, for maps).
   */
  static class PWArgsArrayBuffer {
    private final ByteBuffer buffer;
    private int start;
    private int num;
    private int stride;
    private final List<PWArgsBuffer> pwArgsBuffers;

    static final PWArgsArrayBuffer EMPTY_BUFFER = new PWArgsArrayBuffer();

    PWArgsArrayBuffer(ByteBuffer buffer, int start, int num, int stride) {
      if (num == 0 || buffer == null) {
        throw new IllegalArgumentException();
      }
      this.buffer = buffer;
      this.start = start;
      this.num = num;
      this.stride = stride;
      this.pwArgsBuffers = new ArrayList<>(num);
    }

    private PWArgsArrayBuffer() {
      this.buffer = null;
      this.start = 0;
      this.num = 0;
      this.stride = SIZEOF_PWARGS;
      this.pwArgsBuffers = null;
    }

    void reset(int start, int num, int stride) {
      this.start = start;
      this.num = num;
      this.stride = stride;
    }

    PWArgsBuffer get(int i) {
      if (i < 0 || i >= num) {
        throw new ArrayIndexOutOfBoundsException();
      }
      assert this.buffer != null;
      while (i >= pwArgsBuffers.size()) {
        pwArgsBuffers.add(new PWArgsBuffer(this.buffer));
      }
      PWArgsBuffer pwArgsBuffer = pwArgsBuffers.get(i);
      pwArgsBuffer.reset(start + i * stride, stride == SIZEOF_PWARGS_KV);
      return pwArgsBuffer;
    }

    long getAddress() {
      if (buffer == null) {
        return NULLPTR;
      }
      long address = getByteBufferAddress(buffer);
      return address + start;
    }
  }

  /**
   * A single slot to write a {@code ddwaf_object} into. When the slot belongs to a map it actually
   * spans a whole {@code ddwaf_object_kv}: the key object is written at {@code start} and the value
   * object at {@code start + 16}.
   */
  static class PWArgsBuffer {
    private final ByteBuffer buffer;
    private int keyOffset;
    private int valueOffset;

    /** Creates an unpositioned slot; {@link #reset} must be called before writing to it. */
    PWArgsBuffer(ByteBuffer buffer) {
      this.buffer = buffer;
      this.keyOffset = -1;
      this.valueOffset = -1;
    }

    void reset(int start, boolean isMapEntry) {
      this.keyOffset = isMapEntry ? start : -1;
      this.valueOffset = isMapEntry ? start + OFF_KV_VALUE : start;
    }

    static void clearObject(ByteBuffer buffer, int off) {
      buffer.putLong(off, 0L);
      buffer.putLong(off + 8, 0L);
    }

    boolean writeNull(Arena arena, String parameterName) {
      if (!putParameterName(arena, parameterName)) { // string too large
        return false;
      }
      clearObject(this.buffer, valueOffset);
      this.buffer.put(valueOffset + OFF_TYPE, (byte) PWInputType.PWI_NULL.value);
      return true;
    }

    boolean writeBool(Arena arena, String parameterName, boolean value) {
      if (!putParameterName(arena, parameterName)) { // string too large
        return false;
      }
      clearObject(this.buffer, valueOffset);
      this.buffer.put(valueOffset + OFF_TYPE, (byte) PWInputType.PWI_BOOL.value);
      this.buffer.put(valueOffset + OFF_BOOL_VAL, (byte) (value ? 1 : 0));
      return true;
    }

    boolean writeString(Arena arena, String parameterName, CharSequence value) {
      if (!putParameterName(arena, parameterName)) { // string too large
        return false;
      }
      return arena.writeStringObject(this.buffer, valueOffset, value);
    }

    boolean writeLong(Arena arena, String parameterName, long value) {
      if (!putParameterName(arena, parameterName)) { // string too large
        return false;
      }
      clearObject(this.buffer, valueOffset);
      this.buffer.put(valueOffset + OFF_TYPE, (byte) PWInputType.PWI_SIGNED.value);
      this.buffer.putLong(valueOffset + OFF_NUM_VAL, value);
      return true;
    }

    boolean writeDouble(Arena arena, String parameterName, double value) {
      if (!putParameterName(arena, parameterName)) { // string too large
        return false;
      }
      clearObject(this.buffer, valueOffset);
      this.buffer.put(valueOffset + OFF_TYPE, (byte) PWInputType.PWI_FLOAT.value);
      this.buffer.putDouble(valueOffset + OFF_NUM_VAL, value);
      return true;
    }

    PWArgsArrayBuffer writeArray(Arena arena, String parameterName, int numElements) {
      return writeArrayOrMap(arena, parameterName, numElements, PWInputType.PWI_ARRAY);
    }

    PWArgsArrayBuffer writeMap(Arena arena, String parameterName, int numElements) {
      return writeArrayOrMap(arena, parameterName, numElements, PWInputType.PWI_MAP);
    }

    private PWArgsArrayBuffer writeArrayOrMap(
        Arena arena, String parameterName, int numElements, PWInputType type) {
      if (numElements < 0 || numElements > MAX_CONTAINER_SIZE) {
        throw new IllegalArgumentException("Invalid container size: " + numElements);
      }
      if (!putParameterName(arena, parameterName)) { // string too large
        return null;
      }
      clearObject(this.buffer, valueOffset);
      this.buffer.put(valueOffset + OFF_TYPE, (byte) type.value);
      if (numElements == 0) {
        // size = capacity = 0, ptr = NULL
        return PWArgsArrayBuffer.EMPTY_BUFFER;
      }

      int stride = type == PWInputType.PWI_MAP ? SIZEOF_PWARGS_KV : SIZEOF_PWARGS;
      PWArgsArrayBuffer pwArgsArrayBuffer = arena.allocatePWArgsBuffer(numElements, stride, false);
      if (pwArgsArrayBuffer == null) {
        // should not happen
        return null;
      }
      long address = pwArgsArrayBuffer.getAddress();
      if (address == NULLPTR) {
        // should not happen
        return null;
      }
      this.buffer.putShort(valueOffset + OFF_CONTAINER_SIZE, (short) numElements);
      this.buffer.putShort(valueOffset + OFF_CONTAINER_CAPACITY, (short) numElements);
      this.buffer.putLong(valueOffset + OFF_CONTAINER_PTR, address);
      return pwArgsArrayBuffer;
    }

    private boolean putParameterName(Arena arena, String parameterName) {
      if (keyOffset < 0) {
        // not a map entry: there is no key slot to write to
        return true;
      }
      // callers are expected never to pass a null key for a map entry, but keep the invariant
      // local: an absent key is written as the empty string
      return arena.writeStringObject(
          this.buffer, keyOffset, parameterName == null ? "" : parameterName);
    }
  }

  /** Mirrors {@code DDWAF_OBJ_TYPE}. These are bitmask values, not sequential ordinals. */
  enum PWInputType {
    PWI_INVALID(0x00),
    PWI_NULL(0x01),
    PWI_BOOL(0x02),
    PWI_SIGNED(0x04),
    PWI_UNSIGNED(0x06),
    PWI_FLOAT(0x08),
    PWI_STRING(0x10),
    PWI_LITERAL_STRING(0x12),
    PWI_SMALL_STRING(0x14),
    PWI_ARRAY(0x20),
    PWI_MAP(0x40);

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
     * Encodes {@code in} as UTF-8 at the current position, followed by a NUL byte.
     *
     * @return the encoded length in bytes (excluding the NUL), or -1 if it does not fit
     */
    int write(CharsetEncoder encoder, CharBuffer in, int maxBytes) {
      if (left() < maxBytes) {
        return -1;
      }
      int position = this.buffer.position();
      if (maxBytes > 1 && in.hasRemaining()) {
        try {
          CoderResult cr = encoder.encode(in, this.buffer, true);
          if (cr.isUnderflow()) cr = encoder.flush(this.buffer);
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

      return bytesLen;
    }

    /** Gives back the space taken by the last string written (it was inlined in the object). */
    void rollbackTo(int position) {
      this.buffer.position(position);
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
