package io.quarkiverse.mcp.server;

import jakarta.annotation.Priority;

/**
 * Encodes an object as {@link PromptResponse}.
 * <p>
 * If a propmpt response encoder exists and matches a specific return type then it always takes precedence over matching
 * {@link ContentEncoder}.
 * <p>
 * Implementation classes must be CDI beans. Qualifiers are ignored. {@link jakarta.enterprise.context.Dependent} beans are
 * reused during encoding.
 * <p>
 * Encoders may define the priority with {@link Priority}. An encoder with higher priority takes precedence.
 *
 * @param <TYPE>
 * @see PromptResponse
 * @see Prompt
 */
public interface PromptResponseEncoder<TYPE> extends Encoder<TYPE, PromptResponse> {

}
