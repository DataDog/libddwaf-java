/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf

import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.is

class ByteBufferSerializerMemoryTests extends ByteBufferSerializerTestsBase {

    @Test
    void 'string segments are reused'() {
        maxStringSize = ByteBufferSerializer.STRINGS_MIN_SEGMENTS_SIZE
        def size = ByteBufferSerializer.STRINGS_MIN_SEGMENTS_SIZE / 4
        def str = 'x' * size
        10.times {
            ByteBufferSerializer.ArenaLease lease = ByteBufferSerializer.blankLease
            lease.withCloseable {
                int initialSegments = lease.arena.stringsSegments.size()
                def buffer = lease.serializeMore(limits, [key: str], metrics)
                String res = Powerwaf.pwArgsBufferToString(buffer)
                assertThat res, containsString(str)
                assertThat lease.arena.stringsSegments.size(), is(initialSegments)
            }
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

    @Test
    void 'pwargs segments are reused'() {
        maxElements = Integer.MAX_VALUE
        def size = (int) (ByteBufferSerializer.PWARGS_MIN_SEGMENTS_SIZE / 3)
        def arr = ['x'] * size
        10.times {
            ByteBufferSerializer.ArenaLease lease = ByteBufferSerializer.blankLease
            lease.withCloseable {
                int initialSegments = lease.arena.pwargsSegments.size()
                def buffer = lease.serializeMore(limits, [key: arr], metrics)
                String res = Powerwaf.pwArgsBufferToString(buffer)
                assertThat res.count('\n'), is(size + 2)
                assertThat lease.arena.pwargsSegments.size(), is(initialSegments)
            }
        }
        assertMetrics(0, 0, 0)
    }

}
