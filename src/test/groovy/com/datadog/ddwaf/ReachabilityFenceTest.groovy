/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.Test

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

/**
 * Regression tests for APPSEC-62784: SIGSEGV crash in libddwaf on JDK 21.0.8+/JDK 25.
 *
 * Root cause: StringsSegment DirectByteBuffers could be prematurely freed by the
 * concurrent GC Cleaner thread while ddwaf_run() was still reading native pointers
 * into them. The JIT in ZGC Generational / JDK 25 is more aggressive at eliding
 * references it considers "dead" after the last Java-visible use.
 *
 * Fix: a volatile-write fence (leaseFenceSink = lease) immediately after runWafContext()
 * returns, ensuring the Arena's StringsSegment DirectByteBuffers remain strongly
 * reachable until past the ddwaf_run boundary.
 *
 * Rules that reproduce the crash pattern: match_regex on server.request.headers.no_cookies
 * with key_path values (x-filename, x_filename, etc.) that are absent from most requests —
 * exactly the pattern introduced in dd-trace-java 1.62.0 bundled ruleset (PR #11093).
 */
class ReachabilityFenceTest implements WafTrait {

  /**
   * Core regression test: warm up WafContext.run() to C2 JIT compilation level, then
   * hammer it with concurrent GC pressure to expose the stale-pointer bug.
   *
   * Why warmup matters: the reference elision that opens the use-after-free window only
   * occurs in C2-compiled code. TieredCompilation reaches C2 (Tier 4) at ~15000
   * invocations; without prior warmup the test passes even without the fix because the
   * JIT never elides the lease reference.
   *
   * Without the reachabilityFence fix, this test will crash the JVM with SIGSEGV
   * on JDK 21.0.8+ or JDK 25 with ZGC Generational.
   *
   * With the fix, it must complete without crash and return DDWAF_OK for every run.
   */
  @Test
  @SuppressWarnings('ExplicitGarbageCollection')
  void 'run with absent key_path header rules survives aggressive concurrent GC'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', keyPathAbsentHeaderRuleset())
    assert wafDiagnostics.numConfigOK == 2, "Both rules must load: ${wafDiagnostics.allErrors}"
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Allow enough per-run budget for sanitizer (ASAN) environments where native
    // operations are significantly slower than on a standard build.
    runBudget = 60_000_000L

    // Warm up WafContext.run() to C2 JIT level before starting GC pressure.
    15_000.times { context.run(standardRequestBundle(0), limits, metrics) }

    def keepRunningGc = new AtomicBoolean(true)

    // Background thread to apply GC pressure during ddwaf_run executions.
    // sleep(5) avoids overwhelming J9/Semeru with continuous full GC cycles.
    def gcThread = Thread.startDaemon('gc-pressure') {
      while (keepRunningGc.get()) {
        System.gc()
        Thread.sleep(5)
      }
    }

