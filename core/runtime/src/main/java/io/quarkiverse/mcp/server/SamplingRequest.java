package io.quarkiverse.mcp.server;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;

/**
 * A request from the server to sample an LLM.
 */
public interface SamplingRequest {

    /**
     * @return the maximum number of tokens to sample
     */
    long maxTokens();

    /**
     *
     * @return the sampling messages (not {@code null})
     */
    List<SamplingMessage> messages();

    /**
     *
     * @return the stop sequences
     */
    List<String> stopSequences();

    /**
     * @return the system prompt the server wants to use for sampling
     */
    String systemPrompt();

    /**
     *
     * @return the temperature
     */
    BigDecimal temperature();

    /**
     *
     * @return the request to include the context
     */
    IncludeContext includeContext();

    /**
     * @return the model preferences
     */
    ModelPreferences modelPreferences();

    /**
     *
     * @return the optional metadata
     */
    Map<String, Object> metadata();

    /**
     * Send a message to the client.
     * <p>
     * If the client does not respond before the timeout expires then the returned {@code Uni} fails with
     * {@link TimeoutException}. The default timeout is configured with the {@code quarkus.mcp.server.sampling.default-timeout}
     * config property.
     *
     * @return a new {@link Uni} that completes with a {@code SamplingResponse}
     */
    Uni<SamplingResponse> send();

    /**
     * Send a message to the client and wait for the result.
     * <p>
     * Note that this method will block until the client sends the response.
     * <p>
     * If the client does not respond before the timeout expires then a {@link TimeoutException} is thrown. The default timeout
     * is configured with the {@code quarkus.mcp.server.sampling.default-timeout} config property.
     *
     * @return the response
     */
    default SamplingResponse sendAndAwait() {
        return send().await().indefinitely();
    }

    /**
     * A request to include context from one or more MCP servers.
     */
    enum IncludeContext {
        ALL_SERVERS,
        NONE,
        THIS_SERVER;

        @JsonValue
        public String getValue() {
            return switch (this) {
                case ALL_SERVERS -> "allServers";
                case NONE -> "none";
                case THIS_SERVER -> "thisServer";
            };
        }

    }

    /**
     * @see Sampling#requestBuilder()
     */
    interface Builder {

        /**
         *
         * @param message
         * @return self
         */
        Builder addMessage(SamplingMessage message);

        /**
         *
         * @param value
         * @return self
         */
        Builder setMaxTokens(long maxTokens);

        /**
         *
         * @param value
         * @return self
         */
        Builder setTemperature(BigDecimal temperature);

        /**
         *
         * @param systemPrompt
         * @return self
         */
        Builder setSystemPrompt(String systemPrompt);

        /**
         *
         * @param includeContext
         * @return self
         */
        Builder setIncludeContext(IncludeContext includeContext);

        /**
         *
         * @param modelPreferences
         * @return self
         */
        Builder setModelPreferences(ModelPreferences modelPreferences);

        /**
         *
         * @param metadata
         * @return self
         */
        Builder setMetadata(Map<String, Object> metadata);

        /**
         *
         * @param stopSequences
         * @return self
         */
        Builder setStopSequences(List<String> stopSequences);

        /**
         * If no timeout is set then the default value configured with the {@code quarkus.mcp.server.sampling.default-timeout}
         * is used.
         *
         * @param timeout
         * @return self
         */
        Builder setTimeout(Duration timeout);

        /**
         * @return a new request
         */
        SamplingRequest build();

    }

}
