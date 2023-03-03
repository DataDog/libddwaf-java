package io.sqreen.powerwaf;

import java.util.Map;

public interface MapIterableWithSize<T> extends Iterable<Map.Entry<T, Object>> {
    int size();
}
