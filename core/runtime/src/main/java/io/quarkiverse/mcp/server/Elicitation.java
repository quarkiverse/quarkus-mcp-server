package io.quarkiverse.mcp.server;

/**
 * If an MCP client supports the {@value ClientCapability#ELICITATION} capability, then the server can request additional
 * information.
 *
 * @see ElicitationRequest
 * @see UrlElicitationRequest
 */
public interface Elicitation extends MrtrRequest {

    /**
     * @return {@code true} if the client supports the {@link ClientCapability#ELICITATION} capability, {@code false} otherwise
     * @see InitialRequest
     * @deprecated use {@link #isFormModeSupported()} or {@link #isUrlModeSupported()} instead
     */
    @Deprecated(forRemoval = true)
    boolean isSupported();

    /**
     * For backwards compatibility, an empty elicitation capability object is equivalent to declaring support for form mode
     * only.
     *
     * @return {@code true} if the client supports the form mode of the {@link ClientCapability#ELICITATION} capability,
     *         {@code false} otherwise
     * @see InitialRequest
     */
    boolean isFormModeSupported();

    /**
     * @return {@code true} if the client supports the URL mode of the {@link ClientCapability#ELICITATION} capability,
     *         {@code false} otherwise
     * @see InitialRequest
     */
    boolean isUrlModeSupported();

    /**
     *
     * @return a new form mode elicitation request builder
     * @throws IllegalStateException if the client does not support the form mode of the
     *         {@link ClientCapability#ELICITATION} capability
     */
    ElicitationRequest.Builder requestBuilder();

    /**
     *
     * @return a new URL mode elicitation request builder
     * @throws IllegalStateException if the client does not support the URL mode of the
     *         {@link ClientCapability#ELICITATION} capability
     */
    UrlElicitationRequest.Builder urlRequestBuilder();

}
