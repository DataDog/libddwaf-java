/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2026 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.Test

import java.nio.file.Files

import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue

/**
 * Regression test for APPSEC-69052: SIGSEGV in JNI_OnLoad when a GOT slot of libsqreen_jni.so is
 * written by another in-process component before the dynamic linker relocates the library.
 *
 * <p>The LD_AUDIT module in src/test/c/got_clobber_audit.c performs that write deterministically
 * for the malloc slot. With lazy binding, glibc then adds the load base to the written pointer and
 * the first malloc call in JNI_OnLoad jumps to a wild address. With BIND_NOW, glibc overwrites the
 * slot with the resolved symbol and the library loads normally.
 *
 * <p>Needs Linux/glibc (musl has no LD_AUDIT) and a C compiler; skipped otherwise.
 * NativeLibBindNowTest covers the release binaries where this test cannot run.
 */
class LazyBindingGotClobberTest {

  @Test(timeout = 120_000L)
  void libraryLoadsWhenGotSlotIsWrittenBeforeRelocation() {
    assumeTrue('LD_AUDIT is Linux only', System.getProperty('os.name') == 'Linux')
    assumeFalse('musl does not support LD_AUDIT', isMusl())
    assumeFalse('not combined with the ASan LD_PRELOAD', System.getenv('LD_PRELOAD')?.contains('asan') ?: false)

    File tmpDir = Files.createTempDirectory('got-clobber').toFile()
    File auditLib = new File(tmpDir, 'got_clobber_audit.so')
    assumeTrue('C compiler not available', compileAuditLib(auditLib))

    ProcessBuilder pb = new ProcessBuilder(
      new File(System.getProperty('java.home'), 'bin/java').path,
      '-XX:-CreateCoredumpOnCrash',
      "-XX:ErrorFile=${tmpDir}/hs_err_%p.log".toString(),
      '-cp', System.getProperty('java.class.path'),
      Child.name)
    pb.environment().putAll(
      LD_AUDIT: auditLib.path,
      GOT_CLOBBER_TARGET: 'libsqreen_jni.so',
      GOT_CLOBBER_SYMBOL: 'malloc')
    pb.redirectErrorStream(true)

    Process process = pb.start()
    String output = process.inputStream.text
    int exitCode = process.waitFor()

    assert output.contains('GOT_CLOBBER: patched malloc'):
    "audit module did not patch the GOT, the scenario was not exercised:\n$output"
    assert exitCode == 0 && output.contains(Child.LOADED):
    "libsqreen_jni.so crashed after its malloc GOT slot was written before relocation " +
    "(is it linked without -z now?), exit code $exitCode:\n$output"
  }

  private static boolean compileAuditLib(File target) {
    String source = System.getProperty('gotClobberAuditSource')
    Process process
    try {
      process = new ProcessBuilder('cc', '-shared', '-fPIC', '-o', target.path, source)
        .redirectErrorStream(true)
        .start()
    } catch (IOException ignored) {
      return false
    }
    String output = process.inputStream.text
    assert process.waitFor() == 0: "failed to compile $source:\n$output"
    true
  }

  private static boolean isMusl() {
    new File('/proc/self/maps').readLines('ISO-8859-1').any {
      it.contains('libc.musl-') || it.contains('ld-musl-')
    }
  }

  /** Entry point of the child JVM: loads the native library the same way the tracer does. */
  static class Child {
    static final String LOADED = 'LIBSQREEN_JNI_LOADED'

    static void main(String[] args) {
      Waf.initialize(false)
      println LOADED
    }
  }
}
