package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.Builder;
import io.quarkiverse.mcp.server.ElicitationRequest.PrimitiveSchema;
import io.quarkiverse.mcp.server.McpConnection;

class ElicitationImpl implements Elicitation {

    static ElicitationImpl from(ArgumentProviders argProviders) {
        return new ElicitationImpl(argProviders.connection(), argProviders.sender(),
                argProviders.responseHandlers(),
                argProviders.responseHandlers().getElicitationTimeout(argProviders.serverName()));
    }

    private final McpConnection connection;

    private final Sender sender;

    private final ResponseHandlers responseHandlers;

    private final Duration defaultTimeout;

    ElicitationImpl(McpConnection connection, Sender sender, ResponseHandlers responseHandlers, Duration timeout) {
        this.connection = connection;
        this.sender = sender;
        this.responseHandlers = responseHandlers;
        this.defaultTimeout = timeout;
    }

    @Override
    public boolean isSupported() {
        return connection.initialRequest().supportsElicitation();
    }

    @Override
    public ElicitationRequest.Builder requestBuilder() {
        if (!connection.status().isClientInitialized()) {
            throw McpMessageHandler.clientNotInitialized(connection);
        }
        if (!isSupported()) {
            throw new IllegalStateException(
                    "Client " + connection.initialRequest().implementation()
                            + " does not support the `elicitation` capability");
        }
        return new ElicitationRequestBuilder();
    }

    class ElicitationRequestBuilder implements ElicitationRequest.Builder {

        private String message;
        private final Map<String, PrimitiveSchema> requestedSchema = new HashMap<>();
        private Duration timeout = ElicitationImpl.this.defaultTimeout;

        @Override
        public Builder setMessage(String message) {
            this.message = Objects.requireNonNull(message);
            return this;
        }

        @Override
        public Builder addSchemaProperty(String key, PrimitiveSchema schema) {
            this.requestedSchema.put(Objects.requireNonNull(key), Objects.requireNonNull(schema));
            return this;
        }

        @Override
        public Builder setTimeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        @Override
        public ElicitationRequest build() {
            if (message == null) {
                throw new IllegalStateException("message must be set");
            }
            if (requestedSchema.isEmpty()) {
                throw new IllegalStateException("requestedSchema must be set");
            }
            return new ElicitationRequestImpl(message, requestedSchema, sender, responseHandlers, timeout);
        }

    }

}
