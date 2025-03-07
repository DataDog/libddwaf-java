/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail

class WafHandleTest implements WafTrait {

    @Test
    void 'waf handle is online after creation'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
        handle = builder.buildWafHandleInstance()

        assert handle.online
    }

    @Test
    void 'waf handle is offline after closing'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
        handle = builder.buildWafHandleInstance()
        handle.close()

        assert !handle.online
    }

    @Test
    void 'waf handle can be closed multiple times safely'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
        handle = builder.buildWafHandleInstance()

        // First close
        handle.close()
        assert !handle.online

        // Second close should not throw exception
        handle.close()
        assert !handle.online
    }

    @Test
    void 'waf handle has known addresses'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
        handle = builder.buildWafHandleInstance()

        String[] addresses = handle.knownAddresses
        assert addresses != null
        assert addresses.length > 0
    }

    @Test
    void 'waf handle has known actions'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_BLOCK)
        handle = builder.buildWafHandleInstance()

        String[] actions = handle.knownActions
        assert actions != null
        assert actions.length > 0
    }

    @Test
    void 'knownAddresses throws exception after handle is closed'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
        handle = builder.buildWafHandleInstance()
        handle.close()

        def exception = shouldFail(IllegalStateException) {
            handle.knownAddresses
        }
        assert exception.message == 'This WafHandle is no longer online'
    }

    @Test
    void 'getKnownActions throws exception after handle is closed'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_BLOCK)
        handle = builder.buildWafHandleInstance()
        handle.close()

        def exception = shouldFail(IllegalStateException) {
            handle.knownActions
        }
        assert exception.message == 'This WafHandle is no longer online'
    }

    @Test
    void 'different waf handle instances for same ruleset have same addresses'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)

        // Create two separate instances
        def handle1 = builder.buildWafHandleInstance()
        def handle2 = builder.buildWafHandleInstance()

        try {
            String[] addresses1 = handle1.knownAddresses
            String[] addresses2 = handle2.knownAddresses

            assert addresses1 != null
            assert addresses2 != null
            assert addresses1.length > 0
            assert addresses1.length == addresses2.length
        } finally {
            handle1.close()
            handle2.close()
        }
    }

    @Test
    void 'waf handle thread safety for read operations'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
        handle = builder.buildWafHandleInstance()

        // Simulate multiple threads accessing read methods
        def threads = []
        def exceptions = []

        10.times { i ->
            threads << Thread.start {
                try {
                    handle.knownAddresses
                } catch (IllegalStateException e) {
                    synchronized(exceptions) {
                        exceptions << e
                    }
                }
            }
        }

        // Wait for all threads to complete
        threads.each { it.join() }

        // No exceptions should have been thrown
        assert exceptions.size() == 0
    }

    @Test
    void 'waf handle thread safety for close operation'() {
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
        handle = builder.buildWafHandleInstance()

        // Simulate multiple threads trying to close the handle
        def threads = []

        5.times { i ->
            threads << Thread.start {
                handle.close()
            }
        }

        // Wait for all threads to complete
        threads.each { it.join() }

        // Handle should be closed
        assert !handle.online
    }

    @Test
    void 'updating ruleset configuration works correctly'() {
        // First add a basic ruleset
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)

        // Then update with a different ruleset
        wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_BLOCK)

        handle = builder.buildWafHandleInstance()

        assert handle != null
        assert handle.online

        // Should have actions from the BLOCK ruleset
        String[] actions = handle.knownActions
        assert actions != null
        assert actions.length > 0
    }
}

