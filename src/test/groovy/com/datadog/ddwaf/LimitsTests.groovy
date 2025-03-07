/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import com.datadog.ddwaf.exception.TimeoutWafException
import org.junit.Before
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class LimitsTests extends WafTestBase {
    @Before
    void setUp() {
        maxDepth = 5
        maxElements = 20
        maxStringSize = 100
        timeoutInUs = 200000
        runBudget = 0
    }

    @Test
    void 'maxDepth is respected'() {
        maxDepth = 3

        Waf.ResultWithData awd = runRules(['Arachni'])
        assertThat awd.result, is(Waf.Result.MATCH)

        awd = runRules([['Arachni']])
        assertThat awd.result, is(Waf.Result.OK)
    }

    @Test
    void 'maxDepth is respected - array variant'() {
        maxDepth = 3
        Waf.ResultWithData awd = runRules(['Arachni'] as String[])
        assertThat awd.result, is(Waf.Result.MATCH)

        awd = runRules([['Arachni'] as String[]] as Object[])
        assertThat awd.result, is(Waf.Result.OK)
    }

    @Test
    void 'maxDepth is respected - map variant'() {
        maxDepth = 3
        Waf.ResultWithData awd = runRules([a: 'Arachni'])
        assertThat awd.result, is(Waf.Result.MATCH)

        awd = runRules([a: [a: 'Arachni']])
        assertThat awd.result, is(Waf.Result.OK)
    }

    @Test
    void 'maxElements is respected'() {
        maxElements = 5
        Waf.ResultWithData awd = runRules(['a', 'Arachni'])
        assertThat awd.result, is(Waf.Result.MATCH)

        // the map and list count as elements
        awd = runRules(['a', 'b', 'Arachni'])
        assertThat awd.result, is(Waf.Result.OK)
    }

    @Test
    void 'maxElements is respected - array variant'() {
        maxElements = 5
        Waf.ResultWithData awd = runRules(['a', 'Arachni'] as String[])
        assertThat awd.result, is(Waf.Result.MATCH)

        // the map and list count as elements
        awd = runRules(['a', 'b', 'Arachni'] as String[])
        assertThat awd.result, is(Waf.Result.OK)
    }

    @Test
    void 'maxElements is respected - map variant'() {
        maxElements = 5
        Waf.ResultWithData awd = runRules([a: 'a', b: 'Arachni'])
        assertThat awd.result, is(Waf.Result.MATCH)

        // the map and list count as elements
        awd = runRules([a: 'a', b: 'b', c: 'Arachni'] as String[])
        assertThat awd.result, is(Waf.Result.OK)
    }

    @Test
    void 'maxStringSize is observed'() {
        maxStringSize = 100
        Waf.ResultWithData awd = runRules(' ' * 93 + 'Arachni')
        assertThat awd.result, is(Waf.Result.MATCH)

        awd = runRules(' ' * 94 + 'Arachni')
        assertThat awd.result, is(Waf.Result.OK)
    }

    @Test
    void 'maxStringSize is observed - map key variant'() {
        maxStringSize = 100

        Waf.ResultWithData awd = runRules([(' ' * 93 + 'Arachni'): 'a'])
        // expected failure: running on keys is not possible now on libddwaf
        shouldFail(AssertionError) {
            assertThat awd.result, is(Waf.Result.MATCH)
        }

        awd = runRules([(' ' * 94 + 'Arachni'): 'a'])
        assertThat awd.result, is(Waf.Result.OK)
    }

    @Test
    void 'generalBudgetInUs is observed during PWARgs conversion'() {
        timeoutInUs = 5

        shouldFail(TimeoutWafException) {
            runRules([['Arachni']])
        }
    }
}
