package com.datadog.ddwaf

import com.datadog.ddwaf.exception.InternalWafException
import org.junit.Test
import static groovy.test.GroovyAssert.shouldFail

class WafBuilderTest extends WafTestBase {
    @Test
    void 'test init builder'() {
        if (builder?.online) {
            builder.destroy() // must keep memory clean
        }
        builder = new WafBuilder()
        assert builder != null
        assert builder.online
    }

    @Test
    void 'test init builder with custom config'() {
        if (builder?.online) {
            builder.destroy() // must keep memory clean
        }
        builder = new WafBuilder(new WafConfig())
        assert builder != null
        assert builder.online
    }

    @Test
    void 'test destroy builder'() {
        if (builder?.online) {
            builder.destroy()
        }
        assert builder != null
        assert !builder.online
    }

    @Test
    void 'test remove builder config'() {
        if (builder?.online) {
            builder.destroy()
        }

        builder = new WafBuilder()

        shouldFail(InternalWafException) {
            builder.buildWafHandleInstance(null)
        }
    }

}

