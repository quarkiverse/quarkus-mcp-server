package io.quarkiverse.mcp.server;

/**
 * If an MCP client supports the {@value ClientCapability#ELICITATION} capability, then the server can request additional
 * information.
 *
 * @see ElicitationRequest
 */
public interface Elicitation {

    /**
     * @return {@code true} if the client supports the {@link ClientCapability#ELICITATION} capability, {@code false} otherwise
     * @see InitialRequest
     */
    boolean isSupported();

    /**
     * @return a new elicitation request builder
     */
    ElicitationRequest.Builder requestBuilder();

}
