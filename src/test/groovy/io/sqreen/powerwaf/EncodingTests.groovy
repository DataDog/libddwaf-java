/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import org.junit.Before
import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString

class EncodingTests implements PowerwafTrait {

    @Before
    void assignContext() {
        ctx = Powerwaf.createContext('test', ARACHNI_ATOM_V1_0)
    }

    @Test
    void 'user input has an unpaired leading surrogate'() {
        Powerwaf.ResultWithData awd = runRules('Arachni\uD800')

        def json = slurper.parseText(awd.data)
        assert json[0].rule_matches[0].parameters[0].value == 'Arachni\uFFFD'
    }

    @Test
    void 'user input has unpaired leading surrogate'() {
        Powerwaf.ResultWithData awd = runRules 'Arachni\uD800Ā'

        def json = slurper.parseText(awd.data)
        assert json[0].rule_matches[0].parameters[0].value == 'Arachni\uFFFDĀ'
    }

    @Test
    void 'user input has unpaired trailing surrogate'() {
        Powerwaf.ResultWithData awd = runRules 'Arachni\uDC00x'

        def json = slurper.parseText(awd.data)
        assert json[0].rule_matches[0].parameters[0].value == 'Arachni\uFFFDx'
    }

    @Test
    void 'user input has two adjacent leading surrogates and does not invalidate the second'() {
        Powerwaf.ResultWithData awd = runRules 'Arachni\uD800\uD801\uDC00'

        assertThat awd.data, containsString('Arachni\uFFFD\uD801\uDC00')
    }

    @Test
    void 'user input has NUL character before and after matching part'() {
        Powerwaf.ResultWithData awd = runRules '\u0000Arachni\u0000'

        assertThat awd.data, containsString('\\u0000Arachni\\u0000')
    }
}
