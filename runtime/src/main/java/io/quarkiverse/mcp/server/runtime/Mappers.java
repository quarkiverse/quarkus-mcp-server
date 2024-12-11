package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.function.Function;

import io.smallrye.mutiny.Uni;

public class Mappers {

    public static final Function<Object, Uni<List<Object>>> SINGLE_MESSAGE = o -> {
        return Uni.createFrom().item(List.of(o));
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Uni<List<Object>>> LIST_MESSAGE = o -> {
        return Uni.createFrom().item((List<Object>) o);
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Uni<List<Object>>> UNI_SINGLE_MESSAGE = o -> {
        return ((Uni<Object>) o).map(p -> List.of(p));
    };

    @SuppressWarnings("unchecked")
    public static final Function<Object, Uni<List<Object>>> IDENTITY = o -> (Uni<List<Object>>) o;

}
