package com.datadog.ddwaf

import org.junit.Test

class NativeWafHandleTest extends WafTestBase {
    @Test
    void 'native waf handle is online'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_V1_0, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)

        assert nativeWafHandle.online
    }

    @Test
    void 'native waf handle has addresses'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_V1_0, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)

        assert nativeWafHandle.knownAddresses.size() == 1
    }

    @Test
    void 'native waf handle has actions'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_BLOCK, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)

        assert nativeWafHandle.knownActions.size() == 3
    }

    @Test
    void 'native waf handle is no more'() {
        builder.addOrUpdateRuleConfig('enya', ARACHNI_ATOM_BLOCK, ruleSetInfo)
        nativeWafHandle = builder.buildNativeWafHandleInstance(nativeWafHandle)
        nativeWafHandle.destroy()

        assert !nativeWafHandle.online
        nativeWafHandle = null
    }
}
