package io.quarkiverse.mcp.server;

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
     * @return the resource with the given uri or {@code null}
     */
    ResourceInfo getResource(String uri);

    /**
     *
     * @param name The name must be unique
     * @return a new definition builder
     * @see ResourceDefinition#register()
     */
    ResourceDefinition newResource(String name);

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

        String title();

        String uri();

        String mimeType();

        OptionalInt size();

        Optional<Content.Annotations> annotations();

        /**
         * Sends update notifications to all subscribers without waiting for the result.
         * <p>
         * The message can be sent asynchronously, e.g. if this method is invoked on an event loop.
         */
        void sendUpdateAndForget();

        /**
         * @deprecated Use {@link #sendUpdateAndForget()} instead
         */
        @Deprecated(forRemoval = true, since = "1.1")
        default void sendUpdate() {
            sendUpdateAndForget();
        }

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

    }

    interface ResourceArguments extends RequestFeatureArguments {

        RequestUri requestUri();

    }

}
