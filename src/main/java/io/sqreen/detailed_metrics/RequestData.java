package io.sqreen.detailed_metrics;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RequestData {
    public String route;
    public String overtimeCallback;
    public final List<Measurement> measurements = new LinkedList<>();
    public final List<SlowCall> slowCalls = new LinkedList<>();

    public static class Measurement {
        public String callback;
        public float timing;

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Measurement{");
            sb.append("callback='").append(callback).append('\'');
            sb.append(", timing=").append(timing);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class SlowCall {
        public String callback;
        public float timing;
        public Object[] arguments;

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("SlowCall{");
            sb.append("callback='").append(callback).append('\'');
            sb.append(", timing=").append(timing);
            sb.append(", arguments=").append(Arrays.toString(arguments));
            sb.append('}');
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RequestData{");
        sb.append("route='").append(route).append('\'');
        sb.append(", overtimeCallback='").append(overtimeCallback).append('\'');
        sb.append(", measurements=").append(measurements);
        sb.append(", slowCalls=").append(slowCalls);
        sb.append('}');
        return sb.toString();
    }
}
