/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf

import org.junit.After
import org.junit.Before

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

class ByteBufferSerializerTestsBase extends WafTestBase {

    @Lazy
    ByteBufferSerializer serializer = new ByteBufferSerializer(limits)

    ByteBufferSerializer.ArenaLease lease

    WafMetrics metrics

    @Before
    void before() {
        metrics = new WafMetrics()
    }

    @After
    void after() {
        lease?.close()
        lease = null
    }

    protected static String p(String s) {
        s.stripIndent()[1..-1]
    }

    protected void assertMetrics(Long countStringTooLong, Long countListMapTooLarge, Long countObjectTooDeep) {
        assertThat(metrics.truncatedStringTooLongCount, is(countStringTooLong))
        assertThat(metrics.truncatedListMapTooLargeCount, is(countListMapTooLarge))
        assertThat(metrics.truncatedObjectTooDeepCount, is(countObjectTooDeep))
    }

    protected void assertSerializeValue(Object value) {
        lease = serializer.serialize([key: value], metrics)
        try {
            assertMetrics(0, 0, 0)
            lease.close()
        } finally {
            lease?.close()
            lease = null
        }
    }
}
