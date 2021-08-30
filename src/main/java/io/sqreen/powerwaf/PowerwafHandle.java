package io.sqreen.powerwaf;

public class PowerwafHandle {
    private final long nativeHandle;

    // called from JNI
    private PowerwafHandle(long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Cannot build null PowerwafHandles");
        }
        this.nativeHandle = handle;
    }
}
