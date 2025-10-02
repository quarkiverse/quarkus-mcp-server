package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;

/**
 * Generates the input schema for a {@link ToolInfo}.
 * <p>
 * Implementation classes must be CDI beans. Qualifiers are ignored.
 *
 * @see Tool.InputSchema#generator()
 */
public interface InputSchemaGenerator<R> {

    /**
     * The {@link InputSchema#value()} is serialized with the {@code jackson-databind} library.
     *
     * @param tool
     * @return the input schema (must not be {@code null})
     */
    R generate(ToolInfo tool);

}
