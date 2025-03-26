/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package com.datadog.ddwaf;

import com.datadog.ddwaf.exception.AbstractWafException;
import com.datadog.ddwaf.exception.InvalidRuleSetException;
import com.datadog.ddwaf.exception.TimeoutWafException;
import com.datadog.ddwaf.exception.UnclassifiedWafException;
import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Waf rule, ensuring that no runs happen after the rule
 * is destroyed and that the rule is not destroyed during runs.
 */
public class WafHandle implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WafContext.class);

    private final String uniqueName;

    // must be accessed with locking
    final NativeWafHandle handle;
    private boolean online;

    private final Lock writeLock;
    private final Lock readLock;

    private final RuleSetInfo ruleSetInfo;
    private final LeakDetection.PhantomRefWithName<Object> selfRef;

    WafHandle(String uniqueName, WafConfig config, Map<String, Object> definition) throws AbstractWafException {
        LOGGER.debug("Creating Waf context {}", uniqueName);
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();

        this.uniqueName = uniqueName;

        if (!definition.containsKey("version")) {
            throw new UnclassifiedWafException(
                    "Invalid definition. Expected key 'version' to exist");
        }

        if (!definition.containsKey("events") &&
                !definition.containsKey("rules")) {
            throw new UnclassifiedWafException(
                    "Invalid definition. Expected keys 'events' or 'rules' to exist");
        }
        if (config == null) {
            config = WafConfig.DEFAULT_CONFIG;
        }

        RuleSetInfo[] infoRef = new RuleSetInfo[1];
        try {
            this.handle = Waf.addRules(definition, config, infoRef);
        } catch (IllegalArgumentException iae) {
            if (infoRef[0] != null) {
                throw new InvalidRuleSetException(infoRef[0], iae);
            }
            throw iae;
        }
        this.ruleSetInfo = infoRef[0];

        // online set to true must be after call to Waf.addRules
        // finalizer still runs even if the constructor threw
        online = true;
        if (Waf.EXIT_ON_LEAK) {
            this.selfRef = LeakDetection.registerCloseable(this);
        } else {
            this.selfRef = null;
        }
        LOGGER.debug("Successfully create Waf context {}", uniqueName);
    }

    private WafHandle(String uniqueName, NativeWafHandle handle, RuleSetInfo ruleSetInfo) {
        this.uniqueName = uniqueName;
        this.handle = handle;
        this.ruleSetInfo = ruleSetInfo;
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
        this.online = true;
        if (Waf.EXIT_ON_LEAK) {
            this.selfRef = LeakDetection.registerCloseable(this);
        } else {
            this.selfRef = null;
        }
        LOGGER.debug("Successfully create Waf context {} (update)", uniqueName);
    }

    public String[] getUsedAddresses() {
        this.readLock.lock();
        try {
            checkIfOnline();
            return Waf.getKnownAddresses(this.handle);
        } finally {
            this.readLock.unlock();
        }
    }

    public String[] getUsedActions() {
        this.readLock.lock();
        try {
            checkIfOnline();
            return Waf.getKnownActions(this.handle);
        } finally {
            this.readLock.unlock();
        }
    }

    public Waf.ResultWithData runRules(Map<String, Object> parameters,
                                            Waf.Limits limits,
                                            WafMetrics metrics) throws AbstractWafException {
        this.readLock.lock();
        try {
            checkIfOnline();
            LOGGER.debug("Running rule for context {} with limits {}",
                    this, limits);

            Waf.ResultWithData res;
            // serialization could be extracted out of the lock
            ByteBufferSerializer serializer = new ByteBufferSerializer(limits);
            long before = System.nanoTime();
            ByteBufferSerializer.ArenaLease lease;
            try {
                lease = serializer.serialize(parameters, metrics);
            } catch (Exception e) {
                throw new RuntimeException("Exception encoding parameters", e);
            }
            try {
                long elapsedNs = System.nanoTime() - before;
                Waf.Limits newLimits = limits.reduceBudget(elapsedNs / 1000);
                if (newLimits.generalBudgetInUs == 0L) {
                    LOGGER.debug(
                            "Budget exhausted after serialization; " +
                                    "not running rule of context {}",
                            this);
                    throw new TimeoutWafException();
                }
                res = Waf.runRules(
                        this.handle, lease.getFirstPWArgsByteBuffer(), newLimits, metrics);
            } finally {
                lease.close();
                if (metrics != null) {
                    long after = System.nanoTime();
                    long totalTimeNs = after - before;
                    metrics.addTotalRunTimeNs(totalTimeNs);
                }
            }

            LOGGER.debug("Rule of context {} ran successfully with return {}", this, res);

            return res;
        } catch (RuntimeException rte) {
            throw new UnclassifiedWafException(
                    "Error calling Waf's runRule for rule in context " + this +
                    ": " + rte.getMessage(), rte);
        } finally {
            this.readLock.unlock();
        }
    }

    public WafContext openContext() {
        this.readLock.lock();
        try {
            return new WafContext(this);
        } finally {
            this.readLock.unlock();
        }
    }

    public WafHandle update(String uniqueId,
                            Map<String, Object> specification) throws AbstractWafException {
        // lock to ensure visibility of state of Waf handle in this thread
        this.readLock.lock();
        try {
            // all updates need to be serialized, because Waf handles may share state
            RuleSetInfo[] ruleSetInfoRef = new RuleSetInfo[1];
            synchronized (WafHandle.class) {
                try {
                    NativeWafHandle newHandle = Waf.update(this.handle, specification, ruleSetInfoRef);
                    return new WafHandle(uniqueId, newHandle, ruleSetInfoRef[0]);
                } catch (RuntimeException rte) {
                    if (ruleSetInfoRef[0] == null) {
                        throw new UnclassifiedWafException(rte);
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
            Waf.clearRules(this.handle);
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

    public WafMetrics createMetrics() {
        // right now this doesn't depend on the ctx, but it might in the future
        return new WafMetrics();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("WafContext{");
        sb.append(uniqueName);
        sb.append('}');
        return sb.toString();
    }
}
