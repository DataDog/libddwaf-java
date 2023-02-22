/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf;

import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.InvalidRuleSetException;
import io.sqreen.powerwaf.exception.TimeoutPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;
import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a PowerWAF rule, ensuring that no runs happen after the rule
 * is destroyed and that the rule is not destroyed during runs.
 */
public class PowerwafContext implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PowerwafContext.class);

    private final String uniqueName;

    // must be accessed with locking
    final PowerwafHandle handle;
    private boolean online;

    private final Lock writeLock;
    private final Lock readLock;

    private final RuleSetInfo ruleSetInfo;
    private final LeakDetection.PhantomRefWithName<Object> selfRef;

    PowerwafContext(String uniqueName, PowerwafConfig config, Map<String, Object> definition) throws AbstractPowerwafException {
        LOGGER.debug("Creating PowerWAF context {}", uniqueName);
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();

        this.uniqueName = uniqueName;

        if (!definition.containsKey("version")) {
            throw new UnclassifiedPowerwafException(
                    "Invalid definition. Expected key 'version' to exist");
        }

        if (!definition.containsKey("events") &&
                !definition.containsKey("rules")) {
            throw new UnclassifiedPowerwafException(
                    "Invalid definition. Expected keys 'events' or 'rules' to exist");
        }
        if (config == null) {
            config = PowerwafConfig.DEFAULT_CONFIG;
        }

        RuleSetInfo[] infoRef = new RuleSetInfo[1];
        try {
            this.handle = Powerwaf.addRules(definition, config, infoRef);
        } catch (IllegalArgumentException iae) {
            if (infoRef[0] != null) {
                throw new InvalidRuleSetException(infoRef[0], iae);
            }
            throw iae;
        }
        this.ruleSetInfo = infoRef[0];

        // online set to true must be after call to Powerwaf.addRules
        // finalizer still runs even if the constructor threw
        online = true;
        if (Powerwaf.EXIT_ON_LEAK) {
            this.selfRef = LeakDetection.registerCloseable(this);
        } else {
            this.selfRef = null;
        }
        LOGGER.debug("Successfully create PowerWAF context {}", uniqueName);
    }

    private PowerwafContext(String uniqueName, PowerwafHandle handle, RuleSetInfo ruleSetInfo) {
        this.uniqueName = uniqueName;
        this.handle = handle;
        this.ruleSetInfo = ruleSetInfo;
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
        this.online = true;
        if (Powerwaf.EXIT_ON_LEAK) {
            this.selfRef = LeakDetection.registerCloseable(this);
        } else {
            this.selfRef = null;
        }
        LOGGER.debug("Successfully create PowerWAF context {} (update)", uniqueName);
    }

    public String[] getUsedAddresses() {
        this.readLock.lock();
        try {
            checkIfOnline();
            return Powerwaf.getRequiredAddresses(this.handle);
        } finally {
            this.readLock.unlock();
        }
    }

    public Powerwaf.ResultWithData runRules(Map<String, Object> parameters,
                                            Powerwaf.Limits limits,
                                            PowerwafMetrics metrics) throws AbstractPowerwafException {
        this.readLock.lock();
        try {
            checkIfOnline();
            LOGGER.debug("Running rule for context {} with limits {}",
                    this, limits);

            Powerwaf.ResultWithData res;
            if (Powerwaf.ENABLE_BYTE_BUFFERS) {
                // serialization could be extracted out of the lock
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
                        LOGGER.debug(
                                "Budget exhausted after serialization; " +
                                        "not running rule of context {}",
                                this);
                        throw new TimeoutPowerwafException();
                    }
                    res = Powerwaf.runRules(
                            this.handle, lease.getFirstPWArgsByteBuffer(), limits, metrics);
                } finally {
                    lease.close();
                    if (metrics != null) {
                        long after = System.nanoTime();
                        long totalTimeNs = after - before;
                        synchronized (metrics) {
                            metrics.totalRunTimeNs += totalTimeNs;
                        }
                    }
                }
            } else {
                res = Powerwaf.runRules(this.handle, parameters, limits, metrics);
            }

            LOGGER.debug("Rule of context {} ran successfully with return {}", this, res);

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
        this.readLock.lock();
        try {
            return new Additive(this);
        } finally {
            this.readLock.unlock();
        }
    }

    public PowerwafContext update(String uniqueId,
                                  Map<String, Object> specification) throws AbstractPowerwafException {
        // lock to ensure visibility of state of powerwaf handle in this thread
        this.readLock.lock();
        try {
            // all updates need to be serialized, because powerwaf handles may share state
            RuleSetInfo[] ruleSetInfoRef = new RuleSetInfo[1];
            synchronized (PowerwafContext.class) {
                try {
                    PowerwafHandle newHandle = Powerwaf.update(this.handle, specification, ruleSetInfoRef);
                    return new PowerwafContext(uniqueId, newHandle, ruleSetInfoRef[0]);
                } catch (RuntimeException rte) {
                    if (ruleSetInfoRef[0] == null) {
                        throw new UnclassifiedPowerwafException(rte);
                    }
                    throw new InvalidRuleSetException(ruleSetInfoRef[0], rte);
                }
            }
        } finally {
            this.readLock.unlock();
        }
    }

    private void checkIfOnline() {
        if (!this.online) {
            throw new IllegalStateException("This context is already offline");
        }
    }
    public void close() {
        this.writeLock.lock();
        try {
            checkIfOnline();
            this.online = false;
            Powerwaf.clearRules(this.handle);
            LOGGER.debug("Deleted WAF context {}", this);
            if (this.selfRef != null) {
                LeakDetection.notifyClose(this.selfRef);
            }
        } finally {
            this.writeLock.unlock();
        }
    }

    public RuleSetInfo getRuleSetInfo() {
        return ruleSetInfo;
    }

    public PowerwafMetrics createMetrics() {
        // right now this doesn't depend on the ctx, but it might in the future
        return new PowerwafMetrics();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PowerwafContext{");
        sb.append(uniqueName);
        sb.append('}');
        return sb.toString();
    }
}
