package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.GlobalInputSchemaGenerator.InputSchema;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;

/**
 * Generates the input schema for a {@link ToolInfo} if no {@link Tool.InputSchema#generator()} is specified.
 * <p>
 * A default bean implementation is used if no other CDI bean of this type is configured.
 */
public interface GlobalInputSchemaGenerator extends InputSchemaGenerator<InputSchema> {

    /**
     * Wraps the object representing the schema.
     * <p>
     * The {@link #asJson()} method will come in handy if you need to transform the generated input schema.
     */
    interface InputSchema {

        /**
         * The return value is serialized with the {@code jackson-databind} library.
         *
         * @return the object representing the schema (must not be {@code null})
         */
        Object value();

        /**
         * @return the JSON string representation of the schema
         */
        String asJson();

    }

}
