package io.sqreen.powerwaf;

import com.google.common.collect.ImmutableSet;
import io.sqreen.logging.Logger;
import io.sqreen.logging.LoggerFactory;
import io.sqreen.powerwaf.exception.AbstractPowerwafException;
import io.sqreen.powerwaf.exception.UnclassifiedPowerwafException;

import java.io.Closeable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a collection of PowerWAF rules, allocated and deallocated together.
 */
public class PowerwafContext implements Closeable {
    private final Logger logger = LoggerFactory.get(getClass());

    private final String uniqueName;
    private final Set<String> ruleNames;

    private boolean offline;

    private final Lock writeLock;
    private final Lock readLock;

    PowerwafContext(String uniqueName, Map<String, String> rules) throws AbstractPowerwafException {
        this.logger.debug("Creating PowerWAF context %s", uniqueName);
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();

        ImmutableSet.Builder<String> ruleNamesBuilder = ImmutableSet.builder();
        this.uniqueName = uniqueName;
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            String fullRuleName = addRule(entry.getKey(), entry.getValue());
            ruleNamesBuilder.add(fullRuleName);
        }
        this.ruleNames = ruleNamesBuilder.build();

        this.logger.debug("Successfully create PowerWAF context %s", uniqueName);
    }

    private String addRule(String ruleName, String ruleDefinition) throws AbstractPowerwafException {
        String fullRuleName = getFullRuleName(ruleName);
        this.logger.debug("Adding rule %s", fullRuleName);
        boolean result = Powerwaf.addRule(fullRuleName, ruleDefinition);
        if (!result) {
            throw new UnclassifiedPowerwafException("Failed adding PowerWAF rule " + ruleName
                    + " for context named " + this.uniqueName);
        }
        return fullRuleName;
    }

    private String getFullRuleName(String ruleName) {
        return this.uniqueName + '.' + ruleName;
    }

    public Powerwaf.ActionWithData runRule(String ruleName,
                                           Map<String, Object> parameters,
                                           Powerwaf.Limits limits) throws AbstractPowerwafException {
        String fullRuleName = getFullRuleName(ruleName);
        this.readLock.lock();
        try {
            checkIfOnline();
            this.logger.debug("Running rule %s with limits %s",
                    fullRuleName, limits);

            Powerwaf.ActionWithData res;
            res = Powerwaf.runRule(fullRuleName, parameters, limits);

            this.logger.debug("Rule %s ran successfully with return %s", fullRuleName, res);

            return res;
        } catch (RuntimeException rte) {
            throw new UnclassifiedPowerwafException(
                    "Error calling PowerWAF's runRule for rule " + fullRuleName +
                    ": " + rte.getMessage(), rte);
        } finally {
            this.readLock.unlock();
        }
    }

    private void checkIfOnline() {
        if (this.offline) {
            throw new IllegalStateException("This context is already offline");
        }
    }


    @Override
    public void close() {
        // use lock to avoid clearing rules while they're still being run
        this.writeLock.lock();
        try {
            checkIfOnline();
            for (String rule : this.ruleNames) {
                Powerwaf.clearRule(rule);
            }
            this.offline = true;
        } finally {
            this.writeLock.unlock();
        }
        this.logger.debug("Closed context %s", this.uniqueName);
    }

    @Override
    protected void finalize() {
        // last-resort! close() should be called instead
        this.writeLock.lock();
        try {
            if (!this.offline) {
                this.logger.warn("Context %s had not been properly closed", this.uniqueName);
                close();
            }
        } finally {
            this.writeLock.unlock();
        }
    }
}
