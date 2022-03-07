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

/**
 * Wraps a ddwaf metrics collector (via ptr field) and provides functionality to
 * read the collector results (ddwaf_metrics). ddwaf_metrics is marshalled into
 * a bytebuffer in order to avoid excessive JNI calls from native code and the
 * creation of many java objects.
 *
 * This class has only very limited thread safety guarantees. Calls to the
 * copyResults and close methods are serialized to avoid the object getting into
 * an invalid state, but the collector is not protected from concurrent
 * modifications, including modifications while copyResults is running (if such
 * races occur, they should still not result in a crash for the current libddwaf
 * implementation, though this shouldn't be relied on). If close is called
 * during a ddwaf run, however, a crash can occur, because memory that's been
 * freed can be written to.
 *
 * Consequently: ddwaf runs with the same metrics collector, and calls to
 * {@link #copyResults()} (via {@link #iterator()}) and to {@link #close()} for
 * the same metrics collector will need to be serialized.
 */
public class PowerwafMetrics implements Iterable<PowerwafMetrics.RuleExecDuration>, Closeable {
    // used to ensure that we don't use metrics collectors created for a certain
    // context (set of rules) in another context
    final PowerwafHandle handle;

    private long totalDdwafRunTimeNs;

    // volatile just to ensure that other threads see a valid object
    // after the constructor runs
    private volatile long ptr; // ddwaf_metrics_collector

    private final LeakDetection.PhantomRefWithName<Object> selfRef;

    /**
     * Encoded in the following way (all in native endianness):
     * 1. int: number of rules
     * 2. int: length of rule id (in UTF-16 code points)
     * 3. char[]: rule name in UTF-16
     * 4. long: total time of rule
     * repeat 2-4 for the number of times specified by int 1
     *
     * There is no alignment except for the rules names
     * (because we always write elements with an even number of bytes).
     */
    private ByteBuffer copiedResults;

    PowerwafMetrics(PowerwafHandle handle) {
        this.handle = handle;
        init(handle);
        if (Powerwaf.EXIT_ON_LEAK) {
            this.selfRef = LeakDetection.registerCloseable(this);
        } else {
            this.selfRef = null;
        }
    }

    private native void init(PowerwafHandle handle);

    private native void copyResults();

    @Override
    public void close() {
        doClose();
        if (this.selfRef != null) {
            LeakDetection.notifyClose(this.selfRef);
        }
    }

    private native void doClose();

    @Override
    public RuleExecDurationIterator iterator() {
        copyResults();
        return new RuleExecDurationIterator();
    }

    public static class RuleExecDuration {
        public CharSequence rule;
        public long timeInNs;
    }

    public class RuleExecDurationIterator implements Iterator<RuleExecDuration> {
        final RuleExecDuration duration = new RuleExecDuration();
        final int numRules;
        int cur;

        private RuleExecDurationIterator() {
            copiedResults.order(ByteOrder.nativeOrder());
            numRules = copiedResults.getInt();
        }

        public long getTotalDdwafRunTimeNs() {
            return totalDdwafRunTimeNs;
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
            cur++;

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
