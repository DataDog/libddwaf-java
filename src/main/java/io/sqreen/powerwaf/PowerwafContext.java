package io.sqreen.powerwaf;

import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.TimeoutPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a PowerWAF rule, ensuring that no runs happen after the rule
 * is destroyed and that the rule is not destroyed during runs.
 */
public class PowerwafContext {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String uniqueName;
    final PowerwafHandle handle;

    private boolean online;

    private final Lock writeLock;
    private final Lock readLock;

    private final AtomicInteger refcount = new AtomicInteger(1);

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

        // online set to true must be after call to Powerwaf.addRules
        // finalizer still runs even if the constructor threw
        online = true;
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
            if (Powerwaf.ENABLE_BYTE_BUFFERS) {
                ByteBufferSerializer serializer = new ByteBufferSerializer(limits);
                long before = System.nanoTime();
                ByteBufferSerializer.ArenaLease lease;
                try {
                    lease = serializer.serialize(parameters);
                } catch (Exception e) {
                    throw new RuntimeException("Exception encoding parameters", e);
                }
                try {
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
                } finally {
                    lease.close();
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

    public Additive openAdditive() {
        addReference();
        try {
            return new Additive(this);
        } catch (RuntimeException | Error e) {
            delReference();
            throw e;
        }
    }

    private void checkIfOnline() {
        if (!this.online) {
            throw new IllegalStateException("This context is already offline");
        }
    }

    private void addReference() {
        // read lock to prevent concurrent destruction, which uses a write lock
        this.readLock.lock();
        try {
            checkIfOnline();
            this.refcount.incrementAndGet();
        } finally {
            this.readLock.unlock();
        }
    }

    public void delReference() {
        int curRefcount = this.refcount.get();
        if (curRefcount <= 1) {
            // possible destruction, unless a reference is added in the interim
            this.writeLock.lock();
            boolean success;
            try {
                checkIfOnline();
                success = this.refcount.compareAndSet(curRefcount, curRefcount - 1);
                if (success) {
                    this.online = false;
                    Powerwaf.clearRules(this.handle);
                    logger.debug("Deleted WAF context {}", this);
                }
            } finally {
                this.writeLock.unlock();
            }
            if (!success) {
                delReference(); // try again
            }
        } else {
            boolean success = this.refcount.compareAndSet(curRefcount, curRefcount - 1);
            if (!success) {
                delReference(); // try again
            }
        }
    }

    @Override
    protected void finalize() {
        // last-resort! delReference() should be called instead
        this.writeLock.lock();
        try {
            if (this.online) {
                this.logger.warn("Context {} had not been properly closed", this.uniqueName);
                try {
                    Powerwaf.clearRules(this.handle);
                } finally {
                    if (Powerwaf.EXIT_ON_LEAK) {
                        System.exit(2);
                    }
                }
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
