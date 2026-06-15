package io.quarkiverse.mcp.server;

/**
 * Shared interface for the Multi Round-Trip Requests (MRTR) pattern. Provides access to the {@link InputResponses},
 * {@linkplain #requestState() request state}, and a convenient {@link InputRequiredException} builder.
 * <p>
 * With stateless protocol versions, server-initiated requests are not supported. Instead, the server throws an
 * {@link InputRequiredException} from the feature method. The client retries the original request with the gathered input
 * and echoes back the {@linkplain #requestState() request state}.
 *
 * @see Elicitation
 * @see Sampling
 * @see Roots
 */
public interface MrtrRequest {

    /**
     * If the protocol version is stateless then the server-initiated request pattern is not supported and the server must use
     * the Multi Round-Trip Requests (MRTR) pattern instead, i.e. throw an {@link InputRequiredException} from the feature
     * method.
     *
     * @return {@code true} if the server-initiated request pattern is supported
     */
    boolean isServerInitiatedRequestSupported();

    /**
     * Returns the {@link InputResponses} object associated with the current request. The returned object is never {@code null}
     * but may be {@linkplain InputResponses#isEmpty() empty} on the initial request.
     *
     * @return the input responses, never {@code null}
     */
    InputResponses inputResponses();

    /**
     * Returns the opaque request state echoed back by the client from a previous {@link InputRequiredException}, or
     * {@code null} if not present.
     *
     * @return the request state, or {@code null}
     */
    String requestState();

    /**
     * Returns a new {@link InputRequiredException} builder for the Multi Round-Trip Requests (MRTR) pattern.
     *
     * @return a new builder
     * @throws IllegalStateException if the server-initiated request pattern is supported, i.e. the protocol is stateful
     */
    default InputRequiredException.Builder inputRequired() {
        if (isServerInitiatedRequestSupported()) {
            throw new IllegalStateException(
                    "Server-initiated requests are supported; use the server-initiated request API instead");
        }
        return InputRequiredException.builder();
    }

}
