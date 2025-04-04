/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.Test

import java.nio.CharBuffer

import static groovy.test.GroovyAssert.shouldFail

@SuppressWarnings('MethodCount')
class ByteBufferSerializerTests extends ByteBufferSerializerTestsBase {

    @Test
    void 'can serialize a string'() {
        assertSerializeValue('my string')
    }

    @Test
    void 'can serialize Unicode characters'() {
        // Test boundaries for:
        //   - 1-byte UTF-8 encoding: U+007F, U+0080, U+0081
        //   - 2-byte UTF-8 encoding: U+07FF, U+0800, U+0801
        //   - Surrogate pairs lower bound: \uD800\uDC00
        assertSerializeValue('👍')
        assertSerializeValue('\u007F\u0080\u0081')
        assertSerializeValue('\u07FF\u0800\u0801')
        assertSerializeValue('\uD800\uDC00')
    }

    @Test
    void 'can serialize a dangling surrogate pair'() {
        assertSerializeValue('a\uD83C')
        assertSerializeValue('a\udf09')
        assertSerializeValue('\uD83Ca')
        assertSerializeValue('\udf09a')
        assertSerializeValue('\uD83C👍')
        assertSerializeValue('\udf09👍')
    }

    @Test
    void 'can serialize a CharBuffer'() {
        char[] storedBody = 'my string' as char[]
        CharBuffer cb = CharBuffer.wrap(storedBody, 0, storedBody.length)
        assertSerializeValue(cb)
    }

    @Test
    void 'can serialize a byte'() {
        assertSerializeValue(42 as byte)
        assertSerializeValue(-42 as byte)
    }

    @Test
    void 'can serialize a short'() {
        assertSerializeValue(42 as short)
        assertSerializeValue(-42 as short)
    }

    @Test
    void 'can serialize an int'() {
        assertSerializeValue(42)
        assertSerializeValue(-42)
    }

    @Test
    void 'can serialize a long'() {
        assertSerializeValue(-2305843009213693952L)
    }

    @Test
    void 'can serialize a big int as a long'() {
        assertSerializeValue(BigInteger.valueOf(42L))
    }

    @Test
    void 'can serialize a big int as a long with overflow'() {
        BigInteger bi = 18446744073709551623.toLong() // 2^64 + 7
        assertSerializeValue(bi)
    }

    @Test
    void 'can serialize a boolean'() {
        assertSerializeValue(true)
        assertSerializeValue(false)
    }

    @Test
    void 'can serialize a float'() {
        assertSerializeValue(8.5f)
    }

    @Test
    void 'can serialize a double'() {
        assertSerializeValue(8.5d)
    }

    @Test
    void 'can serialize a BigDecimal'() {
        assertSerializeValue(BigDecimal.valueOf(8.5d))
    }

    @Test
    void 'can serialize lists'() {
        def arr = [1, 2, 3]
        lease = serializer.serialize([my_key: arr], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize arrays'() {
        def arr = [1, 2, 3] as byte[]
        lease = serializer.serialize([my_key: arr], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize nested arrays'() {
        def arr = [[1], [2, 3], [4, 5, 6]] as ArrayList
        lease = serializer.serialize([my_key: arr], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize maps'() {
        def map = [(1): 'xx', 2: 'yy']
        lease = serializer.serialize([my_key: map], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize an empty map'() {
        lease = serializer.serialize([my_key: [:]], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize a map with a null key'() {
        lease = serializer.serialize([(null): [:]], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize nested maps'() {
        def map = [(1): [2: 'xx'], 3: [4: 'yy']]
        lease = serializer.serialize([my_key: map], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize an empty array'() {
        lease = serializer.serialize([my_key: []], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'can serialize iterables'() {
        def list = [1, 2]
        def iterable = [
            iterator: { -> list.iterator() }
        ] as Iterable
        lease = serializer.serialize([my_key: iterable], metrics)
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'unknown values are serialized as nulls'() {
        lease = serializer.serialize([my_key: new Object()], metrics)
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
    void 'does not accept null top-level object'() {
        shouldFail(NullPointerException) {
            serializer.serialize(null, metrics)
        }
        assertMetrics(0, 0, 0)
        // XXX: Hack to make this test work standalone
        ByteBufferSerializer.blankLease.close()
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
    @SuppressWarnings('JUnitTestMethodWithoutAssert')
    void 'double close'() {
        lease = ByteBufferSerializer.blankLease
        lease.close()
        lease.close()
    }

    @Test
    void 'wafContext basic usage'() {
        lease = ByteBufferSerializer.blankLease
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'first pwargs buffer with nothing written'() {
        lease = ByteBufferSerializer.blankLease
        shouldFail(IllegalStateException) { lease.firstPWArgsByteBuffer }
        assertMetrics(0, 0, 0)
    }

}
