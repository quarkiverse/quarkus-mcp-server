package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;

/**
 * This manager can be used to obtain metadata and register a new tool programmatically.
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

        Optional<ToolAnnotations> annotations();

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
        default ToolDefinition addArgument(String name, String description, boolean required, java.lang.reflect.Type type) {
            return addArgument(name, description, required, type, null);
        }

        /**
         *
         * @param name
         * @param description
         * @param required
         * @param type
         * @param defaultValue
         * @return self
         */
        ToolDefinition addArgument(String name, String description, boolean required, java.lang.reflect.Type type,
                String defaultValue);

        /**
         *
         * @param annotations
         * @return self
         */
        ToolDefinition setAnnotations(ToolAnnotations annotations);

    }

    // TODO: replace this record with an interface that extends a common ancestor
    record ToolArguments(Map<String, Object> args, McpConnection connection, McpLog log, RequestId requestId, Progress progress,
            Roots roots, Sampling sampling, Cancellation cancellation) {
    }

    record ToolArgument(String name, String description, boolean required, java.lang.reflect.Type type, String defaultValue) {
    }

    /**
     * @see Tool#annotations()
     */
    record ToolAnnotations(String title, boolean readOnlyHint, boolean destructiveHint, boolean idempotentHint,
            boolean openWorldHint) {
    }
}