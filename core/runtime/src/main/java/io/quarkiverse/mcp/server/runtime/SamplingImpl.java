package io.quarkiverse.mcp.server.runtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ModelPreferences;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.SamplingRequest.Builder;
import io.quarkiverse.mcp.server.SamplingRequest.IncludeContext;
import io.vertx.core.json.JsonObject;

class SamplingImpl implements Sampling {

    static SamplingImpl from(ArgumentProviders argProviders) {
        JsonObject params = Messages.getParams(argProviders.rawMessage());
        return new SamplingImpl(argProviders.connection(), argProviders.sender(),
                argProviders.serverRequests(),
                argProviders.serverRequests().getSamplingTimeout(argProviders.serverName()),
                argProviders.mcpTracing(),
                InputResponsesImpl.from(params),
                params != null ? params.getString("requestState") : null);
    }

    private final McpConnection connection;

    private final Sender sender;

    private final ServerRequests serverRequests;

    private final Duration timeout;

    private final McpTracing mcpTracing;

    private final InputResponses inputResponses;

    private final String requestState;

    SamplingImpl(McpConnection connection, Sender sender, ServerRequests serverRequests, Duration timeout,
            McpTracing mcpTracing, InputResponses inputResponses, String requestState) {
        this.connection = connection;
        this.sender = sender;
        this.serverRequests = serverRequests;
        this.timeout = timeout;
        this.mcpTracing = mcpTracing;
        this.inputResponses = inputResponses;
        this.requestState = requestState;
    }

    @Override
    public boolean isSupported() {
        return connection.initialRequest().supportsSampling();
    }

    @Override
    public boolean isServerInitiatedRequestSupported() {
        return !connection.initialRequest().protocolVersion().isStateless();
    }

    @Override
    public InputResponses inputResponses() {
        return inputResponses;
    }

    @Override
    public String requestState() {
        return requestState;
    }

    @Override
    public Builder requestBuilder() {
        if (!connection.status().isClientInitialized()) {
            throw McpMessageHandler.clientNotInitialized(connection);
        }
        if (!isSupported()) {
            throw new IllegalStateException(
                    "Client " + connection.initialRequest().implementation() + " does not support the `sampling` capability");
        }
        return new SamplingRequestBuilder();
    }

    class SamplingRequestBuilder implements SamplingRequest.Builder {

        private Long maxTokens;
        private final List<SamplingMessage> messages = new ArrayList<>();
        private BigDecimal temperature;
        private String systemPrompt;
        private IncludeContext includeContext;
        private ModelPreferences modelPreferences;
        private Map<String, Object> metadata;
        private List<String> stopSequences;
        private Duration timeout = SamplingImpl.this.timeout;

        @Override
        public Builder addMessage(SamplingMessage message) {
            messages.add(message);
            return this;
        }

        @Override
        public Builder setMaxTokens(long maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        @Override
        public Builder setTemperature(BigDecimal temperature) {
            this.temperature = temperature;
            return this;
        }

        @Override
        public Builder setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        @Override
        public Builder setIncludeContext(IncludeContext includeContext) {
            this.includeContext = includeContext;
            return this;
        }

        @Override
        public Builder setModelPreferences(ModelPreferences modelPreferences) {
            this.modelPreferences = modelPreferences;
            return this;
        }

        @Override
        public Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        @Override
        public Builder setStopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        @Override
        public Builder setTimeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        @Override
        public SamplingRequest build() {
            if (maxTokens == null) {
                throw new IllegalStateException("maxTokens must be set");
            }
            return new SamplingRequestImpl(maxTokens, List.copyOf(messages), temperature, systemPrompt, includeContext,
                    modelPreferences, metadata, stopSequences,
                    sender, serverRequests, timeout, SamplingImpl.this.mcpTracing,
                    SamplingImpl.this.connection.initialRequest().protocolVersion().isStateless());
        }

    }

}
