package io.sqreen.detailed_metrics;

import java.util.LinkedList;
import java.util.List;

public class RequestDataCollection {
    public final List<RequestData> requests = new LinkedList<>();

    public native byte[] serialize();
}
