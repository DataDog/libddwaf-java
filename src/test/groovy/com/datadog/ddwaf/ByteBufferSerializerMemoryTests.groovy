/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class ByteBufferSerializerMemoryTests extends ByteBufferSerializerTestsBase {

    @Test
    void 'string segments are reused'() {
        maxStringSize = ByteBufferSerializer.STRINGS_MIN_SEGMENTS_SIZE
        10.times {
            ByteBufferSerializer.ArenaLease lease = ByteBufferSerializer.blankLease
            lease.withCloseable {
                int initialSegments = lease.arena.stringsSegments.size()
                assertThat lease.arena.stringsSegments.size(), is(initialSegments)
            }
        }
        assertMetrics(0, 0, 0)
    }

    @Test
    void 'pwargs segments are reused'() {
        maxElements = Integer.MAX_VALUE
        10.times {
            ByteBufferSerializer.ArenaLease lease = ByteBufferSerializer.blankLease
            lease.withCloseable {
                int initialSegments = lease.arena.pwargsSegments.size()
                assertThat lease.arena.pwargsSegments.size(), is(initialSegments)
            }
        }
        assertMetrics(0, 0, 0)
    }

}
