package io.quarkiverse.mcp.server;

import java.util.Map;

import io.quarkiverse.mcp.server.ResourceTemplateManager.ResourceTemplateInfo;

/**
 * This manager can be used to obtain metadata and register a new resource template programmatically.
 */
public interface ResourceTemplateManager extends FeatureManager<ResourceTemplateInfo> {

    /**
     *
     * @param name
     * @return the resource template with the given name or {@code null}
     */
    ResourceTemplateInfo getResourceTemplate(String name);

    /**
     *
     * @param name The name must be unique
     * @return a new definition builder
     * @see ResourceTemplateDefinition#register()
     */
    ResourceTemplateDefinition newResourceTemplate(String name);

    /**
     * Removes a resource template previously added with {@link #newResourceTemplate(String)}.
     *
     * @return the removed resource template or {@code null} if no such resource existed
     */
    ResourceTemplateInfo removeResourceTemplate(String name);

    /**
     * Resource info.
     */
    interface ResourceTemplateInfo extends FeatureManager.FeatureInfo {

        String uriTemplate();

        String mimeType();

    }

    /**
     * {@link ResourceTemplateInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface ResourceTemplateDefinition
            extends
            FeatureDefinition<ResourceTemplateInfo, ResourceTemplateArguments, ResourceResponse, ResourceTemplateDefinition> {

        /**
         *
         * @param uriTemplate
         * @return self
         * @see ResourceTemplate#uriTemplate()
         */
        ResourceTemplateDefinition setUriTemplate(String uriTemplate);

        /**
         *
         * @param mimeType
         * @return self
         * @see ResourceTemplate#mimeType()
         */
        ResourceTemplateDefinition setMimeType(String mimeType);

    }

    record ResourceTemplateArguments(Map<String, String> args, McpConnection connection, McpLog log, RequestId requestId,
            RequestUri requestUri, Progress progress, Roots roots, Sampling sampling) {

    }

}
