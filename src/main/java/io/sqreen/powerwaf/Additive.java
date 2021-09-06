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
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PowerwafContext ctx;

    private final ByteBufferSerializer.ArenaLease lease;

    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private long ptr;     // KEEP THIS FIELD!
    private boolean online;

    Additive(PowerwafContext ctx) {
        this.logger.debug("Creating PowerWAF Additive for {}", ctx);
        this.ctx = ctx;
        this.ptr = initAdditive(ctx.handle, Powerwaf.ENABLE_BYTE_BUFFERS);
        this.lease = ByteBufferSerializer.getBlankLease();
        online = true;
    }

    private static native long initAdditive(PowerwafHandle handle, boolean powerwafEnableByteBuffers);

    private native Powerwaf.ActionWithData runAdditive(
            Map<String, Object> parameters, Powerwaf.Limits limits) throws AbstractPowerwafException;

    private native Powerwaf.ActionWithData runAdditive(
            ByteBuffer firstPWArgsBuffer, Powerwaf.Limits limits) throws AbstractPowerwafException;

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
     * @param parameters                    data to push to PowerWAF
     * @param limits                        request execution limits
     * @return                              execution results
     * @throws AbstractPowerwafException    rethrow from native code, timeout or param serialization failure
     */
    public Powerwaf.ActionWithData run(Map<String, Object> parameters,
                                       Powerwaf.Limits limits) throws AbstractPowerwafException {
        try {
            if (Powerwaf.ENABLE_BYTE_BUFFERS) {
                long before = System.nanoTime();
                synchronized (this) {
                    if (!online) {
                        throw new IllegalStateException("This Additive is no longer online");
                    }
                    ByteBuffer bb;
                    try {
                        bb = this.lease.serializeMore(limits, parameters);
                    } catch (Exception e) {
                        // extra exception is here just to match what happens when bytebuffers are disabled
                        throw new UnclassifiedPowerwafException(
                                new RuntimeException("Exception encoding parameters", e));
                    }
                    long elapsedNs = System.nanoTime() - before;
                    Powerwaf.Limits newLimits = limits.reduceBudget(elapsedNs / 1000);
                    if (newLimits.generalBudgetInUs == 0L) {
                        this.logger.debug(
                                "Budget exhausted after serialization; " +
                                        "not running on additive {}", this);
                        throw new TimeoutPowerwafException();
                    }
                    return runAdditive(bb, newLimits);
                }
            } else {
                synchronized (this) {
                    checkOnline();
                    return runAdditive(parameters, limits);
                }
            }
        } catch (RuntimeException rte) {
            throw new UnclassifiedPowerwafException(
                    "Error running PowerWAF's Additive for rule context " + ctx +
                            ": " + rte.getMessage(), rte);
        }
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
                this.logger.debug("Closed Additive for rule context {}", this.ctx);
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
        this.ctx.delReference();

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

    @Override
    protected synchronized void finalize() {
        // last-resort! close() should be called instead
        if (online) {
            this.logger.warn(
                    "Additive for rule context {} had not been properly cleared", this.ctx);
            try {
                close();
            } finally {
                if (Powerwaf.EXIT_ON_LEAK) {
                    this.logger.error("Additive for rule context {} was not properly closed. " +
                            "Exiting with exit code 2", this.ctx);
                    System.exit(2);
                }
            }
        }
    }

    private void checkOnline() { // should be called while locked
        if (!online) {
            throw new IllegalStateException("This Additive is no longer online");
        }
    }
}
