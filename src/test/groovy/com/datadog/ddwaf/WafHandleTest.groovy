package com.datadog.ddwaf

import org.junit.Test

class WafHandleTest extends WafTestBase {
    WafHandle nativeWafHandle = null

    @Test
    void 'waf handle is online'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V1_0)
        nativeWafHandle = builder.buildWafHandleInstance(null)

        assert nativeWafHandle.online
        nativeWafHandle.destroy()
    }

    @Test
    void 'waf handle has addresses'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_V1_0)
        nativeWafHandle = builder.buildWafHandleInstance(null)

        assert nativeWafHandle.knownAddresses.size() == 1
        nativeWafHandle.destroy()
    }

    @Test
    void 'waf handle has actions'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_BLOCK)
        nativeWafHandle = builder.buildWafHandleInstance(null)

        assert nativeWafHandle.knownActions.size() == 3
        nativeWafHandle.destroy()
    }

    @Test
    void 'waf handle is no more'() {
        ruleSetInfo = builder.addOrUpdateConfig('enya', ARACHNI_ATOM_BLOCK)
        nativeWafHandle = builder.buildWafHandleInstance(null)
        nativeWafHandle.destroy()

        assert !nativeWafHandle.online
        nativeWafHandle = null
    }
}
