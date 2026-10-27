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
import java.util.concurrent.TimeUnit

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
  private static final long CHILD_TIMEOUT_MINUTES = 2

  @Test
  void libraryLoadsWhenGotSlotIsWrittenBeforeRelocation() {
    assumeTrue('LD_AUDIT is Linux only', System.getProperty('os.name') == 'Linux')
    assumeFalse('musl does not support LD_AUDIT', runningOnMusl())
    assumeFalse('not combined with the ASan LD_PRELOAD', System.getenv('LD_PRELOAD')?.contains('asan') ?: false)

    String auditSource = System.getProperty('gotClobberAuditSource')
    assumeTrue('gotClobberAuditSource is set by the Gradle test task', auditSource != null)

    File tmpDir = Files.createTempDirectory('got-clobber').toFile()
    File auditLib = new File(tmpDir, 'got_clobber_audit.so')
    boolean compiled = compileAuditLib(auditSource, auditLib)
    if (!compiled) {
      tmpDir.deleteDir()
    }
    assumeTrue('C compiler not available', compiled)

    File outputFile = new File(tmpDir, 'child-output.txt')
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
    pb.redirectOutput(outputFile)

    // the output goes to a file so that nothing blocks on a pipe and the timeout always applies
    Process process = pb.start()
    boolean exited
    try {
      exited = process.waitFor(CHILD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
    } finally {
      process.destroyForcibly()
    }
    String output = outputFile.text

    assert exited: "child JVM did not exit within $CHILD_TIMEOUT_MINUTES minutes:\n$output"
    assert output.contains('GOT_CLOBBER: patched malloc'):
    "audit module did not patch the GOT, the scenario was not exercised:\n$output"
    assert process.exitValue() == 0:
    'libsqreen_jni.so crashed after its malloc GOT slot was written before relocation ' +
    "(is it linked without -z now?), exit code ${process.exitValue()}:\n$output"

    // only reached on success: on failure, keep the hs_err file referenced in the output
    tmpDir.deleteDir()
  }

  private static boolean compileAuditLib(String source, File target) {
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

  private static boolean runningOnMusl() {
    new File('/proc/self/maps').readLines('ISO-8859-1').any {
      it.contains('libc.musl-') || it.contains('ld-musl-')
    }
  }

  /** Entry point of the child JVM: loads the native library the same way the tracer does. */
  static class Child {
    static void main(String[] args) {
      // exits 0 only if the library loaded; a crash in JNI_OnLoad kills the JVM
      Waf.initialize(false)
    }
  }
}
