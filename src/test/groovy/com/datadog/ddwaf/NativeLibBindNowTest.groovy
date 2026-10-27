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
import static org.junit.Assume.assumeFalse

/**
 * Checks that every Linux libsqreen_jni.so on the classpath is linked with BIND_NOW (APPSEC-69052).
 * Unlike LazyBindingGotClobberTest, it needs no compiler, so it also guards the release binaries.
 */
class NativeLibBindNowTest {
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
    for (String arch : ['x86_64', 'aarch64']) {
      for (String libc : ['glibc', 'musl']) {
        String path = "native_libs/linux/$arch/$libc/libsqreen_jni.so"
        InputStream is = getClass().classLoader.getResourceAsStream(path)
        if (is != null) {
          libs[path] = is.withCloseable { it.bytes }
        }
      }
    }
    assumeFalse('no Linux libsqreen_jni.so on the classpath', libs.isEmpty())

    List<String> lazy = libs.findAll { !hasBindNow(it.value) }*.key
    assert lazy.isEmpty(): "linked without -z now: $lazy"
  }

  private static boolean hasBindNow(byte[] elf) {
    ByteBuffer buf = ByteBuffer.wrap(elf).order(LITTLE_ENDIAN)
    assert buf.getInt(0) == 0x464c457f && elf[4] == 2: 'not a 64-bit ELF file'

    long phoff = buf.getLong(E_PHOFF)
    int phentsize = buf.getShort(E_PHENTSIZE) & 0xffff
    int phnum = buf.getShort(E_PHNUM) & 0xffff
    for (int i = 0; i < phnum; i++) {
      int ph = (int) (phoff + i * phentsize)
      if (buf.getInt(ph) != PT_DYNAMIC) {
        continue
      }
      long dynOffset = buf.getLong(ph + P_OFFSET)
      long dynSize = buf.getLong(ph + P_FILESZ)
      for (long dyn = dynOffset; dyn < dynOffset + dynSize; dyn += DYN_SIZE) {
        long tag = buf.getLong((int) dyn)
        long value = buf.getLong((int) dyn + D_VAL)
        if (tag == DT_NULL) {
          break
        }
        if (tag == DT_BIND_NOW ||
          (tag == DT_FLAGS && (value & DF_BIND_NOW) != 0) ||
          (tag == DT_FLAGS_1 && (value & DF_1_NOW) != 0)) {
          return true
        }
      }
    }
    false
  }
}
