package io.quarkiverse.mcp.server;

import java.util.function.Predicate;

import io.quarkiverse.mcp.server.CompletionManager.CompletionInfo;

/**
 * This manager can be used to obtain metadata and register a new completion programmatically.
 */
public interface CompletionManager extends FeatureManager<CompletionInfo> {

    /**
     *
     * @param name
     * @param argumentName
     * @param serverName
     * @return the completion for the given name reference, argument name, and server
     * @see McpServer
     */
    CompletionInfo getCompletion(String name, String argumentName, String serverName);

    /**
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it searches across all servers and throws an exception if the name and argument combination is ambiguous.
     *
     * @param name
     * @param argumentName
     * @return the completion for the given name reference and argument name
     * @throws IllegalStateException if multiple completions with the given name and argument exist on different servers
     * @see #getCompletion(String, String, String)
     */
    CompletionInfo getCompletion(String name, String argumentName);

    /**
     * The combination of the name reference and argument name must be unique within a server configuration. A completion with
     * the same name reference and argument name can exist on different servers.
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

        /**
         * @return the completion info
         * @throws IllegalArgumentException if a completion for the given name reference and argument already exists for the
         *         same server configuration
         */
        @Override
        CompletionInfo register();

    }

    interface CompletionArguments extends RequestFeatureArguments {

        String argumentValue();

        CompleteContext completeContext();
    }
}
