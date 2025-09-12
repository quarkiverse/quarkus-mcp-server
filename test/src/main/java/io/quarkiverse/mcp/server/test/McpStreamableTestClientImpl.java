package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class McpStreamableTestClientImpl extends McpTestClientBase<McpStreamableAssert, McpStreamableTestClient>
        implements McpStreamableTestClient {

    private static final Logger LOG = Logger.getLogger(McpStreamableTestClientImpl.class);

    private final URI mcpEndpoint;
    private final boolean openSubsidiarySse;
    private final boolean expectConnectFailure;

    private volatile McpStreamableClient client;
    private volatile String mcpSessionId;

    private McpStreamableTestClientImpl(BuilderImpl builder) {
        super(builder.name, builder.version, builder.protocolVersion, builder.clientCapabilities, builder.additionalHeaders,
                builder.autoPong, builder.basicAuth);
        this.mcpEndpoint = createEndpointUri(builder.baseUri, builder.mcpPath);
        this.client = new McpStreamableClient(mcpEndpoint);
        this.openSubsidiarySse = builder.openSubsidiarySse;
        this.expectConnectFailure = builder.expectConnectFailure;
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
        JsonObject initMessage = newInitMessage();
        MultiMap initHeaders = additionalHeaders.apply(initMessage);
        addAuthorizationHeader(initHeaders, clientBasicAuth);
        HttpResponse<String> response = client.sendSync(initMessage.encode(), initHeaders);
        if (expectConnectFailure) {
            assertNotEquals(200, response.statusCode());
            return this;
        } else {
            assertEquals(200, response.statusCode(), "Invalid HTTP response status: " + response.statusCode());
        }

        mcpSessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null);
        if (mcpSessionId == null) {
            throw new IllegalStateException("Mcp-Session-Id header not found: " + response.headers());
        }
        LOG.infof("Mcp-Session-Id received: %s", mcpSessionId);

        JsonObject initResponse = new JsonObject(response.body());
        client.state.responses.add(initResponse);
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
        InitResult r = new InitResult(initResult.getString("protocolVersion"), serverInfo.getString("name"),
                serverInfo.getString("title"),
                serverInfo.getString("version"),
                capabilities,
                serverInfo.getString("instructions"));
        if (assertFunction != null) {
            assertFunction.accept(r);
        }
        this.initResult = r;

        if (openSubsidiarySse) {
            client.connectSubsidiarySse(additionalHeaders(null));
        }

        // Send "notifications/initialized"
        JsonObject nofitication = newMessage("notifications/initialized");
        MultiMap headers = additionalHeaders(nofitication);
        addAuthorizationHeader(headers, clientBasicAuth);
        response = client.sendSync(nofitication.encode(), headers);
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
        if (mcpSessionId != null) {
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
    public McpStreamableAssert when() {
        return new McpStreamableAssertImpl();
    }

    @Override
    public McpStreamableAssert whenBatch() {
        return new McpStreamableAssertBatch();
    }

    @Override
    protected McpClientState clientState() {
        return client.state;
    }

    @Override
    protected int nextRequestId() {
        return client.state.nextRequestId();
    }

    @Override
    public void sendAndForget(JsonObject message) {
        send(message, additionalHeaders(message), clientBasicAuth);
    }

    private void send(JsonObject message, MultiMap additionalHeaders, BasicAuth basicAuth) {
        send(message.encode(), additionalHeaders, basicAuth);
    }

    private void send(JsonArray batch, MultiMap additionalHeaders, BasicAuth basicAuth) {
        send(batch.encode(), additionalHeaders, basicAuth);
    }

    private void send(String data, MultiMap additionalHeaders, BasicAuth basicAuth) {
        if (!connected.get()) {
            throw new IllegalStateException("Client is not connected");
        }
        addAuthorizationHeader(additionalHeaders, basicAuth);
        client.send(data, additionalHeaders);
    }

    class McpStreamableAssertImpl extends McpAssertBase implements McpStreamableAssert {

        protected final List<McpTestClientBase.ResponseAssert> asserts = new ArrayList<>();

        protected final AtomicReference<MultiMap> additionalHeaders;
        protected final AtomicReference<BasicAuth> basicAuth;

        private McpStreamableAssertImpl() {
            this.additionalHeaders = new AtomicReference<>();
            this.basicAuth = new AtomicReference<>();
        }

        @Override
        public McpStreamableAssert addHeaders(MultiMap additionalHeaders) {
            this.additionalHeaders.set(additionalHeaders);
            return this;
        }

        @Override
        public McpStreamableAssert basicAuth(String username, String password) {
            this.basicAuth.set(new BasicAuth(username, password));
            return this;
        }

        @Override
        public McpStreamableAssert noBasicAuth() {
            this.basicAuth.set(new BasicAuth(null, null));
            return this;
        }

        @Override
        protected McpStreamableAssert self() {
            return this;
        }

        protected void doSend(JsonObject message) {
            BasicAuth basicAuth = this.basicAuth.get();
            if (basicAuth == null) {
                basicAuth = clientBasicAuth;
            }
            send(message, additionalHeaders(message), basicAuth);
        }

    }

    class McpStreamableAssertBatch extends McpStreamableAssertImpl {

        private final List<JsonObject> requests = new ArrayList<>();

        @Override
        protected void doSend(JsonObject message) {
            requests.add(message);
        }

        @Override
        public Snapshot thenAssertResults() {
            JsonArray batch = new JsonArray();
            requests.forEach(batch::add);
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            if (mcpSessionId != null) {
                headers.add("Mcp-Session-Id", mcpSessionId);
            }
            for (JsonObject request : requests) {
                headers.addAll(McpStreamableTestClientImpl.this.additionalHeaders.apply(request));
            }
            MultiMap additionalHeaders = this.additionalHeaders.get();
            if (additionalHeaders != null) {
                headers.addAll(additionalHeaders);
            }
            BasicAuth basicAuth = this.basicAuth.get();
            if (basicAuth == null) {
                basicAuth = clientBasicAuth;
            }
            send(batch, headers, basicAuth);
            return super.thenAssertResults();
        }

    }

    static class BuilderImpl implements McpStreamableTestClient.Builder {

        private String name = "test-client";
        private String version = "1.0";
        private String protocolVersion = "2025-06-18";
        private String mcpPath = "/mcp";
        private Set<ClientCapability> clientCapabilities = Set.of();
        private URI baseUri = McpAssured.baseUri;
        private Function<JsonObject, MultiMap> additionalHeaders = m -> MultiMap.caseInsensitiveMultiMap();
        private boolean autoPong = true;
        private BasicAuth basicAuth;
        private boolean openSubsidiarySse = false;
        private boolean expectConnectFailure = false;

        @Override
        public McpStreamableTestClient.Builder setName(String clientName) {
            if (clientName == null) {
                throw mustNotBeNull("clientName");
            }
            this.name = clientName;
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setVersion(String clientVersion) {
            if (clientVersion == null) {
                throw mustNotBeNull("clientVersion");
            }
            this.version = clientVersion;
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setProtocolVersion(String protocolVersion) {
            if (protocolVersion == null) {
                throw mustNotBeNull("protocolVersion");
            }
            this.protocolVersion = protocolVersion;
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setClientCapabilities(ClientCapability... capabilities) {
            this.clientCapabilities = new HashSet<>(Arrays.asList(capabilities));
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setAutoPong(boolean val) {
            this.autoPong = val;
            return this;
        }

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
            this.basicAuth = new BasicAuth(username, password);
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setOpenSubsidiarySse(boolean value) {
            this.openSubsidiarySse = value;
            return this;
        }

        @Override
        public McpStreamableTestClient.Builder setExpectConnectFailure() {
            this.expectConnectFailure = true;
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
