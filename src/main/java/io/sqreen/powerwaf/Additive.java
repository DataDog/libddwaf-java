package io.sqreen.powerwaf;

import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.TimeoutPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;

public final class Additive implements Closeable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PowerwafContext ctx;

    private final LinkedList<ByteBufferSerializer.ArenaLease> leases =
            new LinkedList<>();

    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private long ptr;     // KEEP THIS FIELD!
    private boolean online;

    Additive(PowerwafContext ctx) {
        this.logger.debug("Creating PowerWAF Additive for {}", ctx);
        this.ctx = ctx;
        this.ptr = initAdditive(ctx.handle, Powerwaf.ENABLE_BYTE_BUFFERS);
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
     * @throws AbstractPowerwafException    rethrow any RuntimeException from native code
     */
    public Powerwaf.ActionWithData run(Map<String, Object> parameters,
                                       Powerwaf.Limits limits) throws AbstractPowerwafException {
        try {
            if (Powerwaf.ENABLE_BYTE_BUFFERS) {
                ByteBufferSerializer serializer = new ByteBufferSerializer(limits);
                long before = System.nanoTime();
                ByteBufferSerializer.ArenaLease lease;
                try {
                    lease = serializer.serialize(parameters);
                } catch (Exception e) {
                    throw new RuntimeException("Exception encoding parameters", e);
                }
                // henceforth the lease mustn't leak
                synchronized (this) {
                    if (!online) {
                        lease.close();
                        throw new IllegalStateException("This Additive is no longer online");
                    }
                    leases.push(lease);
                    long elapsedNs = System.nanoTime() - before;
                    Powerwaf.Limits newLimits = limits.reduceBudget(elapsedNs / 1000);
                    if (newLimits.generalBudgetInUs == 0L) {
                        this.logger.debug(
                                "Budget exhausted after serialization; " +
                                        "not running on additive {}", this);
                        leases.pop().close(); // might as well close it now
                        throw new TimeoutPowerwafException();
                    }
                    return runAdditive(lease.getFirstPWArgsByteBuffer(), newLimits);
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
        Throwable clearException = null;
        synchronized (this) {
            if (!online) {
                throw new IllegalStateException("This Additive is no longer online");
            }
            online = false;

            try {
                clearAdditive();
                this.logger.debug("Closed Additive for rule context {}", this.ctx);
            } catch (Throwable t) {
                clearException = t;
            }

            ByteBufferSerializer.ArenaLease lease;
            while ((lease = this.leases.pollLast()) != null) {
                try {
                    lease.close();
                } catch (Throwable t) {
                    this.logger.warn("Error releasing lease", t);
                }
            }
        }

        // if we reach this point, we were originally online
        this.ctx.delReference();

        if (clearException != null) {
            if (clearException instanceof Error) {
                throw (Error) clearException;
            } else if (clearException instanceof RuntimeException) {
                throw (RuntimeException) clearException;
            } else {
                throw new UndeclaredThrowableException(clearException);
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
