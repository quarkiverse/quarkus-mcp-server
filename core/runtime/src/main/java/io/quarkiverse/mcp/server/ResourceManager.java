package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;

/**
 * Manager can be used to obtain metadata and register a new resource programmatically.
 */
public interface ResourceManager extends FeatureManager<ResourceInfo> {

    /**
     *
     * @param uri
     * @return the resource with the given uri or {@code null}
     */
    ResourceInfo getResource(String uri);

    /**
     *
     * @param uri The uri must be unique
     * @return a new definition builder
     * @see ResourceDefinition#register()
     */
    ResourceDefinition newResource(String uri);

    /**
     * Removes a resource previously added with {@link #newResource(String)}.
     *
     * @return the removed resource or {@code null} if no such resource existed
     */
    ResourceInfo removeResource(String uri);

    /**
     * Resource info.
     */
    interface ResourceInfo extends FeatureManager.FeatureInfo {

        String uri();

        String mimeType();

    }

    /**
     * {@link ResourceInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface ResourceDefinition
            extends FeatureDefinition<ResourceInfo, ResourceArguments, ResourceResponse, ResourceDefinition> {

        /**
         *
         * @param uri
         * @return self
         * @see Resource#uri()
         */
        ResourceDefinition setUri(String uri);

        /**
         *
         * @param mimeType
         * @return self
         * @see Resource#mimeType()
         */
        ResourceDefinition setMimeType(String mimeType);

    }

    record ResourceArguments(McpConnection connection, McpLog log, RequestId requestId, RequestUri requestUri) {

    }

}
