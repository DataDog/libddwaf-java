package io.sqreen.detailed_metrics;

import com.google.common.collect.Lists;

import java.util.List;

public class RequestDataCollection {
    public final List<RequestData> requests = Lists.newLinkedList();

    public native byte[] serialize();
}
