package io.sqreen.detailed_metrics;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;

import java.util.List;

public class RequestData {
    public String route;
    public String overtimeCallback;
    public final List<Measurement> measurements = Lists.newLinkedList();
    public final List<SlowCall> slowCalls = Lists.newLinkedList();

    public static class Measurement {
        public String callback;
        public float timing;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("callback", callback)
                    .add("timing", timing)
                    .toString();
        }
    }

    public static class SlowCall {
        public String callback;
        public float timing;
        public Object[] arguments;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("callback", callback)
                    .add("timing", timing)
                    .add("arguments", arguments)
                    .toString();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("route", route)
                .add("overtimeCallback", overtimeCallback)
                .add("measurements", measurements)
                .add("slowCalls", slowCalls)
                .toString();
    }
}
