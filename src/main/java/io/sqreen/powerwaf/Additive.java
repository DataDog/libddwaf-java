package io.sqreen.powerwaf;

import io.sqreen.logging.Logger;
import io.sqreen.logging.LoggerFactory;
import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class Additive implements Closeable {
    private final Logger logger = LoggerFactory.get(getClass());

    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private final long ptr;     // KEEP THIS FIELD!

    private final String ruleName;

    private final Lock writeLock;
    private final Lock readLock;

    /**
     * Create new Additive in native code
     *
     * @param ruleName                      related rule name
     * @return                              additive object or null if rule with given name doesn't exist
     * @throws IllegalArgumentException     if ruleName is null
     */
    static native Additive initAdditive(String ruleName);

    /**
     * Push to PowerWAF existing params and returns execution result
     *
     * @param additive                      additive context to push data
     * @param parameters                    data to push to PowerWAF
     * @param limits                        request limits
     * @throws IllegalArgumentException     if Additive or Limits is null
     * @throws RuntimeException             if Additive has already been cleared
     */
    static native Powerwaf.ActionWithData runAdditive(
            Additive additive, Map<String, Object> parameters, Powerwaf.Limits limits);

    /**
     * Clear given Additive (free PWAddContext in PowerWAF)
     *
     * @param additive                      additive context to clear
     * @throws IllegalArgumentException     if Additive is null
     * @throws RuntimeException             if Additive has already been cleared (double free)
     */
    static native void clearAdditive(Additive additive);

    /**
     * This constructor called by PowerWAF only
     *
     * @param ptr           pointer to PWAddContext inside the PowerWAF
     * @param ruleName      related rule name
     */
    private Additive(long ptr, String ruleName) {
        this.logger.debug("Creating PowerWAF Additive for rule %s", ruleName);
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();

        this.ptr = ptr;
        this.ruleName = ruleName;
    }

    /**
     * Wrapper function to create Additive object
     * Will call Additive.<init> from native code to create Additive
     *
     * @param ruleName      related rule name
     * @return              additive object or null if rule with given name doesn't exist
     */
    static Additive createAdditive(String ruleName) throws AbstractPowerwafException {
        try {
            return initAdditive(ruleName);
        } catch (RuntimeException rte) {
            throw new UnclassifiedPowerwafException(
                    "Error creating PowerWAF's Additive for rule " + ruleName +
                            ": " + rte.getMessage(), rte);
        }
    }

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
            return runAdditive(this, parameters, limits);
        } catch (RuntimeException rte) {
            throw new UnclassifiedPowerwafException(
                    "Error run PowerWAF's Additive for rule " + ruleName +
                            ": " + rte.getMessage(), rte);
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void close() {
        // use lock to avoid clearing rules while they're still being run
        this.writeLock.lock();
        try {
            clearAdditive(this);
            this.logger.debug("Closed Additive for rule %s", this.ruleName);
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    protected void finalize() {
        // last-resort! close() should be called instead
        this.writeLock.lock();
        try {
            if (this.ptr != 0) {
                this.logger.warn(
                        "Additive for rule %s had not been properly cleared", this.ruleName);
                close();
            }
        } finally {
            this.writeLock.unlock();
        }
    }
}
