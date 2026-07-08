package io.quarkiverse.mcp.server.runtime;

import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.Builder;
import io.quarkiverse.mcp.server.ElicitationRequest.PrimitiveSchema;
import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.UrlElicitationRequest;
import io.vertx.core.json.JsonObject;

class ElicitationImpl implements Elicitation {

    static ElicitationImpl from(ArgumentProviders argProviders) {
        ServerRequests sr = argProviders.serverRequests();
        String serverName = argProviders.serverName();
        JsonObject params = Messages.getParams(argProviders.rawMessage());
        return new ElicitationImpl(argProviders.connection(), argProviders.sender(), sr,
                sr.getElicitationTimeout(serverName),
                sr.getElicitationCompletionTimeout(serverName),
                argProviders.mcpTracing(),
                InputResponsesImpl.from(params),
                params != null ? params.getString("requestState") : null);
    }

    private final McpConnection connection;

    private final Sender sender;

    private final ServerRequests serverRequests;

    private final Duration defaultTimeout;

    private final Duration defaultCompletionTimeout;

    private final McpTracing mcpTracing;

    private final InputResponses inputResponses;

    private final String requestState;

    ElicitationImpl(McpConnection connection, Sender sender, ServerRequests serverRequests, Duration timeout,
            Duration completionTimeout, McpTracing mcpTracing, InputResponses inputResponses, String requestState) {
        this.connection = connection;
        this.sender = sender;
        this.serverRequests = serverRequests;
        this.defaultTimeout = timeout;
        this.defaultCompletionTimeout = completionTimeout;
        this.mcpTracing = mcpTracing;
        this.inputResponses = inputResponses;
        this.requestState = requestState;
    }

    @Deprecated(forRemoval = true)
    @Override
    public boolean isSupported() {
        return connection.initialRequest().supportsElicitation();
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
    public boolean isFormModeSupported() {
        return connection.initialRequest().supportsElicitationFormMode();
    }

    @Override
    public boolean isUrlModeSupported() {
        return connection.initialRequest().supportsElicitationUrlMode();
    }

    @Override
    public ElicitationRequest.Builder requestBuilder() {
        if (!connection.status().isClientInitialized()) {
            throw McpMessageHandler.clientNotInitialized(connection);
        }
        if (!isFormModeSupported()) {
            if (connection.initialRequest().protocolVersion().isStateless()) {
                throw new McpException(
                        "Client does not support the required `elicitation` capability",
                        JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY,
                        Map.of("requiredCapabilities", Map.of("elicitation", Map.of())));
            }
            throw new IllegalStateException(
                    "Client " + connection.initialRequest().implementation()
                            + " does not support the form mode of the `elicitation` capability");
        }
        return new ElicitationRequestBuilder();
    }

    @Override
    public UrlElicitationRequest.Builder urlRequestBuilder() {
        if (!connection.status().isClientInitialized()) {
            throw McpMessageHandler.clientNotInitialized(connection);
        }
        if (!isUrlModeSupported()) {
            if (connection.initialRequest().protocolVersion().isStateless()) {
                throw new McpException(
                        "Client does not support the required `elicitation` capability",
                        JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY,
                        Map.of("requiredCapabilities", Map.of("elicitation", Map.of())));
            }
            throw new IllegalStateException(
                    "Client " + connection.initialRequest().implementation()
                            + " does not support the URL mode of the `elicitation` capability");
        }
        return new UrlElicitationRequestBuilder();
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
            return new ElicitationRequestImpl(message, requestedSchema, sender, serverRequests, timeout,
                    ElicitationImpl.this.mcpTracing,
                    ElicitationImpl.this.connection.initialRequest().protocolVersion().isStateless());
        }

    }

    class UrlElicitationRequestBuilder implements UrlElicitationRequest.Builder {

        private String message;
        private String url;
        private final String elicitationId = UUID.randomUUID().toString();
        private Duration timeout = ElicitationImpl.this.defaultTimeout;
        private Duration completionTimeout = ElicitationImpl.this.defaultCompletionTimeout;

        @Override
        public UrlElicitationRequest.Builder setMessage(String message) {
            this.message = Objects.requireNonNull(message);
            return this;
        }

        @Override
        public UrlElicitationRequest.Builder setUrl(String url) {
            this.url = Objects.requireNonNull(url);
            return this;
        }

        @Override
        public UrlElicitationRequest.Builder setUrl(URL url) {
            this.url = Objects.requireNonNull(url).toString();
            return this;
        }

        @Override
        public UrlElicitationRequest.Builder setTimeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        @Override
        public UrlElicitationRequest.Builder setCompletionTimeout(Duration completionTimeout) {
            this.completionTimeout = Objects.requireNonNull(completionTimeout);
            return this;
        }

        @Override
        public UrlElicitationRequest build() {
            if (message == null) {
                throw new IllegalStateException("message must be set");
            }
            if (url == null) {
                throw new IllegalStateException("url must be set");
            }
            if (completionTimeout.compareTo(timeout) < 0) {
                throw new IllegalStateException("completionTimeout must not be less than timeout");
            }
            return new UrlElicitationRequestImpl(message, url, elicitationId, sender, serverRequests, timeout,
                    completionTimeout, ElicitationImpl.this.mcpTracing, ElicitationImpl.this.connection.id(),
                    ElicitationImpl.this.connection.initialRequest().protocolVersion().isStateless());
        }

    }

}
