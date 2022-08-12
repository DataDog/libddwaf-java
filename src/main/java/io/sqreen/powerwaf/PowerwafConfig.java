/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2022 Datadog, Inc.
 */

package io.sqreen.powerwaf;

public class PowerwafConfig {
    private final static String DEFAULT_KEY_REGEX =
            "(?i)(p(ass)?w(or)?d|pass(_?phrase)?|secret|(api_?|private_?|" +
                    "public_?)key)|token|consumer_?(id|key|secret)|sign(ed|ature)|bearer|authorization";

    private final static String DEFAULT_VALUE_REGEX = "";

    public final static PowerwafConfig DEFAULT_CONFIG = new PowerwafConfig();

    public String obfuscatorKeyRegex = DEFAULT_KEY_REGEX;
    public String obfuscatorValueRegex = DEFAULT_VALUE_REGEX;
    public final boolean useByteBuffers = Powerwaf.ENABLE_BYTE_BUFFERS;
}
