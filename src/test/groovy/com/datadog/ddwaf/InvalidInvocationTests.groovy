/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import groovy.transform.CompileStatic
import com.datadog.ddwaf.exception.InvalidObjectWafException
import com.datadog.ddwaf.exception.InvalidRuleSetException
import com.datadog.ddwaf.exception.UnclassifiedWafException
import org.junit.Test

import java.nio.ByteBuffer

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasEntry

class InvalidInvocationTests implements ReactiveTrait {
    @CompileStatic
    static class BadMap<K, V> implements Map<K, V> {
        @Delegate
        Map<K, V> delegate

        @Override
        Set<Entry<K, V>> entrySet() {
            throw new IllegalStateException('error here')
        }
    }

    @Test
    void 'force exception during conversion of rule definitions'() {
        def exc = shouldFail(RuntimeException) {
            ctx = Waf.createHandle('test', new BadMap(delegate: [version: '1.0', events: []]))
        }
        assert exc.message =~ 'Exception encoding init/update rule specification'
        assert exc.cause instanceof IllegalStateException
        assert exc.cause.message == 'error here'
    }

    @Test
    void 'runRule with conversion throwing exception'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        def exc = shouldFail(UnclassifiedWafException) {
            ctx.runRules(new BadMap(delegate: [:]), limits, metrics)
        }
        assert exc.cause.message =~ 'Exception encoding parameters'
        assert exc.cause.cause instanceof IllegalStateException
        assert exc.cause.cause.message == 'error here'
    }

    @Test
    void 'runRule with conversion throwing exception wafContext variant'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        wafContext = ctx.openContext()
        def exc = shouldFail(UnclassifiedWafException) {
            wafContext.run(new BadMap(delegate: [:]), limits, metrics)
        }
        assert exc.cause.message =~ 'Exception encoding parameters'
        assert exc.cause.cause instanceof IllegalStateException
        assert exc.cause.cause.message == 'error here'
    }

    @Test
    void 'rule is run on closed context'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        ctx.close()
        def exc = shouldFail(UnclassifiedWafException) {
            ctx.runRules([:], limits, metrics)
        }
        assertThat exc.message, containsString('This context is already offline')
        ctx = null
    }

    @Test
    void 'addresses are fetched on closed context'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        ctx.close()
        def exc = shouldFail(IllegalStateException) {
            ctx.usedAddresses
        }
        assertThat exc.message, containsString('This context is already offline')
        ctx = null
    }

    @Test
    void 'bytebuffer passed does not represent a map'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        wafContext = ctx.openContext()

        ByteBufferSerializer serializer = new ByteBufferSerializer(limits)
        serializer.serialize([a: 'b'], metrics).withCloseable { lease ->
            ByteBuffer buffer = lease.firstPWArgsByteBuffer
            def slice = buffer.slice()
            slice.position(ByteBufferSerializer.SIZEOF_PWARGS)
            shouldFail(InvalidObjectWafException) {
                wafContext.runWafContext(slice, null, limits, metrics)
            }
        }
    }

    @Test
    void 'bytebuffer passed is not direct buffer'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        wafContext = ctx.openContext()

        shouldFail(Exception) {
            wafContext.runWafContext(
                    ByteBuffer.allocate(ByteBufferSerializer.SIZEOF_PWARGS), null,
                    limits, metrics)
        }
    }

    @Test
    void 'error converting update spec'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        def exc = shouldFail(UnclassifiedWafException) {
            ctx.update('test2', new BadMap(delegate: [arachni_rule: false]))
        }
        assertThat exc.message, containsString('Exception encoding init/update rule specification')
    }

    @Test
    void 'empty update call'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        def exc = shouldFail(UnclassifiedWafException) {
            ctx.update('test2', [foo: 'bar'])
        }
        assertThat exc.message, containsString('Call to ddwaf_update failed')
    }

    @Test
    void 'invalid update call'() {
        ctx = Waf.createHandle('test', ARACHNI_ATOM_V2_1)
        InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
            ctx.update('test2', [rules: [[id: 'foobar']]])
        }
        assertThat exc.ruleSetInfo.numRulesError, equalTo(1)
        assertThat exc.ruleSetInfo.numRulesOK, equalTo(0)
        assertThat exc.ruleSetInfo.errors, hasEntry(
                equalTo('missing key \'conditions\''),
                contains(equalTo('foobar'))
        )
        assertThat exc.message, containsString('Call to ddwaf_update failed')
    }
}
