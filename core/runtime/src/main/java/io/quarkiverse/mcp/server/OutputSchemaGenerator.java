package io.quarkiverse.mcp.server;

import java.lang.reflect.Type;

/**
 * Generates the output schema for a return type of a {@link Tool} method.
 * <p>
 * Implementation classes must be CDI beans. Qualifiers are ignored.
 * <p>
 * Implementations should override the {@link #generate(Type)} method. The {@link #generate(Class)} method is deprecated.
 *
 * @see Tool.OutputSchema#generator()
 */
public interface OutputSchemaGenerator {

    /**
     * @param from
     * @return the object representing the schema
     * @deprecated Implement {@link #generate(Type)} instead; {@link #generate(Type)} is always used internally
     *             and its default implementation delegates to this method for {@link Class} arguments
     */
    @Deprecated(forRemoval = true)
    default Object generate(Class<?> from) {
        return null;
    }

    /**
     * The return value is serialized with the {@code jackson-databind} library.
     * <p>
     * This method is always used internally. The default implementation delegates to {@link #generate(Class)} if the argument
     * is a {@link Class}, and throws {@link UnsupportedOperationException} otherwise.
     *
     * @param from
     * @return the object representing the schema
     */
    default Object generate(Type from) {
        if (from instanceof Class<?> clazz) {
            return generate(clazz);
        }
        throw new UnsupportedOperationException("Generic type schema generation is not supported by " + getClass().getName());
    }

}
