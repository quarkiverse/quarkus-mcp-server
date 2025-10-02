package io.quarkiverse.mcp.server;

/**
 * Generates the output schema for a return type of a {@link Tool} method if no {@link Tool.OutputSchema#generator()} is
 * specified.
 * <p>
 * A default bean implementation is used if no other CDI bean of this type is configured.
 */
public interface GlobalOutputSchemaGenerator extends OutputSchemaGenerator {

}
