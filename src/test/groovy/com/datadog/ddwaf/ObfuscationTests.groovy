/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class ObfuscationTests implements WafTrait {

  @Test
  void 'obfuscation by key with default settings'() {
    def ruleSet = ARACHNI_ATOM_V2_1

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)

    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    Waf.ResultWithData awd = context.run(
      ['server.request.headers.no_cookies': ['user-agent': [password: 'Arachni/v1']]], limits, metrics)
    assertThat awd.result, is(Waf.Result.MATCH)

    def json = slurper.parseText(awd.data)

    assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent', 'password']
    assert json[0].rule_matches[0]['parameters'][0].value == '<Redacted>'
    assert json[0].rule_matches[0]['parameters'][0].highlight == ['<Redacted>']
  }

  @Test
  void 'obfuscation by value with default settings'() {
    def ruleSet = ARACHNI_ATOM_V2_1
    def val = 'Arachni/v1 password=s3krit'

    wafDiagnostics = builder.addOrUpdateConfig('test', ruleSet)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    Waf.ResultWithData awd = context.run(
      ['server.request.headers.no_cookies': ['user-agent': [val]]], limits, metrics)
    assertThat awd.result, is(Waf.Result.MATCH)

    def json = slurper.parseText(awd.data)

    assert json[0].rule_matches[0]['parameters'][0].key_path == ['user-agent', '0']
    assert json[0].rule_matches[0]['parameters'][0].value == '<Redacted>'
    assert json[0].rule_matches[0]['parameters'][0].highlight == ['<Redacted>']
  }

  @Test
  void 'no obfuscation if key regex is set to empty string'() {
    def ruleSet = ARACHNI_ATOM_V2_1

    def thisBuilder = new WafBuilder(new WafConfig(obfuscatorKeyRegex: ''))
    wafDiagnostics = thisBuilder.addOrUpdateConfig('test', ruleSet)
    handle = thisBuilder.buildWafHandleInstance()
    context = new WafContext(handle)
    Waf.ResultWithData awd = context.run(
      ['server.request.headers.no_cookies': ['user-agent': [password: 'Arachni/v1']]], limits, metrics)
    assertThat awd.result, is(Waf.Result.MATCH)

    def json = slurper.parseText(awd.data)

    assert json[0].rule_matches[0]['parameters'][0].value == 'Arachni/v1'
    assert json[0].rule_matches[0]['parameters'][0].highlight == ['Arachni/v']
  }

  @Test
  void 'value obfuscation'() {
    def ruleSet = ARACHNI_ATOM_V2_1

    def thisBuilder = new WafBuilder(new WafConfig(obfuscatorValueRegex: 'rachni'))
    wafDiagnostics = thisBuilder.addOrUpdateConfig('test', ruleSet)
    handle = thisBuilder.buildWafHandleInstance()
    context = new WafContext(handle)
    Waf.ResultWithData awd = context.run(
      ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
    assertThat awd.result, is(Waf.Result.MATCH)

    def json = slurper.parseText(awd.data)

    assert json[0].rule_matches[0]['parameters'][0].value == '<Redacted>'
    assert json[0].rule_matches[0]['parameters'][0].highlight == ['<Redacted>']
  }
}

