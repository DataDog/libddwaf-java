/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import com.datadog.ddwaf.exception.InvalidRuleSetException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail

class BadRuleTests implements WafTrait {

  @Test
  void 'no events'() {
    shouldFail(InvalidRuleSetException) {
      wafDiagnostics = builder.addOrUpdateConfig('test', [version: '0.0', events: []])
    }
  }

  @Test
  void 'rule without id'() {
    def rules = copyMap(ARACHNI_ATOM_V2_1)
    rules['rules'][0].remove('id')
    InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
      builder.addOrUpdateConfig('test', rules)
    }
    wafDiagnostics = exc.wafDiagnostics

    assert wafDiagnostics.numConfigOK == 0
    assert wafDiagnostics.numConfigError == 1
    assert wafDiagnostics.allErrors == ['missing key \'id\'':['index:0']]
  }

  @Test
  void 'rules have the wrong form'() {
    def rules = copyMap(ARACHNI_ATOM_V2_1)
    rules['rules'] = [:]

    InvalidRuleSetException exc = shouldFail(InvalidRuleSetException) {
      builder.addOrUpdateConfig('test', rules)
    }
    wafDiagnostics = exc.wafDiagnostics

    assert wafDiagnostics.numConfigOK == 0
    assert wafDiagnostics.numConfigError == 1
    assert wafDiagnostics.rules.error == "bad cast, expected 'array', obtained 'map'"
  }

  @Test
  void 'duplicated rule'() {
    def rules = copyMap(ARACHNI_ATOM_V2_1)
    rules['rules'] << rules['rules'][0]
    wafDiagnostics = builder.addOrUpdateConfig('test', rules)

    assert wafDiagnostics.numConfigOK == 1
    assert wafDiagnostics.numConfigError == 1
    assert wafDiagnostics.allErrors == ['duplicate rule': ['arachni_rule'] as String[]]

    handle = builder.buildWafHandleInstance()
    assert handle != null
  }

  private Map copyMap(Map map) {
    new JsonSlurper().parseText(JsonOutput.toJson(map))
  }
}

