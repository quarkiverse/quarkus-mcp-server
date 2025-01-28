package io.quarkiverse.mcp.server;

import jakarta.annotation.Priority;

/**
 * Encodes an object as {@link Content}.
 * <p>
 * Implementation classes must be CDI beans. Qualifiers are ignored. {@link jakarta.enterprise.context.Dependent} beans are
 * reused during encoding.
 * <p>
 * Encoders may define the priority with {@link Priority}. An encoder with higher priority takes precedence.
 *
 * @param <TYPE>
 * @see Content
 * @see Tool
 * @see Prompt
 */
public interface ContentEncoder<TYPE> extends Encoder<TYPE, Content> {

}
