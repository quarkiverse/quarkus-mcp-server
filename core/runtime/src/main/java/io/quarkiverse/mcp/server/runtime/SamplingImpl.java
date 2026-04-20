package io.quarkiverse.mcp.server.runtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ModelPreferences;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.SamplingRequest.Builder;
import io.quarkiverse.mcp.server.SamplingRequest.IncludeContext;
import io.quarkiverse.mcp.server.ToolChoice;
import io.quarkiverse.mcp.server.ToolManager;

class SamplingImpl implements Sampling {

    static SamplingImpl from(ArgumentProviders argProviders) {
        return new SamplingImpl(argProviders.connection(), argProviders.sender(),
                argProviders.responseHandlers(), argProviders.responseHandlers().getSamplingTimeout(argProviders.serverName()));
    }

    private final McpConnection connection;

    private final Sender sender;

    private final ResponseHandlers responseHandlers;

    private final Duration timeout;

    SamplingImpl(McpConnection connection, Sender sender, ResponseHandlers responseHandlers, Duration timeout) {
        this.connection = connection;
        this.sender = sender;
        this.responseHandlers = responseHandlers;
        this.timeout = timeout;
    }

    @Override
    public boolean isSupported() {
        return connection.initialRequest().supportsSampling();
    }

    @Override
    public boolean isToolCallingSupported() {
        return connection.initialRequest().supportsSamplingTools();
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
        private final List<ToolManager.ToolInfo> tools = new ArrayList<>();
        private ToolChoice toolChoice;
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
        public Builder addTool(ToolManager.ToolInfo tool) {
            tools.add(Objects.requireNonNull(tool));
            return this;
        }

        @Override
        public Builder setTools(List<? extends ToolManager.ToolInfo> tools) {
            this.tools.clear();
            this.tools.addAll(List.copyOf(tools));
            return this;
        }

        @Override
        public Builder setToolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
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
            if ((!tools.isEmpty() || toolChoice != null) && !isToolCallingSupported()) {
                throw new IllegalStateException("Client " + connection.initialRequest().implementation()
                        + " does not support sampling with tools");
            }
            return new SamplingRequestImpl(maxTokens, List.copyOf(messages), temperature, systemPrompt, includeContext,
                    modelPreferences, List.copyOf(tools), toolChoice, metadata, stopSequences,
                    sender, responseHandlers, timeout);
        }

    }

}
