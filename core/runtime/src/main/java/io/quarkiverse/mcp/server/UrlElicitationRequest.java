package io.quarkiverse.mcp.server;

import java.net.URL;
import java.time.Duration;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;

/**
 * URL mode elicitation request from the server to direct the user to an external URL for out-of-band interactions.
 * <p>
 * URL mode is used for sensitive interactions that must not pass through the MCP client, such as providing API keys,
 * OAuth flows, or payment processing.
 *
 * @see Elicitation#urlRequestBuilder()
 */
public interface UrlElicitationRequest {

    /**
     * @return the message for the user
     */
    String message();

    /**
     * @return the URL that the user should navigate to
     */
    String url();

    /**
     * @return the unique identifier for this elicitation
     */
    String elicitationId();

    /**
     * Send a message to the client.
     * <p>
     * If the client does not respond before the timeout expires then the returned {@code Uni} fails with
     * {@link TimeoutException}. The default timeout is configured with the
     * {@code quarkus.mcp.server.elicitation.default-timeout}
     * config property.
     *
     * @return a new {@link Uni} that completes with a {@code ElicitationResponse}
     */
    @CheckReturnValue
    Uni<ElicitationResponse> send();

    /**
     * Send a message to the client and wait for the result.
     * <p>
     * Note that this method will block until the client sends the response.
     * <p>
     * If the client does not respond before the timeout expires then a {@link TimeoutException} is thrown. The default timeout
     * is configured with the {@code quarkus.mcp.server.elicitation.default-timeout} config property.
     *
     * @return the response
     */
    default ElicitationResponse sendAndAwait() {
        return send().await().indefinitely();
    }

    /**
     * @see Elicitation#urlRequestBuilder()
     */
    interface Builder {

        Builder setMessage(String message);

        /**
         * @param url the URL that the user should navigate to
         * @return self
         */
        Builder setUrl(String url);

        /**
         * @param url the URL that the user should navigate to
         * @return self
         */
        Builder setUrl(URL url);

        /**
         * If no timeout is set then the default value configured with the
         * {@code quarkus.mcp.server.elicitation.default-timeout} is used.
         *
         * @param timeout
         * @return self
         */
        Builder setTimeout(Duration timeout);

        /**
         * Sets the maximum time the server will keep the pending elicitation entry waiting for
         * {@link ElicitationCompletion#send(String)}.
         * <p>
         * If no completion timeout is set then the default value configured with the
         * {@code quarkus.mcp.server.elicitation.default-completion-timeout} is used.
         *
         * @param completionTimeout
         * @return self
         */
        Builder setCompletionTimeout(Duration completionTimeout);

        /**
         *
         * @return a new URL mode elicitation request
         */
        UrlElicitationRequest build();

    }
}
