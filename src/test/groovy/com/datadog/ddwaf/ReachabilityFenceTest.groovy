/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.json.JsonSlurper
import org.junit.Test

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
 * Fix: Reference.reachabilityFence(this.lease) and Reference.reachabilityFence(ephemeralLease)
 * immediately after runWafContext() returns, ensuring the Arena's StringsSegment
 * DirectByteBuffers remain strongly reachable until past the ddwaf_run boundary.
 *
 * Rules that reproduce the crash pattern: match_regex on server.request.headers.no_cookies
 * with key_path values (x-filename, x_filename, etc.) that are absent from most requests —
 * exactly the pattern introduced in dd-trace-java 1.62.0 bundled ruleset (PR #11093).
 */
class ReachabilityFenceTest implements WafTrait {

    /**
     * Rule that mirrors the bundled crs-944-140 / dog-920-100 pattern in dd-trace-java 1.62.0:
     * evaluates server.request.headers.no_cookies with key_path values that are typically
     * absent from normal HTTP requests.
     */
    // Rule that mirrors crs-944-140 / dog-920-100 from dd-trace-java 1.62.0 bundled ruleset.
    // Evaluates server.request.headers.no_cookies with key_path values typically absent
    // from normal HTTP requests. This is the exact pattern that triggered APPSEC-62784.
    static Map buildRuleKeyPathHeadersAbsent() {
        [
            version : '2.1',
            metadata: [rules_version: '1.0.0'],
            rules   : [
                [
                    id        : 'test-crs-944-140-alike',
                    name      : 'JSP file upload detection (test replica)',
                    tags      : [type: 'unrestricted_file_upload', category: 'attack_attempt', module: 'waf'],
                    conditions: [[
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
                    ]],
                    transformers: [],
                ],
                [
                    id        : 'test-dog-920-100-alike',
                    name      : 'Double extension file upload (test replica)',
                    tags      : [type: 'http_protocol_violation', category: 'attack_attempt', module: 'waf'],
                    conditions: [[
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
                    ]],
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
                'user-agent'    : "TestClient/1.${i}",
                'accept'        : 'application/json',
                'content-type'  : 'text/plain',
                'host'          : 'example.com',
                // x-filename deliberately absent
            ],
            'server.request.cookies'         : [:],
            'server.request.scheme'          : 'https',
            'server.request.method'          : 'POST',
            'server.request.uri.raw'         : "/api/v${i}/data",
            'server.request.query'           : [:],
            'http.client_ip'                 : '1.2.3.4',
            'server.request.client_ip'       : '1.2.3.4',
            'server.request.client_port'     : 443,
        ]
    }

    /**
     * Core regression test: run WAF evaluation 2000 times with a concurrent GC thread
     * hammering System.gc() to maximise the chance of exposing the stale-pointer bug.
     *
     * Without the reachabilityFence fix, this test will crash the JVM with SIGSEGV
     * on JDK 21.0.8+ or JDK 25 with ZGC Generational.
     *
     * With the fix, it must complete without crash and return DDWAF_OK for every run.
     */
    @Test
    @SuppressWarnings('ExplicitGarbageCollection')
    void 'run with absent key_path header rules survives aggressive concurrent GC'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', buildRuleKeyPathHeadersAbsent())
        assert wafDiagnostics.numConfigOK == 2, "Both rules must load: ${wafDiagnostics.allErrors}"
        handle = builder.buildWafHandleInstance()
        context = new WafContext(handle)

        def keepRunningGc = new AtomicBoolean(true)

        // Background thread to apply maximum GC pressure during ddwaf_run executions
        def gcThread = Thread.startDaemon('gc-pressure') {
            while (keepRunningGc.get()) {
                System.gc()
                Thread.sleep(0)  // yield to allow other threads to run
            }
        }

        try {
            2000.times { i ->
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
        wafDiagnostics = builder.addOrUpdateConfig('test', buildRuleKeyPathHeadersAbsent())
        assert wafDiagnostics.numConfigOK == 2
        handle = builder.buildWafHandleInstance()
        context = new WafContext(handle)

        def result = context.run([
            'server.request.headers.no_cookies': [
                'user-agent' : 'TestClient/1.0',
                'x-filename' : 'payload.jsp',  // ← should trigger crs-944-140-alike
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
        wafDiagnostics = builder.addOrUpdateConfig('test', buildRuleKeyPathHeadersAbsent())
        handle = builder.buildWafHandleInstance()

        def keepRunningGc = new AtomicBoolean(true)
        def gcThread = Thread.startDaemon('gc-pressure-pool') {
            while (keepRunningGc.get()) {
                System.gc()
                Thread.sleep(0)
            }
        }

        try {
            200.times { i ->
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
}
