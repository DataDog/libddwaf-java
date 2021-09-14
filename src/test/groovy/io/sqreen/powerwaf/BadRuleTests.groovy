/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import org.junit.Test

class BadRuleTests implements PowerwafTrait {

    @Test(expected = IllegalArgumentException)
    void 'no events'() {
        ctx = Powerwaf.createContext('test', [version: '0.0', events: []])
    }

    @Test(expected = IllegalArgumentException)
    void 'version is not a string'() {
        def rules = [:] + ARACHNI_ATOM
        rules['version'] = 99
        ctx = Powerwaf.createContext('test', rules)
    }
}
