/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf;

import com.datadog.ddwaf.exception.AbstractWafException;
import com.datadog.ddwaf.exception.TimeoutWafException;
import com.datadog.ddwaf.exception.UnclassifiedWafException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Originally intended to be a {@code final} class to enforce immutability and usage constraints.
 * The {@code final} modifier was intentionally removed to improve testability—specifically to allow mocking
 * in unit tests
 *
 * This class should still be treated as final in spirit: it is not designed for extension ,
 * and should only be subclassed or mocked in test environments.
 */
public class WafContext implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WafContext.class);

    private final WafHandle ctx;
    private final ByteBufferSerializer.ArenaLease lease;
    private final LeakDetection.PhantomRefWithName<Object> selfRef;

    /**
     *  The ptr field holds the pointer to PWAddContext and managed by Waf
     */
    private long ptr;     // KEEP THIS FIELD!
    private boolean online;

    WafContext(WafHandle ctx) {
        LOGGER.debug("Creating Waf WafContext for {}", ctx);
        this.ctx = ctx;
        this.ptr = initWafContext(ctx.handle);
        this.lease = ByteBufferSerializer.getBlankLease();
        this.online = true;
        if (Waf.EXIT_ON_LEAK) {
            this.selfRef = LeakDetection.registerCloseable(this);
        } else {
            this.selfRef = null;
        }
    }

    private static native long initWafContext(NativeWafHandle handle);

    private native Waf.ResultWithData runWafContext(
            ByteBuffer persistentBuffer, ByteBuffer ephemeralBuffer, Waf.Limits limits, WafMetrics metrics) throws AbstractWafException;

    /**
     * Clear given WafContext (free PWAddContext in Waf)
     *
     * @throws IllegalArgumentException     if WafContext is null
     * @throws RuntimeException             if WafContext has already been cleared (double free)
     */
    private native void clearWafContext();

    /**
     * Push params to Waf with given limits
     *
     * @param persistentData                data to push to Waf
     * @param ephemeralData                 data to push to Waf
     * @param limits                        request execution limits
     * @param metrics                       a metrics collector, or null
     * @return                              execution results
     * @throws AbstractWafException    rethrow from native code, timeout or param serialization failure
     */
    public Waf.ResultWithData run(Map<String, Object> persistentData,
                                       Map<String, Object> ephemeralData,
                                       Waf.Limits limits,
                                       WafMetrics metrics) throws AbstractWafException {
        if (limits == null) {
            throw new IllegalArgumentException("limits must be provided");
        }
        try {
            long before = System.nanoTime();
            synchronized (this) {
                checkOnline();
                ByteBuffer persistentBuffer = null;
                ByteBuffer ephemeralBuffer = null;
                ByteBufferSerializer.ArenaLease ephemeralLease = null;
                Waf.ResultWithData result;

                try {
                    try {
                        if (persistentData != null) {
                            persistentBuffer = this.lease.serializeMore(limits, persistentData, metrics);
                        }
                        if (ephemeralData != null) {
                            ephemeralLease = ByteBufferSerializer.getBlankLease();
                            ephemeralBuffer = ephemeralLease.serializeMore(limits, ephemeralData, metrics);
                        }
                    } catch (Exception e) {
                        throw new UnclassifiedWafException(
                                new RuntimeException("Exception encoding parameters", e));
                    }

                    long elapsedNs = System.nanoTime() - before;
                    Waf.Limits newLimits = limits.reduceBudget(elapsedNs / 1000);
                    if (newLimits.generalBudgetInUs == 0L) {
                        LOGGER.debug("Budget exhausted after serialization; not running on wafContext {}", this);
                        throw new TimeoutWafException();
                    }

                    result = runWafContext(persistentBuffer, ephemeralBuffer, newLimits, metrics);
                } finally {
                    if (ephemeralLease != null) {
                        ephemeralLease.close();
                    }
                    if (metrics != null) {
                        long after = System.nanoTime();
                        long totalTimeNs = after - before;
                        metrics.addTotalRunTimeNs(totalTimeNs);
                    }
                }
                return result;
            }
        } catch (RuntimeException rte) {
            throw new UnclassifiedWafException(
                    "Error running Waf's WafContext for rule context " + ctx +
                            ": " + rte.getMessage(), rte);
        }
    }

    public Waf.ResultWithData run(Map<String, Object> parameters,
                                       Waf.Limits limits,
                                       WafMetrics metrics) throws AbstractWafException {
        return run(parameters, null, limits, metrics);
    }

    public Waf.ResultWithData runEphemeral(Map<String, Object> ephemeralData,
                                       Waf.Limits limits,
                                       WafMetrics metrics) throws AbstractWafException {
        return run(null, ephemeralData, limits, metrics);
    }

    @Override
    public void close() {
        Throwable exc = null;
        synchronized (this) {
            checkOnline();
            online = false;

            try {
                clearWafContext();
                LOGGER.debug("Closed WafContext for rule context {}", this.ctx);
            } catch (Throwable t) {
                exc = t;
            }

            try {
                this.lease.close();
            } catch (Throwable t) {
                exc = t;
            }
        }

        // if we reach this point, we were originally online
        if (this.selfRef != null) {
            LeakDetection.notifyClose(this.selfRef);
        }

        if (exc != null) {
            if (exc instanceof Error) {
                throw (Error) exc;
            } else if (exc instanceof RuntimeException) {
                throw (RuntimeException) exc;
            } else {
                throw new UndeclaredThrowableException(exc);
            }
        }
    }

    private void checkOnline() { // should be called while locked
        if (!online) {
            throw new IllegalStateException("This WafContext is no longer online");
        }
    }
}
