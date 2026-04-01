package io.quarkiverse.mcp.server;

import java.util.Map;
import java.util.Optional;

import io.quarkiverse.mcp.server.ResourceTemplateManager.ResourceTemplateInfo;

/**
 * This manager can be used to obtain metadata and register a new resource template programmatically.
 */
public interface ResourceTemplateManager extends FeatureManager<ResourceTemplateInfo> {

    /**
     *
     * @param name
     * @param serverName
     * @return the resource template with the given name bound to the given server, or {@code null}
     * @see McpServer
     */
    ResourceTemplateInfo getResourceTemplate(String name, String serverName);

    /**
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it searches across all servers and throws an exception if the name is ambiguous.
     *
     * @param name
     * @return the resource template with the given name or {@code null}
     * @throws IllegalStateException if multiple resource templates with the given name exist on different servers
     * @see #getResourceTemplate(String, String)
     */
    ResourceTemplateInfo getResourceTemplate(String name);

    /**
     * The name must be unique within a server configuration. A resource template with the same name can exist on different
     * servers.
     *
     * @param name
     * @return a new definition builder
     * @see ResourceTemplateDefinition#register()
     */
    ResourceTemplateDefinition newResourceTemplate(String name);

    /**
     * Removes a resource template previously added with {@link #newResourceTemplate(String)} from the given server
     * configuration only.
     *
     * @param name
     * @param serverName
     * @return the removed resource template or {@code null} if no such resource template existed
     */
    ResourceTemplateInfo removeResourceTemplate(String name, String serverName);

    /**
     * Removes all resource templates previously added with {@link #newResourceTemplate(String)} with the given name from all
     * server configurations.
     * <p>
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it removes matching resource templates across all servers.
     *
     * @param name
     * @return one of the removed resource templates or {@code null} if no such resource template existed
     * @see #removeResourceTemplate(String, String)
     */
    ResourceTemplateInfo removeResourceTemplate(String name);

    /**
     * Resource template info.
     */
    interface ResourceTemplateInfo extends FeatureManager.FeatureInfo {

        String title();

        String uriTemplate();

        String mimeType();

        Map<MetaKey, Object> metadata();

        Optional<Content.Annotations> annotations();

    }

    /**
     * {@link ResourceTemplateInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface ResourceTemplateDefinition
            extends
            FeatureDefinition<ResourceTemplateInfo, ResourceTemplateArguments, ResourceResponse, ResourceTemplateDefinition>,
            TransportHintDefinition<ResourceTemplateDefinition> {

        /**
         * @param title
         * @return self
         * @see ResourceTemplate#title()
         */
        ResourceTemplateDefinition setTitle(String title);

        /**
         * @param uriTemplate
         * @return self
         * @see ResourceTemplate#uriTemplate()
         */
        ResourceTemplateDefinition setUriTemplate(String uriTemplate);

        /**
         * @param mimeType
         * @return self
         * @see ResourceTemplate#mimeType()
         */
        ResourceTemplateDefinition setMimeType(String mimeType);

        /**
         * @param annotations
         * @return self
         */
        ResourceTemplateDefinition setAnnotations(Content.Annotations annotations);

        /**
         * @param metadata
         * @return self
         */
        ResourceTemplateDefinition setMetadata(Map<MetaKey, Object> metadata);

        /**
         * @return the resource template info
         * @throws IllegalArgumentException if a resource template with the given name already exists for the same server
         *         configuration
         */
        @Override
        ResourceTemplateInfo register();

    }

    interface ResourceTemplateArguments extends RequestFeatureArguments {

        Map<String, String> args();

        RequestUri requestUri();
    }

}
