/*
 * Unless explicitly stated otherwise all files in this repository are licensed
 * under the Apache-2.0 License.
 *
 * This product includes software developed at Datadog
 * (https://www.datadoghq.com/). Copyright 2021 Datadog, Inc.
 */

package io.sqreen.powerwaf.util;

import java.util.Iterator;

public class Joiner {
    private final String delimiter;
    public Joiner(String delimiter) {
        this.delimiter = delimiter;
    }

    public String join(Iterable<?> iter) {
        StringBuilder builder = new StringBuilder();
        Iterator<?> iterator = iter.iterator();
        if (iterator.hasNext()) {
            builder.append(iterator.next().toString());
        }
        while (iterator.hasNext()) {
            builder.append(this.delimiter);
            builder.append(iterator.next().toString());
        }
        return builder.toString();
    }

    public static Joiner on(String delimiter) {
        return new Joiner(delimiter);
    }

}
