package io.sqreen.powerwaf;

import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class Additive implements Closeable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private final long ptr;     // KEEP THIS FIELD!

    private final PowerwafContext ctx;

    private final Lock writeLock;
    private final Lock readLock;

    Additive(PowerwafContext ctx) {
        this.logger.debug("Creating PowerWAF Additive for {}", ctx);
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();

        this.ctx = ctx;
        this.ptr = initAdditive(ctx.handle);
    }

    // see pw_initAdditiveH
    static native long initAdditive(PowerwafHandle handle);

    /**
     * Push to PowerWAF existing params and returns execution result
     *
     * @param parameters                    data to push to PowerWAF
     * @param limits                        request limits
     * @throws IllegalArgumentException     if Additive or Limits is null
     * @throws RuntimeException             if Additive has already been cleared
     */
    private native Powerwaf.ActionWithData runAdditive(
            Map<String, Object> parameters, Powerwaf.Limits limits);

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
        this.readLock.lock();

        try {
            return runAdditive(parameters, limits);
        } catch (RuntimeException rte) {
            throw new UnclassifiedPowerwafException(
                    "Error running PowerWAF's Additive for rule context " + ctx +
                            ": " + rte.getMessage(), rte);
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void close() {
        // use lock to avoid clearing additive while they're still being run
        this.writeLock.lock();
        try {
            clearAdditive();
            this.logger.debug("Closed Additive for rule context {}", this.ctx);
        } finally {
            this.writeLock.unlock();
        }
        this.ctx.delReference();
    }

    @Override
    protected void finalize() {
        // last-resort! close() should be called instead
        this.writeLock.lock();
        try {
            if (this.ptr != 0) {
                this.logger.warn(
                        "Additive for rule context {} had not been properly cleared", this.ctx);
                close();
                this.ctx.delReference();
            }
        } finally {
            this.writeLock.unlock();
        }
    }
}
