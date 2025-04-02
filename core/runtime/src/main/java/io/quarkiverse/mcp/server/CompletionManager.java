package io.quarkiverse.mcp.server;

import java.util.function.Predicate;

import io.quarkiverse.mcp.server.CompletionManager.CompletionInfo;

/**
 * This manager can be used to obtain metadata and register a new completion programmatically.
 */
public interface CompletionManager extends FeatureManager<CompletionInfo> {

    /**
     * The combination of the name reference and argument name must be unique.
     *
     * @param nameReference
     * @return a new definition builder
     * @see CompletionDefinition#register()
     */
    CompletionDefinition newCompletion(String nameReference);

    /**
     * Removes all completions previously added with {@link #newCompletion(String)} and matching the given filter.
     *
     * @return {@code true} if any completions were removed
     */
    boolean removeCompletion(Predicate<CompletionInfo> filter);

    /**
     * Completion info.
     */
    interface CompletionInfo extends FeatureManager.FeatureInfo {

        /**
         * @return the name reference
         */
        @Override
        String name();

        /**
         * @return the name of the completed argument
         */
        String argumentName();

    }

    /**
     * {@link CompletionInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface CompletionDefinition
            extends FeatureDefinition<CompletionInfo, CompletionArguments, CompletionResponse, CompletionDefinition> {

        /**
         * Set the name of the completed argument.
         *
         * @return self
         */
        CompletionDefinition setArgumentName(String argumentName);

    }

    record CompletionArguments(String argumentValue, McpConnection connection, McpLog log, RequestId requestId) {

    }
}
