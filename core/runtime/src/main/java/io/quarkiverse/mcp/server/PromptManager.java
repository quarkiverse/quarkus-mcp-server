package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.PromptManager.PromptInfo;

/**
 * This manager can be used to obtain metadata and register a new prompt programmatically.
 */
public interface PromptManager extends FeatureManager<PromptInfo> {

    /**
     *
     * @param name
     * @param serverName
     * @return the prompt with the given name bound to the given server, or {@code null}
     * @see McpServer
     */
    PromptInfo getPrompt(String name, String serverName);

    /**
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it searches across all servers and throws an exception if the name is ambiguous.
     *
     * @param name
     * @return the prompt with the given name, or {@code null}
     * @throws IllegalStateException if multiple prompts with the given name exist on different servers
     * @see #getPrompt(String, String)
     */
    PromptInfo getPrompt(String name);

    /**
     * The name must be unique within a server configuration. A prompt with the same name can exist on different servers.
     *
     * @param name
     * @return a new definition builder
     * @see PromptDefinition#register()
     */
    PromptDefinition newPrompt(String name);

    /**
     * Removes a prompt previously added with {@link #newPrompt(String)} from the given server configuration only.
     *
     * @param name
     * @param serverName
     * @return the removed prompt or {@code null} if no such prompt existed
     */
    PromptInfo removePrompt(String name, String serverName);

    /**
     * Removes all prompts previously added with {@link #newPrompt(String)} with the given name from all server configurations.
     * <p>
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it removes matching prompts across all servers.
     *
     * @param name
     * @return one of the removed prompts or {@code null} if no such prompt existed
     * @see #removePrompt(String, String)
     */
    PromptInfo removePrompt(String name);

    /**
     * Prompt info.
     */
    interface PromptInfo extends FeatureManager.FeatureInfo {

        String title();

        List<PromptArgument> arguments();

        Map<MetaKey, Object> metadata();

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
         * @param title
         * @return self
         */
        PromptDefinition setTitle(String title);

        /**
         *
         * @param name
         * @param description
         * @param required
         * @return self
         */
        default PromptDefinition addArgument(String name, String description, boolean required) {
            return addArgument(name, description, required, null);
        }

        /**
         *
         * @param name
         * @param description
         * @param required
         * @param defaultValue
         * @return self
         */
        default PromptDefinition addArgument(String name, String description, boolean required, String defaultValue) {
            return addArgument(name, null, description, required, defaultValue);
        }

        /**
         *
         * @param name
         * @param title
         * @param description
         * @param required
         * @param defaultValue
         * @return self
         */
        PromptDefinition addArgument(String name, String title, String description, boolean required, String defaultValue);

        /**
         * @param metadata
         * @return self
         */
        PromptDefinition setMetadata(Map<MetaKey, Object> metadata);

        /**
         * @return the prompt info
         * @throws IllegalArgumentException if a prompt with the given name already exists for the same server configuration
         */
        @Override
        PromptInfo register();

    }

    record PromptArgument(String name, String title, String description, boolean required, String defaultValue) {

    }

    interface PromptArguments extends RequestFeatureArguments {

        Map<String, String> args();

    }
}
