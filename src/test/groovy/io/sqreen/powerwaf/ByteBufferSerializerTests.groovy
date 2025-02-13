/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import org.junit.After
import org.junit.Before
import org.junit.Test

import java.nio.ByteBuffer
import java.nio.CharBuffer

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.*

class ByteBufferSerializerTests implements PowerwafTrait {

    @Lazy
    ByteBufferSerializer serializer = new ByteBufferSerializer(limits)

    ByteBufferSerializer.ArenaLease lease

    PowerwafMetrics metrics

    @Before
    void before() {
        metrics = new PowerwafMetrics()
    }

    @After
    @Override
    void after() {
        lease?.close()
    }

    @Test
    void 'can serialize a string'() {
        lease = serializer.serialize([my_key: 'my string'], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <STRING> my string
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize a CharBuffer'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cb = CharBuffer.wrap(storedBody, 0, storedBody.length)

        lease = serializer.serialize([my_key: cb], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <STRING> my string
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize a long'() {
        long l = -2305843009213693952
        lease = serializer.serialize([my_key: l], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <SIGNED> -2305843009213693952
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize a big int as a long'() {
        BigInteger bi = 18446744073709551623.toLong() // 2^64 + 7
        lease = serializer.serialize([my_key: bi], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <SIGNED> 7
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize a boolean'() {
        lease = serializer.serialize([my_key: [true, false]], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <BOOL> true
            <BOOL> false
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize decimals and floats'() {
        lease = serializer.serialize([my_key: [8.5d, 8.5f, 8.5]], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <FLOAT> 8.500000000000000000e+00
            <FLOAT> 8.500000000000000000e+00
            <FLOAT> 8.500000000000000000e+00
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize lists'() {
        def arr = [1, 2, 3]
        lease = serializer.serialize([my_key: arr], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <SIGNED> 1
            <SIGNED> 2
            <SIGNED> 3
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize arrays'() {
        def arr = [1, 2, 3] as byte[]
        lease = serializer.serialize([my_key: arr], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <SIGNED> 1
            <SIGNED> 2
            <SIGNED> 3
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize maps'() {
        def map = [(1): 'xx', 2: 'yy']
        lease = serializer.serialize([my_key: map], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <MAP>
            1: <STRING> xx
            2: <STRING> yy
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize iterables'() {
        def list = [1, 2]
        def iterable = [
            iterator: { -> list.iterator() }
        ] as Iterable
        lease = serializer.serialize([my_key: iterable], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <ARRAY>
            <SIGNED> 1
            <SIGNED> 2
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'unknown values are serialized as nulls'() {
        lease = serializer.serialize([my_key: new Object()], metrics)
        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          my_key: <NULL>
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'iteration yields fewer elements the second time'() {
        def list = [1, 2]
        def first = true
        def iterable = [
                iterator: { ->
                    if (first) {
                        first = false
                        list.iterator()
                    } else {
                        [1].iterator()
                    }
                }
        ] as Iterable

        shouldFail(ConcurrentModificationException) {
            serializer.serialize([key: iterable], metrics)
        }
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'map reports size larger than what is gotten through iteration'() {
        def orig = [b: 2]
        Map newMap = new Map() {
            @Delegate
            Map delegate = orig

            @Override
            int size() {
                2
            }
        }
        shouldFail(ConcurrentModificationException) {
            serializer.serialize(newMap, metrics)
        }
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'collection reports size larger than what is gotten through iteration'() {
        def orig = ['x']
        Collection newColl = new Collection() {
            @Delegate
            Collection delegate = orig

            @Override
            int size() {
                2
            }
        }
        shouldFail(ConcurrentModificationException) {
            serializer.serialize([a: newColl], metrics)
        }
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'force creation of new string segment'() {
        maxStringSize = Integer.MAX_VALUE

        def size = ByteBufferSerializer.STRINGS_MIN_SEGMENTS_SIZE + 1
        def str = 'x' * size
        2.times {
            serializer.serialize([key1: str, key2: 42], metrics).withCloseable { lease ->
                String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
                assertThat res, containsString(str)
            }
        }
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'force creation of new pwargs segment'() {
        maxElements = Integer.MAX_VALUE

        def size = ByteBufferSerializer.PWARGS_MIN_SEGMENTS_SIZE
        def arr = ['x'] * size
        2.times {
            serializer.serialize([key1: arr, key2: 'x'], metrics).withCloseable { lease ->
                String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
                assertThat res.count('\n'), is(size + 3)
            }
        }
        assertMetrics(0, 0, 0)
    }

    // Limits

    @Test
    void 'observes max elements limit'() {
        maxElements = 5
        def obj = [a: 1, b: 2, c: [3, 4], d: 5, e: 6]
        lease = serializer.serialize(obj, metrics)

        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
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
        assertThat res, is(exp)
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

        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          a: <ARRAY>
            <MAP>
              b: <MAP>
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 1)
    }

    @Test
    void 'observes maximum string size'() {
        maxStringSize = 3
        // the size is number of UTF-16 code units
        def str = '\uFFFD' * 3 + 'x'

        def obj = ['12\uAAAA4': str]
        lease = serializer.serialize(obj, metrics)

        String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
        def exp = p '''
        <MAP>
          12\uAAAA: <STRING> \uFFFD\uFFFD\uFFFD
        '''
        assertThat res, is(exp)
        assertMetrics(2, 0, 0)
    }

    @Test
    void 'additive basic usage'() {
        lease = ByteBufferSerializer.blankLease
        ByteBuffer bb1 = lease.serializeMore(limits, [a: 'b'], metrics)
        ByteBuffer bb2 = lease.serializeMore(limits, [c: 'd'], metrics)

        String res = Powerwaf.pwArgsBufferToString(bb1)
        def exp = p '''
        <MAP>
          a: <STRING> b
        '''
        assertThat res, is(exp)

        res = Powerwaf.pwArgsBufferToString(bb2)
        exp = p '''
        <MAP>
          c: <STRING> d
        '''
        assertThat res, is(exp)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'first pwargs buffer with nothing written'() {
        lease = ByteBufferSerializer.blankLease
        shouldFail(IllegalStateException) { lease.firstPWArgsByteBuffer }
        assertMetrics(0, 0, 0)
    }

    private static String p(String s) {
        s.stripIndent()[1..-1]
    }

    private void assertMetrics(Long countStringTooLong, Long countListMapTooLarge, Long countObjectTooDeep) {
        assertThat(metrics.wafInputsTruncatedStringTooLongCount, is(countStringTooLong))
        assertThat(metrics.wafInputsTruncatedListMapTooLargeCount, is(countListMapTooLarge))
        assertThat(metrics.wafInputsTruncatedObjectTooDeepCount, is(countObjectTooDeep))
    }
}
