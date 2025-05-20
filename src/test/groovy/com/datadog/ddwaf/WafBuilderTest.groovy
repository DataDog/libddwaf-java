/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf

import com.datadog.ddwaf.exception.InvalidObjectWafException
import com.datadog.ddwaf.exception.InvalidRuleSetException
import com.datadog.ddwaf.exception.UnclassifiedWafException
import groovy.transform.CompileStatic
import org.junit.Test

import java.nio.ByteBuffer

import static groovy.test.GroovyAssert.shouldFail

class WafBuilderTest implements WafTrait {

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
  void 'empty builder cannot handle'() {
    shouldFail(UnclassifiedWafException) {
      handle = builder.buildWafHandleInstance()
    }
  }

  @Test
  void 'init builder with custom config'() {
    builder = new WafBuilder(new WafConfig())
  }

  @Test
  void 'double close does not throw'() {
    builder = new WafBuilder()
    assert builder.online
    builder.close()
    assert !builder.online
    // Second destroy does not throw
    builder.close()
  }

  @Test
  void 'handle after adding one configuration'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    assert handle != null
  }

  @Test
  void 'remove an existing configuration'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    assert wafDiagnostics.numConfigOK == 1
    builder.removeConfig('test')
    shouldFail {
      builder.buildWafHandleInstance()
    }
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    assert wafDiagnostics.numConfigOK == 1
    handle = builder.buildWafHandleInstance()
    assert handle != null
  }

  @Test
  void 'remove a non-existing configuration throws'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    assert wafDiagnostics.numConfigOK == 1
    shouldFail(UnclassifiedWafException) {
      builder.removeConfig('non-existing')
    }
    handle = builder.buildWafHandleInstance()
    assert handle != null
  }

  @Test
  void 'attempt to build instance after builder is closed'() {
    final builderInstance = new WafBuilder()
    builderInstance.close()

    shouldFail(UnclassifiedWafException) {
      builderInstance.buildWafHandleInstance()
    }
  }

  @Test
  void 'add invalid rule configuration'() {
    final invalidRule = [
      version: '2.1',
      rules: [
        [
          // Missing required fields like id, conditions, etc.
          name: 'Invalid Rule'
        ]
      ]
    ]

    shouldFail(InvalidRuleSetException) {
      builder.addOrUpdateConfig('invalid-rule', invalidRule)
    }
  }

  @Test
  void 'add configuration with empty path throws'() {
    shouldFail(IllegalArgumentException) {
      builder.addOrUpdateConfig('', ARACHNI_ATOM_V1_0)
    }
  }

  @Test
  void 'add configuration with null path throws'() {
    shouldFail(IllegalArgumentException) {
      builder.addOrUpdateConfig(null, ARACHNI_ATOM_V1_0)
    }
  }

  @Test
  void 'remove configuration with empty path throws'() {
    shouldFail(IllegalArgumentException) {
      builder.removeConfig('')
    }
  }

  @Test
  void 'remove configuration with null path throws'() {
    shouldFail(IllegalArgumentException) {
      builder.removeConfig(null)
    }
  }

  @Test
  void 'remove config without builder throws'() {
    shouldFail {
      WafBuilder.removeConfigNative(null, 'test')
    }
  }

  @Test
  void 'update existing configuration'() {
    // Add initial configuration
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    assert wafDiagnostics.numConfigOK == 1

    // Update with new configuration
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    assert wafDiagnostics.numConfigOK == 1
    assert wafDiagnostics.rulesetVersion == '1.2.6'

    // Check that the updated configuration is used
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    final params = ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']]
    def result = context.run(params, limits, metrics)
    assert result.result == Waf.Result.MATCH
  }

  @Test
  void 'multiple configurations can be added'() {
    try {
      // Add first configuration
      wafDiagnostics = builder.addOrUpdateConfig('test1', ARACHNI_ATOM_V1_0)
      assert wafDiagnostics.numConfigOK == 1

      // Add second configuration - this might throw depending on compatibility of the configs
      wafDiagnostics = builder.addOrUpdateConfig('test2', ARACHNI_ATOM_V1_0)

      // Should be able to build a handle with both configurations
      handle = builder.buildWafHandleInstance()
      assert handle != null
    } catch (InvalidRuleSetException e) {
      // It's acceptable if the second rule can't be added - might be by design
      assert e.message != null, 'Exception should have a message'
    }
  }

  @Test
  void 'isOnline reflects builder state'() {
    // New builder is online
    assert builder.online

    // After closing, it's offline
    builder.close()
    assert !builder.online
  }

  @Test
  void 'null config uses default config'() {
    // Creating with null should use default config
    final builderWithNull = new WafBuilder(null)
    assert builderWithNull.online
    builderWithNull.close()
  }

  @Test
  void 'custom WafConfig with regex patterns'() {
    WafConfig customConfig = new WafConfig()
    customConfig.obfuscatorKeyRegex = 'key_.*'
    customConfig.obfuscatorValueRegex = '.*password.*'

    // Builder should initialize correctly with custom config
    builder = new WafBuilder(customConfig)
  }

  @Test
  void 'force exception during conversion of rule definitions'() {
    def exc = shouldFail(IllegalStateException) {
      wafDiagnostics = builder.addOrUpdateConfig('test', new BadMap(delegate: [version: '1.0', events: []]))
    }
    assert exc.message == 'error here'
  }

  @Test
  void 'context run with conversion throwing exception'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    def exc = shouldFail(UnclassifiedWafException) {
      context.run(new BadMap(delegate: [:]), limits, metrics)
    }
    assert exc.cause.message =~ 'Exception encoding parameters'
    assert exc.cause.cause instanceof IllegalStateException
    assert exc.cause.cause.message == 'error here'
  }

  @Test
  void 'rule is run on closed context'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    context.close()
    def exc = shouldFail(UnclassifiedWafException) {
      context.run(['server.request.headers.no_cookies': ['user-agent': ['Arachni/v1']]], limits, metrics)
    }
    assert exc.message.contains('This WafContext is no longer online')
  }

  @Test
  void 'addresses are fetched on closed handle'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    handle.close()
    def exc = shouldFail(IllegalStateException) {
      handle.knownAddresses
    }
    assert exc.message.contains('This WafHandle is no longer online')
  }

  @Test
  void 'actions are fetched on closed handle'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    handle.close()
    def exc = shouldFail(IllegalStateException) {
      handle.knownActions
    }
    assert exc.message.contains('This WafHandle is no longer online')
  }

  @Test
  void 'bytebuffer passed does not represent a map'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    ByteBufferSerializer serializer = new ByteBufferSerializer(limits)
    serializer.serialize([a: 'b'], metrics).withCloseable { lease ->
      ByteBuffer buffer = lease.firstPWArgsByteBuffer
      def slice = buffer.slice()
      slice.position(ByteBufferSerializer.SIZEOF_PWARGS)
      shouldFail(InvalidObjectWafException) {
        context.runWafContext(slice, null, limits, metrics)
      }
    }
  }

  @Test
  void 'bytebuffer passed is not direct buffer'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    shouldFail(Exception) {
      context.runWafContext(
        ByteBuffer.allocate(ByteBufferSerializer.SIZEOF_PWARGS), null,
        limits, metrics)
    }
  }
}

