package io.quarkiverse.mcp.server;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Indicates that a request cannot be processed until one or more URL mode elicitations are completed.
 * <p>
 * When thrown from a server feature method, this exception is automatically converted to a JSON-RPC error with code
 * {@value JsonRpcErrorCodes#URL_ELICITATION_REQUIRED} ({@code -32042}).
 *
 * @see JsonRpcErrorCodes#URL_ELICITATION_REQUIRED
 */
public class UrlElicitationRequiredException extends McpException {

    private static final long serialVersionUID = 1L;

    private final List<ElicitationEntry> elicitations;

    UrlElicitationRequiredException(String message, List<ElicitationEntry> elicitations) {
        super(message, JsonRpcErrorCodes.URL_ELICITATION_REQUIRED);
        this.elicitations = List.copyOf(elicitations);
    }

    /**
     * @return the list of URL mode elicitations required before the request can be retried
     */
    public List<ElicitationEntry> elicitations() {
        return elicitations;
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A URL mode elicitation entry included in the error response.
     *
     * @param url the URL that the user should navigate to
     * @param elicitationId the unique identifier for this elicitation
     * @param message a human-readable message explaining why the interaction is needed
     */
    public record ElicitationEntry(String url, String elicitationId, String message) {

        public ElicitationEntry {
            Objects.requireNonNull(url, "url must not be null");
            Objects.requireNonNull(elicitationId, "elicitationId must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }
    }

    public static class Builder {

        private String message;
        private final List<ElicitationEntry> elicitations = new ArrayList<>();

        Builder() {
        }

        /**
         * Sets the top-level error message.
         *
         * @param message
         * @return self
         */
        public Builder setMessage(String message) {
            this.message = Objects.requireNonNull(message);
            return this;
        }

        /**
         * Adds a URL mode elicitation entry. The {@code elicitationId} is auto-generated.
         *
         * @param url the URL that the user should navigate to
         * @param message a human-readable message explaining why the interaction is needed
         * @return the generated elicitation ID
         */
        public String addElicitation(String url, String message) {
            String elicitationId = UUID.randomUUID().toString();
            elicitations.add(new ElicitationEntry(Objects.requireNonNull(url), elicitationId, Objects.requireNonNull(message)));
            return elicitationId;
        }

        /**
         * Adds a URL mode elicitation entry. The {@code elicitationId} is auto-generated.
         *
         * @param url the URL that the user should navigate to
         * @param message a human-readable message explaining why the interaction is needed
         * @return the generated elicitation ID
         */
        public String addElicitation(URL url, String message) {
            return addElicitation(Objects.requireNonNull(url).toString(), message);
        }

        /**
         * @return a new exception
         * @throws IllegalStateException if message or elicitations are not set
         */
        public UrlElicitationRequiredException build() {
            if (message == null) {
                throw new IllegalStateException("message must be set");
            }
            if (elicitations.isEmpty()) {
                throw new IllegalStateException("at least one elicitation must be added");
            }
            return new UrlElicitationRequiredException(message, elicitations);
        }
    }
}
