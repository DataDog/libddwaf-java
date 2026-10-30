/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2026 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.Test

import java.nio.ByteBuffer

import static java.nio.ByteOrder.LITTLE_ENDIAN
import static org.junit.Assume.assumeTrue

/**
 * Checks that every Linux libsqreen_jni.so on the classpath is linked with BIND_NOW (APPSEC-69052).
 * Unlike LazyBindingGotClobberTest, it needs no compiler, so it also guards the release binaries.
 */
class NativeLibBindNowTest {
  private static final List<String> LINUX_JNI_LIBS = [
    'native_libs/linux/x86_64/glibc/libsqreen_jni.so',
    'native_libs/linux/x86_64/musl/libsqreen_jni.so',
    'native_libs/linux/aarch64/glibc/libsqreen_jni.so',
    'native_libs/linux/aarch64/musl/libsqreen_jni.so',
  ]

  // Elf64_Ehdr, Elf64_Phdr and Elf64_Dyn field offsets
  private static final int E_PHOFF = 0x20
  private static final int E_PHENTSIZE = 0x36
  private static final int E_PHNUM = 0x38
  private static final int P_OFFSET = 0x08
  private static final int P_FILESZ = 0x20
  private static final int DYN_SIZE = 16
  private static final int D_VAL = 8

  private static final int PT_DYNAMIC = 2
  private static final long DT_NULL = 0
  private static final long DT_BIND_NOW = 24
  private static final long DT_FLAGS = 30
  private static final long DT_FLAGS_1 = 0x6ffffffbL
  private static final long DF_BIND_NOW = 0x8
  private static final long DF_1_NOW = 0x1

  @Test
  void linuxJniLibrariesAreLinkedWithBindNow() {
    Map<String, byte[]> libs = [:]
    for (String path : LINUX_JNI_LIBS) {
      InputStream is = getClass().classLoader.getResourceAsStream(path)
      if (is != null) {
        libs[path] = is.withCloseable { it.bytes }
      }
    }
    // not libs.empty: on a Map that looks up the 'empty' key
    assumeTrue('no Linux libsqreen_jni.so on the classpath', libs as boolean)

    List<String> lazy = libs.findAll { !hasBindNow(it.value) }*.key
    assert lazy.empty: "linked without -z now: $lazy"
  }

  private static boolean hasBindNow(byte[] elf) {
    ByteBuffer buf = ByteBuffer.wrap(elf).order(LITTLE_ENDIAN)
    assert buf.getInt(0) == 0x464c457f && elf[4] == 2: 'not a 64-bit ELF file'

    long phoff = buf.getLong(E_PHOFF)
    int phentsize = buf.getShort(E_PHENTSIZE) & 0xffff
    int phnum = buf.getShort(E_PHNUM) & 0xffff
    (0..<phnum).collect { int i -> (int) (phoff + i * phentsize) }
    .findAll { int ph -> buf.getInt(ph) == PT_DYNAMIC }
    .any { int ph -> dynamicSectionHasBindNow(buf, buf.getLong(ph + P_OFFSET), buf.getLong(ph + P_FILESZ)) }
  }

  private static boolean dynamicSectionHasBindNow(ByteBuffer buf, long offset, long size) {
    for (long dyn = offset; dyn < offset + size; dyn += DYN_SIZE) {
      long tag = buf.getLong((int) dyn)
      if (tag == DT_NULL) {
        break
      }
      if (isBindNowEntry(tag, buf.getLong((int) dyn + D_VAL))) {
        return true
      }
    }
    false
  }

  private static boolean isBindNowEntry(long tag, long value) {
    switch (tag) {
      case DT_BIND_NOW:
        return true
      case DT_FLAGS:
        return (value & DF_BIND_NOW) != 0
      case DT_FLAGS_1:
        return (value & DF_1_NOW) != 0
      default:
        return false
    }
  }
}
