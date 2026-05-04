package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class Types {

    private Types() {
    }

    public static boolean isOptional(Type type) {
        if (type instanceof ParameterizedType pt) {
            return pt.getRawType().equals(Optional.class);
        }
        return OptionalInt.class.equals(type) || OptionalLong.class.equals(type) || OptionalDouble.class.equals(type);
    }

    public static boolean isGenericOptional(Type type) {
        return type instanceof ParameterizedType pt && pt.getRawType().equals(Optional.class);
    }

    public static Type getOptionalValueType(Type type) {
        if (type instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[0];
        }
        if (OptionalInt.class.equals(type)) {
            return int.class;
        }
        if (OptionalLong.class.equals(type)) {
            return long.class;
        }
        if (OptionalDouble.class.equals(type)) {
            return double.class;
        }
        throw new IllegalArgumentException("Not an optional type: " + type);
    }
}
