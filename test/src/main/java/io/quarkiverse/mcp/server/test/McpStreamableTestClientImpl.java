package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured.ConnectFailureResponse;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

class McpStreamableTestClientImpl extends McpTestClientBase<McpStreamableAssert, McpStreamableTestClient>
        implements McpStreamableTestClient {

    private static final Logger LOG = Logger.getLogger(McpStreamableTestClientImpl.class);

    private final URI mcpEndpoint;
    private final boolean openSubsidiarySse;
    private final Consumer<ConnectFailureResponse> connectFailureConsumer;

    private volatile McpStreamableClient client;
    private volatile String mcpSessionId;

    private McpStreamableTestClientImpl(BuilderImpl builder) {
        super(builder.name, builder.version, builder.protocolVersion, builder.clientCapabilities, builder.additionalHeaders,
                builder.autoPong, builder.authorization, builder.title, builder.description, builder.websiteUrl, builder.icons,
                builder.openTelemetry);
        this.mcpEndpoint = createEndpointUri(builder.baseUri, builder.mcpPath);
        this.client = new McpStreamableClient(mcpEndpoint);
        this.openSubsidiarySse = builder.openSubsidiarySse;
        this.connectFailureConsumer = builder.connectFailureConsumer;
    }

    @Override
    public URI mcpEndpoint() {
        return mcpEndpoint;
    }

    @Override
    public String mcpSessionId() {
        return mcpSessionId;
    }

    @Override
    public McpStreamableTestClient connect(Consumer<InitResult> assertFunction) {
        if (client == null) {
            client = new McpStreamableClient(mcpEndpoint);
        }
        if (protocolVersion.isStateless()) {
            return connectStateless(assertFunction);
        }
        return connectStateful(assertFunction);
    }

    private McpStreamableTestClient connectStateless(Consumer<InitResult> assertFunction) {
        // Stateless clients send server/discover instead of initialize
        JsonObject discoverMessage = newRequest(McpAssured.SERVER_DISCOVER);
        injectStatelessMeta(discoverMessage);
        MultiMap headers = additionalHeaders(discoverMessage);
        addAuthorizationHeader(headers, clientAuthorization);
        HttpResponse<String> response;
        try (TracingHandle ignored = startTracingSpan(discoverMessage, headers)) {
            response = client.sendSync(discoverMessage.encode(), headers);
        }
        assertEquals(200, response.statusCode(), "Invalid HTTP response status: " + response.statusCode());

        JsonObject discoverResponse = new JsonObject(response.body());
        client.state.addResponse(discoverResponse);
        JsonObject discoverResult = assertResultResponse(discoverMessage, discoverResponse);
        assertNotNull(discoverResult);

        JsonObject serverInfo = discoverResult.getJsonObject("serverInfo");
        JsonObject discoverCapabilities = discoverResult.getJsonObject("capabilities");
        List<ServerCapability> capabilities = new ArrayList<>();
        if (discoverCapabilities != null) {
            for (String capability : discoverCapabilities.fieldNames()) {
                capabilities.add(new ServerCapability(capability, discoverCapabilities.getJsonObject(capability).getMap()));
            }
        }
        Implementation implementation = Messages.decodeImplementation(serverInfo);
        InitResult r = new InitResult(null,
                implementation.name(),
                implementation.title(),
                implementation.version(),
                capabilities,
                discoverResult.getString("instructions"),
                implementation,
                discoverResult.getJsonObject("_meta"));
        if (assertFunction != null) {
            assertFunction.accept(r);
        }
        this.initResult = r;
        connected.set(true);
        return this;
    }

    private McpStreamableTestClient connectStateful(Consumer<InitResult> assertFunction) {
        JsonObject initMessage = newInitMessage();
        MultiMap initHeaders = additionalHeaders.apply(initMessage);
        addAuthorizationHeader(initHeaders, clientAuthorization);
        HttpResponse<String> response;
        try (TracingHandle ignored = startTracingSpan(initMessage, initHeaders)) {
            response = client.sendSync(initMessage.encode(), initHeaders);
        }
        if (connectFailureConsumer != null) {
            assertNotEquals(200, response.statusCode());
            connectFailureConsumer
                    .accept(new ConnectFailureResponse(response.statusCode(), response.headers().map()));
            return this;
        } else {
            assertEquals(200, response.statusCode(), "Invalid HTTP response status: " + response.statusCode());
        }

        mcpSessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null);
        if (mcpSessionId == null) {
            throw new IllegalStateException("Mcp-Session-Id header not found: " + response.headers());
        }
        LOG.debugf("Mcp-Session-Id received: %s", mcpSessionId);

        JsonObject initResponse = new JsonObject(response.body());
        client.state.addResponse(initResponse);
        JsonObject initResult = assertResultResponse(initMessage, initResponse);
        assertNotNull(initResult);

        JsonObject serverInfo = initResult.getJsonObject("serverInfo");
        JsonObject initCapabilities = initResult.getJsonObject("capabilities");
        List<ServerCapability> capabilities = new ArrayList<>();
        if (initCapabilities != null) {
            for (String capability : initCapabilities.fieldNames()) {
                capabilities.add(new ServerCapability(capability, initCapabilities.getJsonObject(capability).getMap()));
            }
        }
        Implementation implementation = Messages.decodeImplementation(serverInfo);
        InitResult r = new InitResult(initResult.getString("protocolVersion"),
                implementation.name(),
                implementation.title(),
                implementation.version(),
                capabilities,
                initResult.getString("instructions"),
                implementation,
                initResult.getJsonObject("_meta"));
        if (assertFunction != null) {
            assertFunction.accept(r);
        }
        this.initResult = r;

        if (openSubsidiarySse) {
            client.connectSubsidiarySse(additionalHeaders(null));
            // Wait for the subsidiary SSE stream to be established;
            // the server sends a log notification when the stream opens
            client.state.waitForNotifications(1);
            if (autoPong) {
                client.setRequestConsumer(m -> {
                    String method = m.getString("method");
                    if (method != null && "ping".equals(method)) {
                        JsonObject pong = Messages.newResult(m.getValue("id"), new JsonObject());
                        sendAndForget(pong);
                    }
                });
            }
        }

        // Send "notifications/initialized"
        JsonObject nofitication = newMessage("notifications/initialized");
        MultiMap headers = additionalHeaders(nofitication);
        addAuthorizationHeader(headers, clientAuthorization);
        try (TracingHandle ignored = startTracingSpan(nofitication, headers)) {
            response = client.sendSync(nofitication.encode(), headers);
        }
        // The server must respond with 202 for response or notification
        if (response.statusCode() != 202) {
            throw new IllegalStateException(
                    "Initialization not finished successfully; HTTP response status: " + response.statusCode());
        }
        connected.set(true);
        return this;
    }

    private MultiMap additionalHeaders(JsonObject message) {
        MultiMap ret = additionalHeaders.apply(message);
        if (protocolVersion.isStateless()) {
            ret.add("MCP-Protocol-Version", protocolVersion.version());
            if (message != null) {
                String method = message.getString("method");
                if (method != null) {
                    ret.add("Mcp-Method", method);
                }
                JsonObject params = message.getJsonObject("params");
                if (params != null) {
                    // Mcp-Name for tools/call and prompts/get (name), resources/read (uri)
                    if ("tools/call".equals(method) || "prompts/get".equals(method)) {
                        String name = params.getString("name");
                        if (name != null) {
                            ret.add("Mcp-Name", name);
                        }
                    } else if ("resources/read".equals(method)) {
                        String uri = params.getString("uri");
                        if (uri != null) {
                            ret.add("Mcp-Name", uri);
                        }
                    }
                }
            }
        } else if (mcpSessionId != null) {
            ret.add("Mcp-Session-Id", mcpSessionId);
        }
        return ret;
    }

    @Override
    public void disconnect() {
        mcpSessionId = null;
        connected.set(false);
        client = null;
    }

    @Override
    public void terminateSession() {
        if (protocolVersion.isStateless()) {
            return;
        }
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        if (mcpSessionId != null) {
            headers.add("Mcp-Session-Id", mcpSessionId);
        }
        client.sendTerminate(headers);
    }

    @Override
    public McpStreamableAssert when() {
        return new McpStreamableAssertImpl();
    }

    @Override
    protected McpClientState clientState() {
        return client.state;
    }

    @Override
    public void sendAndForget(JsonObject message) {
        MultiMap headers = additionalHeaders(message);
        try (TracingHandle tracingHandle = startTracingSpan(message, headers)) {
            send(message, headers, clientAuthorization);
        }
    }

    private void send(JsonObject message, MultiMap additionalHeaders, Authorization authorization) {
        send(message.encode(), additionalHeaders, authorization);
    }

    private void send(String data, MultiMap additionalHeaders, Authorization authorization) {
        if (!connected.get()) {
            throw new IllegalStateException("Client is not connected");
        }
        addAuthorizationHeader(additionalHeaders, authorization);
        client.send(data, additionalHeaders);
    }

    class McpStreamableAssertImpl extends McpAssertBase implements McpStreamableAssert {

        protected final AtomicReference<MultiMap> additionalHeaders;
        protected final AtomicReference<Authorization> authorization;

        private McpStreamableAssertImpl() {
            this.additionalHeaders = new AtomicReference<>();
            this.authorization = new AtomicReference<>();
        }

        @Override
        public McpStreamableAssert addHeaders(MultiMap additionalHeaders) {
            this.additionalHeaders.set(additionalHeaders);
            return this;
        }

        @Override
        public McpStreamableAssert basicAuth(String username, String password) {
            this.authorization.set(Authorization.basic(username, password));
            return this;
        }

        @Override
        public McpStreamableAssert noBasicAuth() {
            this.authorization.set(Authorization.none());
            return this;
        }

        @Override
        protected McpStreamableAssert self() {
            return this;
        }

        protected TracingHandle doSend(JsonObject message) {
            try (TracingHandle tracingHandle = startTracingSpan(message,
                    McpStreamableTestClientImpl.this.additionalHeaders(message))) {
                Authorization authorization = this.authorization.get();
                if (authorization == null) {
                    authorization = clientAuthorization;
                }
                send(message, McpStreamableTestClientImpl.this.additionalHeaders(message), authorization);
            }
            return null;
        }

    }

    static class BuilderImpl extends McpTestClientBuilder<McpStreamableTestClient.Builder>
            implements McpStreamableTestClient.Builder {

        private String mcpPath = "/mcp";
        private URI baseUri = McpAssured.baseUri;
        private Function<JsonObject, MultiMap> additionalHeaders = m -> MultiMap.caseInsensitiveMultiMap();
        private Authorization authorization;
        private boolean openSubsidiarySse = false;
        private Consumer<ConnectFailureResponse> connectFailureConsumer;

        @Override
        public McpStreamableTestClient.Builder setBaseUri(URI baseUri) {
            if (baseUri == null) {
                throw mustNotBeNull("baseUri");
            }
            this.baseUri = baseUri;
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setMcpPath(String mcpPath) {
            if (mcpPath == null) {
                throw mustNotBeNull("mcpPath");
            }
            this.mcpPath = mcpPath;
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setAdditionalHeaders(Function<JsonObject, MultiMap> additionalHeaders) {
            if (additionalHeaders == null) {
                throw mustNotBeNull("additionalHeaders");
            }
            this.additionalHeaders = additionalHeaders;
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setBasicAuth(String username, String password) {
            if (username == null) {
                throw mustNotBeNull("username");
            }
            if (password == null) {
                throw mustNotBeNull("password");
            }
            this.authorization = Authorization.basic(username, password);
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setBearerToken(String token) {
            if (token == null) {
                throw mustNotBeNull("token");
            }
            this.authorization = Authorization.bearer(token);
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setOpenSubsidiarySse(boolean value) {
            this.openSubsidiarySse = value;
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setExpectConnectFailure(Consumer<ConnectFailureResponse> assertFunction) {
            this.connectFailureConsumer = assertFunction;
            return this;
        }

        @Override
        public McpStreamableTestClient build() {
            if (baseUri == null) {
                throw new IllegalArgumentException("Base URI must be set");
            }
            return new McpStreamableTestClientImpl(this);
        }

    }

}
