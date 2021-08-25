package io.sqreen.powerwaf;

import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.TimeoutPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a PowerWAF rule, ensuring that no runs happen after the rule
 * is destroyed and that the rule is not destroyed during runs.
 */
public class PowerwafContext implements Closeable {
    private final static boolean POWERWAF_ENABLE_BYTE_BUFFERS;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String uniqueName;
    final PowerwafHandle handle;

    private boolean offline;

    private final Lock writeLock;
    private final Lock readLock;

    static {
        String bb = System.getProperty("POWERWAF_ENABLE_BYTE_BUFFERS", "true");
        POWERWAF_ENABLE_BYTE_BUFFERS = !bb.equalsIgnoreCase("false") && !bb.equals("0");
    }

    PowerwafContext(String uniqueName, Map<String, Object> definition) throws AbstractPowerwafException {
        this.logger.debug("Creating PowerWAF context {}", uniqueName);
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();

        this.uniqueName = uniqueName;

        if (!definition.containsKey("version") ||
                !definition.containsKey("events")) {
            throw new IllegalStateException(
                    "Invalid definition. Expected keys 'version' and 'events' to exist");
        }
        this.handle = Powerwaf.addRules(definition);

        this.logger.debug("Successfully create PowerWAF context {}", uniqueName);
    }

    public Powerwaf.ActionWithData runRules(Map<String, Object> parameters,
                                            Powerwaf.Limits limits) throws AbstractPowerwafException {
        this.readLock.lock();
        try {
            checkIfOnline();
            this.logger.debug("Running rule for context {} with limits {}",
                    this, limits);

            Powerwaf.ActionWithData res;
            if (POWERWAF_ENABLE_BYTE_BUFFERS) {
                ByteBufferSerializer serializer = new ByteBufferSerializer(limits);
                long before = System.nanoTime();
                try (ByteBufferSerializer.ArenaLease lease = serializer.serialize(parameters)) {
                    long elapsedNs = System.nanoTime() - before;
                    Powerwaf.Limits newLimits = limits.reduceBudget(elapsedNs / 1000);
                    if (newLimits.generalBudgetInUs == 0L) {
                        this.logger.debug(
                                "Budget exhausted after serialization; " +
                                        "not running rule of context {}",
                                this);
                        throw new TimeoutPowerwafException();
                    }
                    res = Powerwaf.runRules(this.handle, lease.getFirstPWArgsByteBuffer(), limits);
                }
            } else {
                res = Powerwaf.runRules(this.handle, parameters, limits);
            }

            this.logger.debug("Rule of context {} ran successfully with return {}", this, res);

            return res;
        } catch (RuntimeException rte) {
            throw new UnclassifiedPowerwafException(
                    "Error calling PowerWAF's runRule for rule in context " + this +
                    ": " + rte.getMessage(), rte);
        } finally {
            this.readLock.unlock();
        }
    }

    public Additive openAdditive() throws AbstractPowerwafException {
        return new Additive(this);
    }

    private void checkIfOnline() {
        if (this.offline) {
            throw new IllegalStateException("This context is already offline");
        }
    }

    public <T> T withReadLock(Callable<T> callable) {
        this.readLock.lock();
        try {
            checkIfOnline();
            return callable.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new UndeclaredThrowableException(e);
            }
        } finally {
            this.readLock.unlock();
        }
    }

    @Override
    public void close() {
        // use lock to avoid clearing rules while they're still being run
        this.writeLock.lock();
        try {
            checkIfOnline();
            Powerwaf.clearRules(this.handle);
            this.offline = true;
        } finally {
            this.writeLock.unlock();
        }
        this.logger.debug("Closed context {}", this.uniqueName);
    }

    @Override
    protected void finalize() {
        // last-resort! close() should be called instead
        this.writeLock.lock();
        try {
            if (!this.offline) {
                this.logger.warn("Context {} had not been properly closed", this.uniqueName);
                close();
            }
        } finally {
            this.writeLock.unlock();
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PowerwafContext{");
        sb.append(uniqueName);
        sb.append('}');
        return sb.toString();
    }
}
