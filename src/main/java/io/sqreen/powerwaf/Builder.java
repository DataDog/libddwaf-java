/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2025 Datadog, Inc.
 */

package io.sqreen.powerwaf;


public final class Builder{
    /**
     *  The ptr field holds the pointer to PWAddContext and managed by PowerWAF
     */
    private long ptr;     // KEEP THIS FIELD!
    PowerwafConfig config;

    public Builder() {
        this.config = PowerwafConfig.DEFAULT_CONFIG;
        this.ptr = initBuilder(config);
    }

    public Builder(PowerwafConfig config) {
        this.config = config;
        this.ptr = initBuilder(config);
    }

    private static native long initBuilder(PowerwafConfig config);
}
