package io.quarkiverse.mcp.server;

import jakarta.annotation.Priority;

import io.quarkiverse.mcp.server.ResourceContentsEncoder.ResourceContentsData;

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

    record ResourceContentsData<TYPE>(RequestUri uri, TYPE data) {
    }

}
