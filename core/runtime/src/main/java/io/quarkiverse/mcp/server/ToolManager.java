package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;

/**
 * Manager can be used to obtain metadata and register a new tool programmatically.
 */
public interface ToolManager extends FeatureManager<ToolInfo> {

    /**
     *
     * @param name
     * @return the tool with the given name, or {@code null}
     */
    ToolInfo getTool(String name);

    /**
     *
     * @param name The name must be unique
     * @return a new definition builder
     * @see ToolDefinition#register()
     */
    ToolDefinition newTool(String name);

    /**
     * Removes a tool previously added with {@link #newTool(String)}.
     *
     * @return the removed tool or {@code null} if no such tool existed
     */
    ToolInfo removeTool(String name);

    /**
     * Tool info.
     */
    interface ToolInfo extends FeatureManager.FeatureInfo {

        List<ToolArgument> arguments();

    }

    /**
     * {@link ToolInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface ToolDefinition extends FeatureDefinition<ToolInfo, ToolArguments, ToolResponse, ToolDefinition> {

        /**
         *
         * @param name
         * @param description
         * @param required
         * @param type
         * @return self
         */
        ToolDefinition addArgument(String name, String description, boolean required, java.lang.reflect.Type type);

    }

    record ToolArguments(Map<String, Object> args, McpConnection connection, McpLog log, RequestId requestId) {

    }

    record ToolArgument(String name, String description, boolean required, java.lang.reflect.Type type) {

    }
}