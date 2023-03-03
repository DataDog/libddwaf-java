package io.sqreen.powerwaf;

import java.nio.ByteBuffer;

public interface NativeStringAddressable extends CharSequence{
    ByteBuffer getNativeStringBuffer();
}
