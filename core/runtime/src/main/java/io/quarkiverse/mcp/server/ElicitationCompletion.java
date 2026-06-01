package io.quarkiverse.mcp.server;

/**
 * Sends a {@code notifications/elicitation/complete} notification to the client that initiated a URL mode elicitation
 * request.
 * <p>
 * This interface can be injected as a CDI bean from any context (e.g., JAX-RS endpoints, event handlers) to notify the
 * client that an out-of-band interaction started by URL mode elicitation has completed.
 *
 * @see UrlElicitationRequest
 */
public interface ElicitationCompletion {

    /**
     * Sends a {@code notifications/elicitation/complete} notification to the client that initiated the URL mode elicitation
     * request with the given {@code elicitationId}.
     *
     * @param elicitationId the unique identifier of the elicitation
     * @throws IllegalArgumentException if no pending elicitation with the given ID is found
     */
    void send(String elicitationId);

}
