package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.PromptManager.PromptInfo;

/**
 * Manager can be used to obtain metadata and register a new prompt programmatically.
 */
public interface PromptManager extends FeatureManager<PromptInfo> {

    /**
     *
     * @param name
     * @return the prompt with the given name, or {@code null}
     */
    PromptInfo getPrompt(String name);

    /**
     *
     * @param name The name must be unique
     * @return a new definition builder
     * @see PromptDefinition#register()
     */
    PromptDefinition newPrompt(String name);

    /**
     * Removes a prompt previously added with {@link #newPrompt(String)}.
     *
     * @return the removed prompt or {@code null} if no such tool existed
     */
    PromptInfo removePrompt(String name);

    /**
     * Tool info.
     */
    interface PromptInfo extends FeatureManager.FeatureInfo {

        List<PromptArgument> arguments();

    }

    /**
     * {@link PromptInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface PromptDefinition
            extends FeatureManager.FeatureDefinition<PromptInfo, PromptArguments, PromptResponse, PromptDefinition> {

        /**
         *
         * @param name
         * @param description
         * @param required
         * @return self
         */
        PromptDefinition addArgument(String name, String description, boolean required);

    }

    record PromptArguments(Map<String, String> args, McpConnection connection, McpLog log, RequestId requestId) {
    }

    record PromptArgument(String name, String description, boolean required) {

    }
}
