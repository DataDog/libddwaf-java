/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class PowerwafMetrics implements Iterable<PowerwafMetrics.RuleExecDuration>, Closeable {
    final PowerwafHandle handle;
    private long totalDdwafRunTimeMicros;
    private long ptr;

    /**
     * Encoded in the following way (all in native endianness):
     * 1. int: number of rules
     * 2. int: length of rule id (in UTF-16 code points)
     * 3. char[]: rule name in UTF-16
     * 4. long: total time of rule
     * repeat 2-4 for the number of times specified by int 1
     *
     * There is no alignment.
     */
    private ByteBuffer copiedResults;

    PowerwafMetrics(PowerwafHandle handle) {
        this.handle = handle;
        init(handle);
    }

    private native void init(PowerwafHandle handle);

    // uses MonitorEnter/Exit on this
    private native void copyResults();

    // uses MonitorEnter/Exit on this
    @Override
    public native void close();

    @Override
    public Iterator<RuleExecDuration> iterator() {
        copyResults();
        return new RuleExecDurationIterator();
    }

    public static class RuleExecDuration {
        private CharSequence rule;
        private long timeInNs;
    }

    private class RuleExecDurationIterator implements Iterator<RuleExecDuration> {
        final RuleExecDuration duration = new RuleExecDuration();
        final int numRules;
        int cur;

        private RuleExecDurationIterator() {
            copiedResults.order(ByteOrder.nativeOrder());
            numRules = copiedResults.getInt();
        }

        @Override
        public boolean hasNext() {
            return cur < numRules;
        }

        @Override
        public RuleExecDuration next() {
            if (cur >= numRules) {
                throw new NoSuchElementException();
            }

            int ruleIdLen = copiedResults.getInt();
            CharBuffer charBuffer = copiedResults.asCharBuffer();
            charBuffer.limit(ruleIdLen);
            copiedResults.position(copiedResults.position() + ruleIdLen * 2);
            duration.rule = charBuffer;
            duration.timeInNs = copiedResults.getLong();
            return duration;
        }
    }
}
