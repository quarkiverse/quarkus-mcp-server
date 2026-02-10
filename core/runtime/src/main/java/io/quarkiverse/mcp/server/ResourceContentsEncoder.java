package io.quarkiverse.mcp.server;

import jakarta.annotation.Priority;

import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.ResourceContentsEncoder.ResourceContentsData;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.ResourceTemplateManager.ResourceTemplateInfo;

/**
 * Encodes an object as {@link ResourceContents}.
 * <p>
 * Implementation classes must be CDI beans. Qualifiers are ignored. {@link jakarta.enterprise.context.Dependent} beans are
 * reused during encoding.
 * <p>
 * Encoders may define the priority with {@link Priority}. An encoder with higher priority takes precedence.
 *
 * @param <TYPE>
 * @see ResourceContents
 * @see Resource
 * @see ResourceTemplate
 */
public interface ResourceContentsEncoder<TYPE> extends Encoder<ResourceContentsData<TYPE>, ResourceContents> {

    /**
     * The {@link FeatureInfo} param represents either {@link ResourceInfo} or {@link ResourceTemplateInfo}.
     *
     * @param <TYPE>
     */
    record ResourceContentsData<TYPE>(RequestUri uri, TYPE data, FeatureInfo featureInfo) {

        public ResourceContentsData {
            if (uri == null) {
                throw new IllegalArgumentException("uri must not be null");
            }
            if (data == null) {
                throw new IllegalArgumentException("data must not be null");
            }
            if (featureInfo == null) {
                throw new IllegalArgumentException("featureInfo must not be null");
            }
        }

    }

}
