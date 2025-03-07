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

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class ObfuscationTests extends WafTestBase {

    @Test
    void 'obfuscation by key with default settings'() {
        def ruleSet = ARACHNI_ATOM_V2_1

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)

        Waf.ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': [password: 'Arachni/v1']]], limits, wafMetrics,
                builder.buildWafHandleInstance(null))
        assertThat awd.result, is(Waf.Result.MATCH)

        def json = new JsonSlurper().parseText(awd.data)

        assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent', 'password']
        assert json[0].rule_matches[0]['parameters'][0].value == '<Redacted>'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['<Redacted>']
    }

    @Test
    void 'obfuscation by value with default settings'() {
        def ruleSet = ARACHNI_ATOM_V2_1
        def val = 'Arachni/v1 password=s3krit'

        ruleSetInfo = builder.addOrUpdateConfig('enya', ruleSet)
        Waf.ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': [val]]], limits, wafMetrics,
                builder.buildWafHandleInstance(null))
        assertThat awd.result, is(Waf.Result.MATCH)

        def json = new JsonSlurper().parseText(awd.data)

        assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent', '0']
        assert json[0].rule_matches[0]['parameters'][0].value == '<Redacted>'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['<Redacted>']
    }

    @Test
    void 'no obfuscation if key regex is set to empty string'() {
        def ruleSet = ARACHNI_ATOM_V2_1

        def thisBuilder = new WafBuilder(new WafConfig(obfuscatorKeyRegex: ''))
        ruleSetInfo = thisBuilder.addOrUpdateConfig('enya', ruleSet)

        Waf.ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': [password: 'Arachni/v1']]], limits, wafMetrics,
                thisBuilder.buildWafHandleInstance(null))
        assertThat awd.result, is(Waf.Result.MATCH)

        def json = new JsonSlurper().parseText(awd.data)

        assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni/v1'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni/v']
    }

    @Test
    void 'value obfuscation'() {
        def ruleSet = ARACHNI_ATOM_V2_1

        def thisBuilder = new WafBuilder(new WafConfig(obfuscatorValueRegex: 'rachni'))
        ruleSetInfo = thisBuilder.addOrUpdateConfig('enya', ruleSet)
        Waf.ResultWithData awd = Waf.runContext(
                ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, wafMetrics,
                thisBuilder.buildWafHandleInstance(null))
        assertThat awd.result, is(Waf.Result.MATCH)

        def json = new JsonSlurper().parseText(awd.data)

        assert json[0].rule_matches[0]['parameters'][0].value == '<Redacted>'
        assert json[0].rule_matches[0]['parameters'][0].highlight == ['<Redacted>']
    }
}