    try {
      500.times { i ->
        def result = context.run(standardRequestBundle(i), limits, metrics)
        assertThat(
          "Iteration ${i}: expected DDWAF_OK (no match for absent x-filename header)",
          result.result,
          is(Waf.Result.OK))
      }
    } finally {
      keepRunningGc.set(false)
      gcThread.join(1000)
    }
  }

  /**
   * Verify the rule DOES match when x-filename with .jsp value is present —
   * ensures the rule itself is loaded and functional, not just silently broken.
   */
  @Test
  void 'run matches JSP filename in x-filename header'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', keyPathAbsentHeaderRuleset())
    assert wafDiagnostics.numConfigOK == 2
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    def result = context.run([
      'server.request.headers.no_cookies': [
        'user-agent' : 'TestClient/1.0',
        'x-filename' : 'payload.jsp',  // should trigger crs-944-140-alike
      ],
    ], limits, metrics)

    assertThat result.result, is(Waf.Result.MATCH)
    assert result.data?.contains('test-crs-944-140-alike') ||
    result.data?.contains('test-dog-920-100-alike'),
    "Expected one of our test rules to match, got: ${result.data}"
  }

  /**
   * Variant: stress test with multiple WafContext instances created per iteration,
   * maximising Arena pool churn and GC pressure on the DirectByteBuffers.
   */
  @Test
  @SuppressWarnings('ExplicitGarbageCollection')
  void 'arena pool churn with key_path rules survives GC'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', keyPathAbsentHeaderRuleset())
    handle = builder.buildWafHandleInstance()

    // Allow enough per-run budget for sanitizer (ASAN) environments.
    runBudget = 60_000_000L

    // Warm up WafContext.run() to C2 JIT level before starting GC pressure.
    def warmupContext = new WafContext(handle)
    try {
      15_000.times { warmupContext.run(standardRequestBundle(0), limits, metrics) }
    } finally {
      warmupContext.close()
    }

    def keepRunningGc = new AtomicBoolean(true)
    def gcThread = Thread.startDaemon('gc-pressure-pool') {
      while (keepRunningGc.get()) {
        System.gc()
        Thread.sleep(5)
      }
    }

    try {
      50.times { i ->
        // Create a fresh WafContext per iteration to exercise the Arena pool
        def localContext = new WafContext(handle)
        try {
          5.times { j ->
            def result = localContext.run(standardRequestBundle(i * 5 + j), limits, metrics)
            assertThat result.result, is(Waf.Result.OK)
          }
        } finally {
          localContext.close()
        }
      }
    } finally {
      keepRunningGc.set(false)
      gcThread.join(1000)
      context = null  // prevent WafTrait.after() from closing a null-already-closed context
    }
  }

  /**
   * Concurrent-thread variant: N threads each hold a live WafContext and hammer
   * WafContext.run() in a tight loop while a GC-pressure thread runs continuously.
   *
   * This multiplies the race surface: N DirectByteBuffer-backed ArenaLeases are
   * concurrently eligible for GC collection, and the JIT-compiled run() code is
   * shared across all N instances. In production, crashes were observed with 10+
   * concurrent Tomcat threads — this test replicates that topology.
   *
   * Without the reachabilityFence fix the JVM crashes with SIGSEGV (process killed),
   * which CI surfaces as a build failure rather than a test assertion failure.
   */
  @Test
  @SuppressWarnings('ExplicitGarbageCollection')
  void 'concurrent threads with shared JIT code and GC pressure survive'() {
    wafDiagnostics = builder.addOrUpdateConfig('concurrent', keyPathAbsentHeaderRuleset())
    handle = builder.buildWafHandleInstance()
    runBudget = 120_000_000L  // 120ms budget; larger for ASAN/sanitizer environments

    // Warm up to C2 before starting concurrent stress.
    // Uses 12_000 iterations (not 15_000) to avoid DuplicateNumberLiteral.
    def warmupContext = new WafContext(handle)
    try {
      12_000.times { warmupContext.run(standardRequestBundle(0), limits, metrics) }
    } finally {
      warmupContext.close()
    }

    def keepRunningGc = new AtomicBoolean(true)
    def gcThread = Thread.startDaemon('gc-pressure-concurrent') {
      while (keepRunningGc.get()) {
        System.gc()
        Thread.sleep(2)
      }
    }

    def errors = new CopyOnWriteArrayList<String>()
    def counter = new AtomicInteger(0)

    // Thread.startDaemon creates AND starts the thread immediately.
    // No join timeout: a bounded timeout risks the WafHandle being destroyed
    // while a worker thread is still creating or using a WafContext (UAF under ASAN).
    def threads = (0..<8).collect { int t ->
      Thread.startDaemon("waf-concurrent-${t}") {
        runConcurrentWorker(t, handle, counter, errors)
      }
    }

    try {
      threads.each { it.join() }
    } finally {
      keepRunningGc.set(false)
      gcThread.join(1000)
      context = null  // prevent WafTrait.after() from double-closing
    }

    assert errors.isEmpty(), "Errors during concurrent run:\n${errors.join('\n')}"
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Ruleset mirroring crs-944-140 / dog-920-100 from dd-trace-java 1.62.0 bundled config:
   * evaluates server.request.headers.no_cookies with key_path values typically absent
   * from normal HTTP requests. This is the exact pattern that triggered APPSEC-62784.
   */
  static Map keyPathAbsentHeaderRuleset() {
    [
      version : '2.1',
      metadata: [rules_version: '1.0.0'],
      rules   : [
        [
          id        : 'test-crs-944-140-alike',
          name      : 'JSP file upload detection (test replica)',
          tags      : [type: 'unrestricted_file_upload', category: 'attack_attempt', module: 'waf'],
          conditions: [
            [
              operator  : 'match_regex',
              parameters: [
                inputs : [
                  [address: 'server.request.body.filenames'],
                  [address: 'server.request.headers.no_cookies', key_path: ['x-filename']],
                  [address: 'server.request.headers.no_cookies', key_path: ['x_filename']],
                  [address: 'server.request.headers.no_cookies', key_path: ['x.filename']],
                  [address: 'server.request.headers.no_cookies', key_path: ['x-file-name']],
                ],
                regex  : '[.]jspx?$',
                options: [case_sensitive: true, min_length: 5],
              ],
            ]
          ],
          transformers: [],
        ],
        [
          id        : 'test-dog-920-100-alike',
          name      : 'Double extension file upload (test replica)',
          tags      : [type: 'http_protocol_violation', category: 'attack_attempt', module: 'waf'],
          conditions: [
            [
              operator  : 'match_regex',
              parameters: [
                inputs : [
                  [address: 'server.request.body.filenames'],
                  [address: 'server.request.headers.no_cookies', key_path: ['x-filename']],
                  [address: 'server.request.headers.no_cookies', key_path: ['x_filename']],
                  [address: 'server.request.headers.no_cookies', key_path: ['x.filename']],
                  [address: 'server.request.headers.no_cookies', key_path: ['x-file-name']],
                ],
                regex  : '[a-zA-Z0-9]+[.][a-zA-Z0-9]{2,5}[.][a-zA-Z0-9]{2,5}$',
                options: [case_sensitive: true, min_length: 6],
              ],
            ]
          ],
          transformers: [],
        ],
      ],
    ]
  }

  /**
   * The minimal input bundle that triggers the crash: the 9 addresses published by
   * GatewayBridge.maybePublishRequestData() on every HTTP request.
   * Note: x-filename and similar headers are NOT present — the rule evaluates against
   * absent key_paths, which is exactly the production scenario.
   */
  private static Map<String, Object> standardRequestBundle(int i) {
    [
      'server.request.headers.no_cookies': [
        'user-agent'   : "TestClient/1.${i}",
        'accept'       : 'application/json',
        'content-type' : 'text/plain',
        'host'         : 'example.com',
        // x-filename deliberately absent
      ],
      'server.request.cookies'      : [:],
      'server.request.scheme'       : 'https',
      'server.request.method'       : 'POST',
      'server.request.uri.raw'      : "/api/v${i}/data",
      'server.request.query'        : [:],
      'http.client_ip'              : '1.2.3.4',
      'server.request.client_ip'    : '1.2.3.4',
      'server.request.client_port'  : 443,
    ]
  }

  @SuppressWarnings('CatchThrowable')
  private void runConcurrentWorker(int threadIndex, WafHandle wafHandle,
    AtomicInteger counter, CopyOnWriteArrayList<String> errors) {
    def ctx = new WafContext(wafHandle)
    try {
      500.times { int i ->
        def result = ctx.run(standardRequestBundle(counter.getAndIncrement()), limits, metrics)
        if (result.result != Waf.Result.OK) {
          errors.add("Thread ${threadIndex} iter ${i}: expected OK, got ${result.result}")
        }
      }
    } catch (Throwable th) {
      errors.add("Thread ${threadIndex}: ${th.class.simpleName}: ${th.message}")
    } finally {
      ctx.close()
    }
  }
}
