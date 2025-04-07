package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public final class Types {

    private Types() {
    }

    public static boolean isOptional(Type type) {
        if (type instanceof ParameterizedType pt) {
            return pt.getRawType().equals(Optional.class);
        }
        return false;
    }

    public static Type getFirstActualTypeArgument(Type type) {
        if (type instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[0];
        }
        throw new IllegalArgumentException("Not a parameterized type: " + type);
    }
}
