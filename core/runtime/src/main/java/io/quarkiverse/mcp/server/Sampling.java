package io.quarkiverse.mcp.server;

/**
 * If an MCP client supports the {@value ClientCapability#SAMPLING} capability, then the server can request LLM sampling from
 * language models.
 *
 * @see SamplingRequest
 * @see SamplingResponse
 */
public interface Sampling {

    /**
     * @return {@code true} if the client supports the {@value ClientCapability#SAMPLING} capability, {@code false} otherwise
     * @see InitialRequest
     */
    boolean isSupported();

    /**
     * @return a new sampling request builder
     * @throws IllegalStateException if the client does not support the {@value ClientCapability#SAMPLING} capability
     */
    SamplingRequest.Builder requestBuilder();

}
