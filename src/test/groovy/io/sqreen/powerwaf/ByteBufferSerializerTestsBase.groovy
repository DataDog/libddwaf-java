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

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class ByteBufferSerializerTestsBase implements PowerwafTrait {

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
        lease = null

        // Check that all buffers were reset
        ByteBufferSerializer.ArenaPool.INSTANCE.arenas.each { arena ->
            arena.pwargsSegments.each { segment ->
                assertThat segment.buffer.position(), is(0)
            }
            arena.stringsSegments.each { segment ->
                assertThat segment.buffer.position(), is(0)
            }
        }
    }

    protected static String p(String s) {
        s.stripIndent()[1..-1]
    }

    protected void assertMetrics(Long countStringTooLong, Long countListMapTooLarge, Long countObjectTooDeep) {
        assertThat(metrics.truncatedStringTooLongCount, is(countStringTooLong))
        assertThat(metrics.truncatedListMapTooLargeCount, is(countListMapTooLarge))
        assertThat(metrics.truncatedObjectTooDeepCount, is(countObjectTooDeep))
    }

    protected void assertSerializeValue(Object value, String expected) {
        lease = serializer.serialize([key: value], metrics)
        try {
            String res = Powerwaf.pwArgsBufferToString(lease.firstPWArgsByteBuffer)
            def exp = p"""
            <MAP>
              key: $expected
            """
            assertThat res, is(exp)
            assertMetrics(0, 0, 0)
            lease.close()
        } finally {
            lease?.close()
            lease = null
        }
    }
}
