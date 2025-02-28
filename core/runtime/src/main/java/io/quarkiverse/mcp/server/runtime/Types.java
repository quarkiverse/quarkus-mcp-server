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
}
