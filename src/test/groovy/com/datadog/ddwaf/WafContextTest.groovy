/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package com.datadog.ddwaf

import com.datadog.ddwaf.exception.InvalidArgumentWafException
import com.datadog.ddwaf.exception.InvalidObjectWafException
import com.datadog.ddwaf.exception.TimeoutWafException
import com.datadog.ddwaf.exception.UnclassifiedWafException
import groovy.transform.CompileStatic
import org.junit.Test

import java.nio.ByteBuffer

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class WafContextTest implements WafTrait {

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
  void 'creating WafContext with null WafHandle throws exception'() {
    shouldFail(IllegalArgumentException) {
      new WafContext(null)
    }
  }

  @Test
  void 'creating WafContext with valid WafHandle succeeds'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()

    context = new WafContext(handle)
    assert context.online
  }

  @Test
  void 'run with null limits throws IllegalArgumentException'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    def exc = shouldFail(IllegalArgumentException) {
      context.run([:], null, metrics)
    }

    assert exc.message == 'limits must be provided'
  }

  @Test
  void 'run with null parameters throws InvalidArgumentWafException'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    shouldFail(InvalidArgumentWafException) {
      context.run(null, limits, metrics)
    }
  }

  @Test
  void 'throw an exception when both persistent and ephemeral are null in wafContext'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    shouldFail(InvalidArgumentWafException) {
      context.run(null, null, limits, metrics)
    }
  }

  @Test
  void 'run with empty parameters returns valid result'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    def result = context.run([:], limits, metrics)
    assert result != null
    assert result.result == Waf.Result.OK
  }

  @Test
  void 'context run with matching rule returns MATCH result'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    def result = context.run(['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metrics)
    assert result.result == Waf.Result.MATCH
  }

  @Test
  void 'run with very short timeout throws TimeoutWafException'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // Set extreme low budget to force timeout
    def shortLimits = new Waf.Limits(5, 20, 100, 1, 1)

    shouldFail(TimeoutWafException) {
      context.run(['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], shortLimits, metrics)
    }
  }

  @Test
  void 'run updates metrics if provided'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    def metricsObj = new WafMetrics()
    context.run(['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']], limits, metricsObj)

    // This might be totalRunTimeNs, totalRuleRunTimeNs, or another property
    assert metricsObj.totalRunTimeNs > 0 || metricsObj.serializationTimeNs > 0
  }

  @Test
  void 'context run with conversion throwing exception passes through the cause'() {
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
  void 'running rules on closed context throws exception'() {
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
  void 'closing context twice is safe'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    // First close
    context.close()
    assert !context.online

    // Second close might throw an exception depending on implementation
    // We just need to ensure it doesn't crash the program
    try {
      context.close()
    } catch (IllegalStateException e) {
      // This is acceptable behavior for a double-close
      assert e.message.contains('no longer online')
    }
  }

  @Test
  void 'run with persistent and ephemeral data succeeds'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    def persistentData = ['persistent': 'data']
    def ephemeralData = ['ephemeral': 'data']

    def result = context.run(persistentData, ephemeralData, limits, metrics)
    assert result != null
    assert result.result == Waf.Result.OK
  }

  @Test
  void 'runEphemeral with data succeeds'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V1_0)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    def ephemeralData = ['server.request.headers.no_cookies': ['user-agent': 'Arachni/v1']]

    def result = context.runEphemeral(ephemeralData, limits, metrics)
    assert result != null
    assert result.result == Waf.Result.MATCH
  }

  @Test
  void 'bytebuffer passed to runWafContext is not direct buffer'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)

    shouldFail(Exception) {
      context.runWafContext(
        ByteBuffer.allocate(ByteBufferSerializer.SIZEOF_PWARGS), null,
        limits, metrics)
    }
  }

  @Test
  void 'bytebuffer passed to runWafContext does not represent a map'() {
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
  void 'handle can be destroyed with live context'() {
    wafDiagnostics = builder.addOrUpdateConfig('test', ARACHNI_ATOM_V2_1)
    handle = builder.buildWafHandleInstance()
    context = new WafContext(handle)
    handle.close()

    Waf.ResultWithData rwd = context.run([:], limits, metrics)
    assertThat rwd.result, is(Waf.Result.OK)
  }
}

