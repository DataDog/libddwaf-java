/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf

import com.datadog.ddwaf.WafDiagnostics.SectionInfo
import com.datadog.ddwaf.exception.InvalidRuleSetException
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail

class WafDiagnosticsTest implements WafTrait {

  @Test
  void 'WafDiagnostics returns expected values for addOrUpdateConfig'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)

    assert wafDiagnostics != null
    assert wafDiagnostics.rules != null
    assert wafDiagnostics.wellStructured
    assert wafDiagnostics.numConfigOK == 1
    assert wafDiagnostics.numConfigError == 0
  }

  @Test
  void 'WafDiagnostics contains the correct ruleset version'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)

    assert wafDiagnostics != null
    assert wafDiagnostics.rulesetVersion == '1.2.6'
  }

  @Test
  void 'SectionInfo with only error contains expected fields'() {
    String errorMessage = 'Test error message'
    SectionInfo sectionInfo = new SectionInfo(errorMessage)

    assert sectionInfo.error == errorMessage
    assert sectionInfo.loaded.size() == 0
    assert sectionInfo.failed.size() == 0
    assert sectionInfo.errors.size() == 0
  }

  @Test
  void 'SectionInfo with full information contains expected fields'() {
    List<String> skipped = ['skip1', 'skip2']
    List<String> loaded = ['load1', 'load2']
    List<String> failed = ['fail1']
    Map<String, List<String>> warnings = ['warning1': ['rule1', 'rule2']]
    Map<String, List<String>> errors = ['error1': ['rule3']]

    SectionInfo sectionInfo = new SectionInfo(skipped, loaded, failed, warnings, errors)

    assert sectionInfo != null
    assert sectionInfo.loaded == loaded
    assert sectionInfo.failed == failed
    assert sectionInfo.errors == errors
  }

  @Test
  void 'WafDiagnostics getAllErrors returns combined errors from all sections'() {
    // Create section infos with different errors
    SectionInfo rules = new SectionInfo(
      null, ['rule1'], ['fail1'], null, ['error1': ['rule2']]
      )
    SectionInfo customRules = new SectionInfo(
      null, ['custom1'], ['failCustom'], null, ['error2': ['custom2']]
      )

    WafDiagnostics diagnostics = new WafDiagnostics(
      null, '1.0', rules, customRules, null, null, null, null, null, null, null
      )

    Map<String, List<String>> allErrors = diagnostics.allErrors
    assert allErrors.size() == 2
    assert allErrors.containsKey('error1')
    assert allErrors.containsKey('error2')
    assert allErrors.get('error1') == ['rule2']
    assert allErrors.get('error2') == ['custom2']
  }

  @Test
  void 'WafDiagnostics with only error is well structured'() {
    WafDiagnostics diagnostics = new WafDiagnostics(
      'Global error', null, null, null, null, null, null, null, null, null, null
      )

    assert diagnostics.wellStructured
    assert diagnostics.numConfigOK == 0
    assert diagnostics.numConfigError == 1
  }

  @Test
  void 'WafDiagnostics countErrorsForSection handles different section error types'() {
    // Section with direct error
    SectionInfo directError = new SectionInfo('Direct error')
    // Section with failed items
    SectionInfo failedItems = new SectionInfo(null, ['ok'], ['failed1', 'failed2'], null, null)
    // Section with error map
    SectionInfo errorMap = new SectionInfo(null, ['ok'], null, null, ['error1': ['rule1'], 'error2': ['rule2']])

    WafDiagnostics diagnostics1 = new WafDiagnostics(
      null, '1.0', directError, null, null, null, null, null, null, null, null
      )
    assert diagnostics1.numConfigError == 1

    WafDiagnostics diagnostics2 = new WafDiagnostics(
      null, '1.0', failedItems, null, null, null, null, null, null, null, null
      )
    assert diagnostics2.numConfigError == 2

    WafDiagnostics diagnostics3 = new WafDiagnostics(
      null, '1.0', errorMap, null, null, null, null, null, null, null, null
      )
    assert diagnostics3.numConfigError == 2
  }

  @Test
  void 'Invalid ruleset throws InvalidRuleSetException'() {
    Map<String, Object> invalidRuleset = [
      version: '2.1',
      rules: [
        [
          id: 'invalid_rule',
          name: 'Invalid Rule',
          conditions: [
            [
              // Missing required fields
              operator: 'invalid_operator'
            ]
          ]
        ]
      ]
    ]

    def exception = shouldFail(InvalidRuleSetException) {
      builder.addOrUpdateConfig('test', invalidRuleset)
    }

    assert exception != null
    assert exception.message != null
    assert !exception.message.empty
  }

  @Test
  void 'WafDiagnostics toString includes all non-null sections'() {
    SectionInfo rules = new SectionInfo(null, ['rule1'], null, null, null)

    WafDiagnostics diagnostics = new WafDiagnostics(
      null, '1.0', rules, null, null, null, null, null, null, null, null
      )

    String diagnosticsString = String.valueOf(diagnostics)
    assert diagnosticsString.contains('rulesetVersion=\'1.0\'')
    assert diagnosticsString.contains('rules=SectionInfo[loaded=[rule1]')
  }
}
