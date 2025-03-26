package com.datadog.ddwaf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LeakDetection {
    private static final Logger LOGGER = LoggerFactory.getLogger(LeakDetection.class);

    private static final ReferenceQueue<Object> QUEUE =
            new ReferenceQueue<>();

    private static final Set<PhantomRefWithName<Object>> UNCLOSED_REFERENCES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    static {
        if (Waf.EXIT_ON_LEAK) {
            ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
                Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setDaemon(true);
                return thread;
            });
            ex.execute(new LeakDetectionRunnable());
            ex.shutdown();
        }
    }

    public static class PhantomRefWithName<T> extends PhantomReference<T> {
        private final String toStringVal;

        public PhantomRefWithName(T referent, ReferenceQueue<? super T> q) {
            super(referent, q);
            this.toStringVal = referent.toString();
        }
    }

    public static PhantomRefWithName<Object> registerCloseable(Object closeable) {
        PhantomRefWithName<Object> ref = new PhantomRefWithName<>(closeable, QUEUE);
        synchronized (UNCLOSED_REFERENCES) {
            UNCLOSED_REFERENCES.add(ref);
        }
        return ref;
    }

    public static void notifyClose(PhantomRefWithName<Object> ref) {
        synchronized (UNCLOSED_REFERENCES) {
            UNCLOSED_REFERENCES.remove(ref);
        }
    }

    private static class LeakDetectionRunnable implements Runnable {
        @Override
        public void run() {
            try {
                Reference<?> ref;
                while ((ref = QUEUE.remove()) != null) {
                    boolean unclosed;
                    synchronized (UNCLOSED_REFERENCES) {
                        // in principle this is always true, because if the
                        // reference was removed from UNCLOSED_REFERENCES, it
                        // will likely not be reachable anymore and therefore
                        // no notification to the reference queue will occur
                        unclosed = UNCLOSED_REFERENCES.remove(ref);
                    }
                    ref.clear();
                    if (unclosed) {
                        panic(ref);
                    }
                }
            } catch (InterruptedException e) {}
        }

        private static void panic(Reference<?> ref) {
            String name = "(unknown)";
            if (ref instanceof PhantomRefWithName) {
                name = ((PhantomRefWithName<?>) ref).toStringVal;
            }
            LOGGER.error("Object {} was not properly closed. " +
                    "Exiting with exit code 2", name);
            System.exit(2);
        }
    }
}
