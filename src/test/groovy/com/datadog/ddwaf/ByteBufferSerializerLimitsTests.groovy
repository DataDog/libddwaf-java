/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.hamcrest.MatcherAssert
import org.junit.Test

import static org.hamcrest.Matchers.is

class ByteBufferSerializerLimitsTests extends ByteBufferSerializerTestsBase {

    @Test
    void 'observes max elements limit'() {
        maxElements = 5
        def obj = [a: 1, b: 2, c: [3, 4], d: 5, e: 6]
        lease = serializer.serialize(obj, metrics)

        String res = Waf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        // maps and arrays count towards the limits.
        // d is included because when the map starts there are still 4 elements
        // remaining and the amount of entries needs is preallocated before
        // going through them
        def exp = p '''
        <MAP>
          a: <SIGNED> 1
          b: <SIGNED> 2
          c: <ARRAY>
            <SIGNED> 3
          d: <MAP>
        '''
        MatcherAssert.assertThat res, is(exp)
        assertMetrics(0, 1, 0)
    }

    @Test
    void 'observes maximum depth'() {
        maxDepth = 2
        def obj = [ // 1
                a: [ // 2
                        [ // 3: elements here are not serialized anymore
                                b: 'd']]]
        lease = serializer.serialize(obj, metrics)

        String res = Waf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          a: <ARRAY>
            <MAP>
              b: <MAP>
        '''
        MatcherAssert.assertThat res, is(exp)
        assertMetrics(0, 0, 1)
    }

    @Test
    void 'observes maximum string size'() {
        maxStringSize = 3
        // the size is number of UTF-16 code units
        def str = '\uFFFD' * 3 + 'x'

        def obj = ['12\uAAAA4': str]
        lease = serializer.serialize(obj, metrics)

        String res = Waf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          12\uAAAA: <STRING> \uFFFD\uFFFD\uFFFD
        '''
        MatcherAssert.assertThat res, is(exp)
        assertMetrics(2, 0, 0)
    }

    @Test
    void 'does not truncate string at exactly max size'() {
        maxStringSize = 3

        lease = serializer.serialize(['x': 'xxx'], metrics)

        String res = Waf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          x: <STRING> xxx
        '''
        MatcherAssert.assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'does not truncate parameter name at exactly max size'() {
        maxStringSize = 3

        lease = serializer.serialize(['xxx': 'x'], metrics)

        String res = Waf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          xxx: <STRING> x
        '''
        MatcherAssert.assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'observes maximum string size in parameter name'() {
        maxStringSize = 3

        lease = serializer.serialize(['xxxx': 'x'], metrics)

        String res = Waf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          xxx: <STRING> x
        '''
        MatcherAssert.assertThat res, is(exp)
        assertMetrics(1, 0, 0)
    }

}
