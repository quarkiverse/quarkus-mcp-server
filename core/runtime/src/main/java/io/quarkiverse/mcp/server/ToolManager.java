package io.quarkiverse.mcp.server;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;

/**
 * This manager can be used to obtain metadata and register a new tool programmatically.
 */
public interface ToolManager extends FeatureManager<ToolInfo> {

    /**
     *
     * @param name
     * @param serverName
     * @return the tool with the given name bound to the given server, or {@code null}
     * @see McpServer
     */
    ToolInfo getTool(String name, String serverName);

    /**
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it searches across all servers and throws an exception if the name is ambiguous.
     *
     * @param name
     * @return the tool with the given name, or {@code null}
     * @throws IllegalStateException if multiple tools with the given name exist on different servers
     * @see #getTool(String, String)
     */
    ToolInfo getTool(String name);

    /**
     * The name must be unique within a server configuration. A tool with the same name can exist on different servers.
     *
     * @param name
     * @return a new definition builder
     * @see ToolDefinition#register()
     */
    ToolDefinition newTool(String name);

    /**
     * Removes a tool previously added with {@link #newTool(String)} from the given server configuration only.
     *
     * @param name
     * @param serverName
     * @return the removed tool or {@code null} if no such tool existed
     */
    ToolInfo removeTool(String name, String serverName);

    /**
     * Removes all tools previously added with {@link #newTool(String)} with the given name from all server configurations.
     * <p>
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it removes matching tools across all servers.
     *
     * @param name
     * @return one of the removed tools or {@code null} if no such tool existed
     * @see #removeTool(String, String)
     */
    ToolInfo removeTool(String name);

    /**
     * Sends a {@code notifications/tools/list_changed} notification to the connections that match the given filter.
     * <p>
     * Unlike {@link ToolDefinition#register()} and {@link #removeTool(String)}, which broadcast to all connections,
     * this method allows targeted notification delivery. This is useful when the effective tool list changes for specific
     * connections only, for example due to a {@link ToolFilter} state change.
     *
     * @param filter the predicate used to select connections to notify
     */
    void notifyListChanged(Predicate<McpConnection> filter);

    /**
     * Tool info.
     */
    interface ToolInfo extends FeatureManager.FeatureInfo {

        String title();

        List<ToolArgument> arguments();

        Optional<ToolAnnotations> annotations();

        Map<MetaKey, Object> metadata();

        /**
         * @return the task options if the tool is task-augmented, an empty {@link Optional} otherwise
         * @see Task
         */
        default Optional<TaskOptions> taskOptions() {
            return Optional.empty();
        }

    }

    /**
     * {@link ToolInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface ToolDefinition extends FeatureDefinition<ToolInfo, ToolArguments, ToolResponse, ToolDefinition>,
            TransportHintDefinition<ToolDefinition> {

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

        /**
         *
         * @param title
         * @return self
         */
        ToolDefinition setTitle(String title);

        /**
         * Generate the output schema for structured content from the given class.
         *
         * @param from
         * @return self
         */
        default ToolDefinition generateOutputSchema(Class<?> from) {
            return generateOutputSchema((Type) from);
        }

        /**
         * Generate the output schema for structured content from the given type.
         *
         * @param from
         * @return self
         */
        ToolDefinition generateOutputSchema(Type from);

        /**
         * @param schema
         * @return self
         */
        ToolDefinition setOutputSchema(Object schema);

        /**
         * If not set the input schema is generated automatically.
         *
         * @param schema
         * @return self
         */
        ToolDefinition setInputSchema(Object schema);

        /**
         * @param metadata
         * @return self
         */
        ToolDefinition setMetadata(Map<MetaKey, Object> metadata);

        /**
         * @param inputGuardrails
         * @return self
         */
        ToolDefinition setInputGuardrails(List<Class<? extends ToolInputGuardrail>> inputGuardrails);

        /**
         * @param outputGuardrails
         * @return self
         */
        ToolDefinition setOutputGuardrails(List<Class<? extends ToolOutputGuardrail>> outputGuardrails);

        /**
         * Makes the tool task-augmented, i.e. eligible for asynchronous execution as defined by the MCP Tasks extension.
         *
         * @param taskOptions the task options, or {@code null} to disable task-augmented execution
         * @return self
         * @see Task
         * @see TaskOptions
         */
        ToolDefinition setTaskOptions(TaskOptions taskOptions);

        /**
         * @return the tool info
         * @throws IllegalArgumentException if a tool with the given name already exists for the same server configuration
         */
        @Override
        ToolInfo register();

    }

    public interface ToolArguments extends RequestFeatureArguments {

        Map<String, Object> args();

        /**
         * @return the task context; {@link TaskContext#isTaskAugmented()} returns {@code false} if the tool is executed
         *         synchronously
         * @see ToolDefinition#setTaskOptions(TaskOptions)
         */
        TaskContext taskContext();

    }

    record ToolArgument(String name, String description, boolean required, java.lang.reflect.Type type, String defaultValue) {
    }

    /**
     * @see Tool#annotations()
     */
    record ToolAnnotations(String title, boolean readOnlyHint, boolean destructiveHint, boolean idempotentHint,
            boolean openWorldHint) {
    }

    /**
     * The options of a task-augmented tool; the programmatic counterpart of the {@link Task} annotation.
     *
     * @param required if {@code true}, the tool can only be executed as a task; see {@link Task#required()}
     * @param ttl the time-to-live of a task, or {@code null} to use {@code quarkus.mcp.server.tasks.default-ttl}; a zero or
     *        negative duration means unlimited
     * @param pollInterval the polling interval suggested to the client, or {@code null} to use
     *        {@code quarkus.mcp.server.tasks.default-poll-interval}; must be positive if set
     * @see Task
     */
    record TaskOptions(boolean required, Duration ttl, Duration pollInterval) {

        public TaskOptions {
            if (pollInterval != null && (pollInterval.isZero() || pollInterval.isNegative())) {
                throw new IllegalArgumentException("pollInterval must be positive");
            }
        }

        /**
         * @return the options of a task-augmented tool that falls back to synchronous execution and uses the configured
         *         defaults
         */
        public static TaskOptions defaults() {
            return new TaskOptions(false, null, null);
        }
    }
}