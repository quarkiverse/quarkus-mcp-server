package io.quarkiverse.mcp.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Indicates that a request cannot be processed until additional input is provided by the client.
 * <p>
 * When thrown from a server feature method (tool, prompt or resource), this exception is automatically converted to an
 * {@code InputRequiredResult} JSON-RPC result with {@code resultType: "input_required"}.
 * <p>
 * This is part of the Multi Round-Trip Requests (MRTR) pattern used with stateless protocol versions, where server-initiated
 * requests are not supported. Instead of calling {@link ElicitationRequest#send()} or {@link SamplingRequest#send()}, the
 * server returns an {@code InputRequiredResult} and the client retries the original request with the gathered input.
 *
 * @see MrtrRequest#isServerInitiatedRequestSupported()
 * @see MrtrRequest#inputRequired()
 */
public class InputRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Map<String, InputRequestEntry> inputRequests;
    private final String requestState;

    /**
     * @see MrtrRequest#inputRequired()
     */
    InputRequiredException(Map<String, InputRequestEntry> inputRequests, String requestState) {
        super("Additional input required");
        this.inputRequests = Map.copyOf(inputRequests);
        this.requestState = requestState;
    }

    /**
     * @return the map of input requests keyed by server-assigned identifiers
     */
    public Map<String, InputRequestEntry> inputRequests() {
        return inputRequests;
    }

    /**
     * @return the opaque request state, or {@code null}
     */
    public String requestState() {
        return requestState;
    }

    /**
     * @see MrtrRequest#inputRequired()
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * An input request entry included in the {@code InputRequiredResult}.
     */
    public sealed interface InputRequestEntry {
    }

    /**
     * An elicitation (form mode) input request entry.
     *
     * @param request the elicitation request
     */
    public record ElicitationInputRequest(ElicitationRequest request) implements InputRequestEntry {
        public ElicitationInputRequest {
            Objects.requireNonNull(request, "request must not be null");
        }
    }

    /**
     * An elicitation (URL mode) input request entry.
     *
     * @param request the URL elicitation request
     */
    public record UrlElicitationInputRequest(UrlElicitationRequest request) implements InputRequestEntry {
        public UrlElicitationInputRequest {
            Objects.requireNonNull(request, "request must not be null");
        }
    }

    /**
     * A sampling input request entry.
     *
     * @param request the sampling request
     */
    public record SamplingInputRequest(SamplingRequest request) implements InputRequestEntry {
        public SamplingInputRequest {
            Objects.requireNonNull(request, "request must not be null");
        }
    }

    /**
     * A roots/list input request entry.
     */
    public record RootsInputRequest() implements InputRequestEntry {
    }

    public static class Builder {

        private final Map<String, InputRequestEntry> inputRequests = new LinkedHashMap<>();
        private String requestState;

        Builder() {
        }

        /**
         * Adds an elicitation (form mode) input request.
         *
         * @param key the server-assigned identifier
         * @param request the elicitation request built via {@link Elicitation#requestBuilder()}
         * @return self
         */
        public Builder addElicitationRequest(String key, ElicitationRequest request) {
            inputRequests.put(Objects.requireNonNull(key), new ElicitationInputRequest(request));
            return this;
        }

        /**
         * Adds an elicitation (URL mode) input request.
         *
         * @param key the server-assigned identifier
         * @param request the URL elicitation request built via {@link Elicitation#urlRequestBuilder()}
         * @return self
         */
        public Builder addUrlElicitationRequest(String key, UrlElicitationRequest request) {
            inputRequests.put(Objects.requireNonNull(key), new UrlElicitationInputRequest(request));
            return this;
        }

        /**
         * Adds a sampling input request.
         *
         * @param key the server-assigned identifier
         * @param request the sampling request built via {@link Sampling#requestBuilder()}
         * @return self
         */
        public Builder addSamplingRequest(String key, SamplingRequest request) {
            inputRequests.put(Objects.requireNonNull(key), new SamplingInputRequest(request));
            return this;
        }

        /**
         * Adds a roots/list input request.
         *
         * @param key the server-assigned identifier
         * @return self
         */
        public Builder addRootsRequest(String key) {
            inputRequests.put(Objects.requireNonNull(key), new RootsInputRequest());
            return this;
        }

        /**
         * Sets the opaque request state. The client must echo this value back when retrying the request.
         *
         * @param requestState
         * @return self
         */
        public Builder setRequestState(String requestState) {
            this.requestState = Objects.requireNonNull(requestState);
            return this;
        }

        /**
         * @return a new exception
         * @throws IllegalStateException if neither inputRequests nor requestState is set
         */
        public InputRequiredException build() {
            if (inputRequests.isEmpty() && requestState == null) {
                throw new IllegalStateException("At least one of inputRequests or requestState must be set");
            }
            return new InputRequiredException(inputRequests, requestState);
        }
    }
}
