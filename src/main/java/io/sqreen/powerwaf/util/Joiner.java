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
