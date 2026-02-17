package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class McpWebSocketTestClientImpl extends McpTestClientBase<McpWebSocketAssert, McpWebSocketTestClient>
        implements McpWebSocketTestClient {

    private static final Logger LOG = Logger.getLogger(McpWebSocketTestClientImpl.class);

    private final URI endpointUri;

    private volatile McpWebSocketClient client;

    McpWebSocketTestClientImpl(BuilderImpl builder) {
        super(builder.name, builder.version, builder.protocolVersion, builder.clientCapabilities, null,
                builder.autoPong, builder.basicAuth, builder.title, builder.description, builder.websiteUrl, builder.icons);
        this.endpointUri = createEndpointUri(builder.baseUri, builder.endpointPath);
        LOG.debugf("McpWebSocketTestClient created with WebSocket endpoint: %s", endpointUri);
    }

    @Override
    public URI endpointUri() {
        return endpointUri;
    }

    @Override
    public McpWebSocketTestClient connect(Consumer<InitResult> assertFunction) {
        if (client == null) {
            Vertx vertx;
            ArcContainer container = Arc.container();
            if (container != null && container.isRunning()) {
                vertx = container.instance(Vertx.class).get();
            } else {
                vertx = Vertx.vertx();
            }
            // TODO additional headers
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            if (clientBasicAuth != null) {
                headers.add(HEADER_AUTHORIZATION,
                        McpTestClientBase.getBasicAuthenticationHeader(clientBasicAuth.username(), clientBasicAuth.password()));
            }

            client = new McpWebSocketClient(endpointUri, vertx, headers);
        }
        if (autoPong) {
            client.setRequestConsumer(m -> {
                String method = m.getString("method");
                if (method != null && "ping".equals(method)) {
                    JsonObject pong = Messages.newResult(m.getValue("id"), new JsonObject());
                    sendAndForget(pong);
                }
            });
        }

        JsonObject initMessage = newInitMessage();
        sendAndForget(initMessage);

        JsonObject initResponse = client.state.waitForResponse(initMessage);
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

        // Send "notifications/initialized"
        JsonObject nofitication = newMessage("notifications/initialized");
        client.send(nofitication.encode()).toCompletionStage().toCompletableFuture().join();

        connected.set(true);
        return this;
    }

    @Override
    public void disconnect() {
        client.disconnect();
    }

    @Override
    public McpWebSocketAssert when() {
        return new McpWebSocketAssertImpl();
    }

    @Override
    public McpWebSocketAssert whenBatch() {
        return new McpWebSocketAssertBatch();
    }

    @Override
    public void sendAndForget(JsonObject message) {
        client.send(message.encode());
    }

    @Override
    protected McpClientState clientState() {
        return client.state;
    }

    class McpWebSocketAssertImpl extends McpAssertBase implements McpWebSocketAssert {

        @Override
        protected McpWebSocketAssert self() {
            return this;
        }

        @Override
        protected void doSend(JsonObject message) {
            sendAndForget(message);
        }

    }

    class McpWebSocketAssertBatch extends McpWebSocketAssertImpl {

        private final List<JsonObject> requests = new ArrayList<>();

        @Override
        protected void doSend(JsonObject message) {
            requests.add(message);
        }

        @Override
        public Snapshot thenAssertResults() {
            JsonArray batch = new JsonArray();
            requests.forEach(batch::add);
            client.send(batch.encode());
            return super.thenAssertResults();
        }

    }

    static class BuilderImpl extends McpTestClientBuilder<McpWebSocketTestClient.Builder>
            implements McpWebSocketTestClient.Builder {

        private String endpointPath = "/mcp/ws";
        private URI baseUri = McpAssured.baseUri;
        private BasicAuth basicAuth;

        @Override
        public McpWebSocketTestClient.Builder setBasicAuth(String username,
                String password) {
            this.basicAuth = new BasicAuth(username, password);
            return this;
        }

        @Override
        public McpWebSocketTestClient.Builder setBaseUri(URI baseUri) {
            this.baseUri = baseUri;
            return this;
        }

        @Override
        public McpWebSocketTestClient.Builder setEndpointPath(String path) {
            this.endpointPath = path;
            return this;
        }

        @Override
        public McpWebSocketTestClient build() {
            return new McpWebSocketTestClientImpl(this);
        }

    }

}
