package io.quarkiverse.mcp.server;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;

/**
 * This manager can be used to obtain metadata and register a new resource programmatically.
 */
public interface ResourceManager extends FeatureManager<ResourceInfo> {

    /**
     *
     * @param uri
     * @param serverName
     * @return the resource with the given URI bound to the given server, or {@code null}
     * @see McpServer
     */
    ResourceInfo getResource(String uri, String serverName);

    /**
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it searches across all servers and throws an exception if the URI is ambiguous.
     *
     * @param uri
     * @return the resource with the given URI or {@code null}
     * @throws IllegalStateException if multiple resources with the given URI exist on different servers
     * @see #getResource(String, String)
     */
    ResourceInfo getResource(String uri);

    /**
     * The name and URI must be unique within a server configuration. A resource with the same name can exist on different
     * servers.
     *
     * @param name
     * @return a new definition builder
     * @see ResourceDefinition#register()
     */
    ResourceDefinition newResource(String name);

    /**
     * Removes a resource previously added with {@link #newResource(String)} from the given server configuration only.
     *
     * @param uri
     * @param serverName
     * @return the removed resource or {@code null} if no such resource existed
     */
    ResourceInfo removeResource(String uri, String serverName);

    /**
     * Removes all resources previously added with {@link #newResource(String)} with the given URI from all server
     * configurations.
     * <p>
     * For backwards compatibility, this method does not default to the {@link McpServer#DEFAULT} server configuration.
     * Instead, it removes matching resources across all servers.
     *
     * @param uri
     * @return one of the removed resources or {@code null} if no such resource existed
     * @see #removeResource(String, String)
     */
    ResourceInfo removeResource(String uri);

    /**
     * Resource info.
     */
    interface ResourceInfo extends FeatureManager.FeatureInfo {

        String title();

        String uri();

        String mimeType();

        OptionalInt size();

        Optional<Content.Annotations> annotations();

        Map<MetaKey, Object> metadata();

        /**
         * Sends update notifications to all subscribers without waiting for the result.
         * <p>
         * The message can be sent asynchronously, e.g. if this method is invoked on an event loop.
         */
        void sendUpdateAndForget();

    }

    /**
     * {@link ResourceInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface ResourceDefinition
            extends FeatureDefinition<ResourceInfo, ResourceArguments, ResourceResponse, ResourceDefinition> {

        /**
         * @param title
         * @return self
         */
        ResourceDefinition setTitle(String title);

        /**
         * @param uri
         * @return self
         * @see Resource#uri()
         */
        ResourceDefinition setUri(String uri);

        /**
         * @param mimeType
         * @return self
         * @see Resource#mimeType()
         */
        ResourceDefinition setMimeType(String mimeType);

        /**
         * @param size
         * @return self
         * @see Resource#size()
         */
        ResourceDefinition setSize(int size);

        /**
         * @param annotations
         * @return self
         */
        ResourceDefinition setAnnotations(Content.Annotations annotations);

        /**
         * @param metadata
         * @return self
         */
        ResourceDefinition setMetadata(Map<MetaKey, Object> metadata);

        /**
         * @return the resource info
         * @throws IllegalArgumentException if a resource with the given name or URI already exists for the same server
         *         configuration
         */
        @Override
        ResourceInfo register();

    }

    interface ResourceArguments extends RequestFeatureArguments {

        RequestUri requestUri();

    }

}
