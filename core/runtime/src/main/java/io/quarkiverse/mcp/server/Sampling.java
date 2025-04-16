package io.quarkiverse.mcp.server;

/**
 * If an MCP client supports the {@code sampling} capability, then the server can request LLM sampling from language models.
 *
 * @see SamplingRequest
 * @see SamplingResponse
 */
public interface Sampling {

    /**
     * @return {@code true} if the client supports the {@code sampling} capability, {@code false} otherwise
     * @see InitialRequest
     */
    boolean isSupported();

    /**
     * @return a new sampling request builder
     */
    SamplingRequest.Builder requestBuilder();

}
