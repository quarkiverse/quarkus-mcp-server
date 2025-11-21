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
     * @return the resource template with the given name or {@code null}
     */
    ResourceTemplateInfo getResourceTemplate(String name);

    /**
     *
     * @param name The name must be unique
     * @return a new definition builder
     * @throws IllegalArgumentException if a resource template with the given name already exits
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
            FeatureDefinition<ResourceTemplateInfo, ResourceTemplateArguments, ResourceResponse, ResourceTemplateDefinition> {

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
         * @throws IllegalArgumentException if a resource template with the given name already exits
         * @return the resource template info
         */
        @Override
        ResourceTemplateInfo register();

    }

    interface ResourceTemplateArguments extends RequestFeatureArguments {

        Map<String, String> args();

        RequestUri requestUri();
    }

}
