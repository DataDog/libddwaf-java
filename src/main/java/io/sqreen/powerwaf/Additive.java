/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.TimeoutPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.util.Map;

public final class Additive implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Additive.class);

    private final PowerwafContext ctx;
    private final ByteBufferSerializer.ArenaLease lease;
    private final LeakDetection.PhantomRefWithName<Object> selfRef;

    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private long ptr;     // KEEP THIS FIELD!
    private boolean online;

    Additive(PowerwafContext ctx) {
        LOGGER.debug("Creating PowerWAF Additive for {}", ctx);
        this.ctx = ctx;
        this.ptr = initAdditive(ctx.handle);
        this.lease = ByteBufferSerializer.getBlankLease();
        this.online = true;
        if (Powerwaf.EXIT_ON_LEAK) {
            this.selfRef = LeakDetection.registerCloseable(this);
        } else {
            this.selfRef = null;
        }
    }

    private static native long initAdditive(PowerwafHandle handle);

    private native Powerwaf.ResultWithData runAdditive(
            Map<String, Object> persistentData, Map<String, Object> ephemeralData, Powerwaf.Limits limits, PowerwafMetrics metrics) throws AbstractPowerwafException;

    private native Powerwaf.ResultWithData runAdditive(
            ByteBuffer persistentBuffer, ByteBuffer ephemeralBuffer, Powerwaf.Limits limits, PowerwafMetrics metrics) throws AbstractPowerwafException;

    /**
     * Clear given Additive (free PWAddContext in PowerWAF)
     *
     * @throws IllegalArgumentException     if Additive is null
     * @throws RuntimeException             if Additive has already been cleared (double free)
     */
    private native void clearAdditive();

    /**
     * Push params to PowerWAF with given limits
     *
     * @param persistentData                data to push to PowerWAF
     * @param ephemeralData                 data to push to PowerWAF
     * @param limits                        request execution limits
     * @param metrics                       a metrics collector, or null
     * @return                              execution results
     * @throws AbstractPowerwafException    rethrow from native code, timeout or param serialization failure
     */
    public Powerwaf.ResultWithData run(Map<String, Object> persistentData,
                                       Map<String, Object> ephemeralData,
                                       Powerwaf.Limits limits,
                                       PowerwafMetrics metrics) throws AbstractPowerwafException {
        if (limits == null) {
            throw new IllegalArgumentException("limits must be provided");
        }
        try {
            if (Powerwaf.ENABLE_BYTE_BUFFERS) {
                long before = System.nanoTime();
                synchronized (this) {
                    if (!online) {
                        throw new IllegalStateException("This Additive is no longer online");
                    }
                    ByteBuffer persistentBuffer = null;
                    ByteBuffer ephemeralBuffer = null;
                    ByteBufferSerializer.ArenaLease ephemeralLease = null;
                    Powerwaf.ResultWithData result;

                    try {
                        try {
                            if (persistentData != null) {
                                persistentBuffer = this.lease.serializeMore(limits, persistentData);
                            }
                            if (ephemeralData != null) {
                                ephemeralLease = ByteBufferSerializer.getBlankLease();
                                ephemeralBuffer = ephemeralLease.serializeMore(limits, ephemeralData);
                            }
                        } catch (Exception e) {
                            // extra exception is here just to match what happens when bytebuffers are disabled
                            throw new UnclassifiedPowerwafException(
                                    new RuntimeException("Exception encoding parameters", e));
                        }

                        long elapsedNs = System.nanoTime() - before;
                        Powerwaf.Limits newLimits = limits.reduceBudget(elapsedNs / 1000);
                        if (newLimits.generalBudgetInUs == 0L) {
                            LOGGER.debug(
                                    "Budget exhausted after serialization; " +
                                            "not running on additive {}", this);
                            throw new TimeoutPowerwafException();
                        }

                        result = runAdditive(persistentBuffer, ephemeralBuffer, newLimits, metrics);
                    } finally {
                        if (ephemeralLease != null) {
                            ephemeralLease.close();
                        }
                        if (metrics != null) {
                            long after = System.nanoTime();
                            long totalTimeNs = after - before;
                            synchronized (metrics) {
                                metrics.totalRunTimeNs += totalTimeNs;
                            }
                        }
                    }
                    return result;
                }
            } else {
                synchronized (this) {
                    checkOnline();
                    return runAdditive(persistentData, ephemeralData, limits, metrics);
                }
            }
        } catch (RuntimeException rte) {
            throw new UnclassifiedPowerwafException(
                    "Error running PowerWAF's Additive for rule context " + ctx +
                            ": " + rte.getMessage(), rte);
        }
    }

    public Powerwaf.ResultWithData run(Map<String, Object> parameters,
                                       Powerwaf.Limits limits,
                                       PowerwafMetrics metrics) throws AbstractPowerwafException {
        return run(parameters, null, limits, metrics);
    }

    public Powerwaf.ResultWithData runEphemeral(Map<String, Object> ephemeralData,
                                       Powerwaf.Limits limits,
                                       PowerwafMetrics metrics) throws AbstractPowerwafException {
        return run(null, ephemeralData, limits, metrics);
    }

    @Override
    public void close() {
        Throwable exc = null;
        synchronized (this) {
            if (!online) {
                throw new IllegalStateException("This Additive is no longer online");
            }
            online = false;

            try {
                clearAdditive();
                LOGGER.debug("Closed Additive for rule context {}", this.ctx);
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
            throw new IllegalStateException("This Additive is no longer online");
        }
    }
}
