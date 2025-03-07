package com.datadog.ddwaf

import org.junit.Test

class WafHandleTest extends WafTestBase {
    WafHandle nativeWafHandle = null

    @Test
    void 'waf handle is online'() {
        builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V1_0, ruleSetInfo)
        nativeWafHandle = builder.buildWafHandleInstance(null)

        assert nativeWafHandle.online
        nativeWafHandle.destroy()
    }

    @Test
    void 'waf handle has addresses'() {
        builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V1_0, ruleSetInfo)
        nativeWafHandle = builder.buildWafHandleInstance(null)

        assert nativeWafHandle.knownAddresses.size() == 1
        nativeWafHandle.destroy()
    }

    @Test
    void 'waf handle has actions'() {
        builder.addOrUpdateConfig('enya', ARACHNI_ATOM_BLOCK, ruleSetInfo)
        nativeWafHandle = builder.buildWafHandleInstance(null)

        assert nativeWafHandle.knownActions.size() == 3
        nativeWafHandle.destroy()
    }

    @Test
    void 'waf handle is no more'() {
        builder.addOrUpdateConfig('enya', ARACHNI_ATOM_BLOCK, ruleSetInfo)
        nativeWafHandle = builder.buildWafHandleInstance(null)
        nativeWafHandle.destroy()

        assert !nativeWafHandle.online
        nativeWafHandle = null
    }
}
