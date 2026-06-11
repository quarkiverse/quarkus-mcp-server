package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

class McpWebSocketTestClientImpl extends McpTestClientBase<McpWebSocketAssert, McpWebSocketTestClient>
        implements McpWebSocketTestClient {

    private static final Logger LOG = Logger.getLogger(McpWebSocketTestClientImpl.class);

    private final URI endpointUri;

    private volatile McpWebSocketClient client;

    McpWebSocketTestClientImpl(BuilderImpl builder) {
        super(builder.name, builder.version, builder.protocolVersion, builder.clientCapabilities, null,
                builder.autoPong, builder.authorization, builder.title, builder.description, builder.websiteUrl, builder.icons,
                builder.openTelemetry);
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
            addAuthorizationHeader(headers, clientAuthorization);

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
        try {
            client.send(nofitication.encode()).toCompletionStage()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending initialized notification", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to send initialized notification", e);
        }

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
        protected TracingHandle doSend(JsonObject message) {
            sendAndForget(message);
            return null;
        }

    }

    static class BuilderImpl extends McpTestClientBuilder<McpWebSocketTestClient.Builder>
            implements McpWebSocketTestClient.Builder {

        private String endpointPath = "/mcp/ws";
        private URI baseUri = McpAssured.baseUri;
        private Authorization authorization;

        @Override
        public McpWebSocketTestClient.Builder setBasicAuth(String username,
                String password) {
            this.authorization = Authorization.basic(username, password);
            return this;
        }

        @Override
        public McpWebSocketTestClient.Builder setBearerToken(String token) {
            if (token == null) {
                throw mustNotBeNull("token");
            }
            this.authorization = Authorization.bearer(token);
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
