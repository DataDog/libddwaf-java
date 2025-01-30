package io.sqreen.powerwaf.metrics;

public enum InputTruncatedType {
    STRING_TOO_LONG(1),
    LIST_MAP_TOO_LARGE(2),
    OBJECT_TOO_DEEP(4);

    private final int value;

    InputTruncatedType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
