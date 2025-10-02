package io.quarkiverse.mcp.server;

/**
 * Generates the output schema for a return type of a {@link Tool} method.
 * <p>
 * Implementation classes must be CDI beans. Qualifiers are ignored.
 *
 * @see Tool.OutputSchema#generator()
 */
public interface OutputSchemaGenerator {

    /**
     * The return value is serialized with the {@code jackson-databind} library.
     *
     * @param from
     * @return the object representing the schema
     */
    Object generate(Class<?> from);

}
