package io.quarkiverse.mcp.server.runtime;

import java.util.Iterator;
import java.util.List;

record Page<INFO>(List<INFO> data, boolean isLast) implements Iterable<INFO> {

    static final Page<?> EMPTY = new Page<>(List.of(), true);

    @SuppressWarnings("unchecked")
    static <INFO> Page<INFO> empty() {
        return (Page<INFO>) EMPTY;
    }

    @Override
    public Iterator<INFO> iterator() {
        return data.iterator();
    }

    public INFO lastInfo() {
        return data().get(data().size() - 1);
    }

    public boolean hasNextCursor() {
        return !data().isEmpty() && !isLast();
    }

}