package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured.HttpResponse;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class McpSseTestClientImpl extends McpTestClientBase<McpSseAssert, McpSseTestClient> implements McpSseTestClient {

    private static final Logger LOG = Logger.getLogger(McpSseTestClientImpl.class);

    private final URI sseEndpoint;
    private final boolean expectSseConnectionFailure;

    private volatile McpSseClient client;
    private volatile URI messageEndpoint;

    McpSseTestClientImpl(BuilderImpl builder) {
        super(builder.name, builder.version, builder.protocolVersion, builder.clientCapabilities, builder.additionalHeaders,
                builder.autoPong, builder.basicAuth);
        this.sseEndpoint = createEndpointUri(builder.baseUri, builder.ssePath);
        this.expectSseConnectionFailure = builder.expectSseConnectionFailure;
        LOG.debugf("McpSseTestClient created with SSE endpoint: %s", sseEndpoint);
    }

    @Override
    public URI sseEndpoint() {
        return sseEndpoint;
    }

    @Override
    public URI messageEndpoint() {
        return messageEndpoint;
    }

    @Override
    public McpSseTestClient connect(Consumer<InitResult> assertFunction) {
        client = new McpSseClient(sseEndpoint);

        if (autoPong) {
            client.setRequestConsumer(m -> {
                String method = m.getString("method");
                if (method != null && "ping".equals(method)) {
                    JsonObject pong = Messages.newResult(m.getValue("id"), new JsonObject());
                    send(pong, MultiMap.caseInsensitiveMultiMap(), clientBasicAuth);
                }
            });
        }

        Map<String, String> headers = new HashMap<>();
        if (clientBasicAuth != null) {
            headers.put("Authorization",
                    McpTestClientBase.getBasicAuthenticationHeader(clientBasicAuth.username(), clientBasicAuth.password()));
        }

        // Normally, the CF returned from the connect method never completes
        CompletableFuture<java.net.http.HttpResponse<Void>> cf = client.connect(headers);
        if (expectSseConnectionFailure) {
            try {
                java.net.http.HttpResponse<Void> response = cf.get(5, TimeUnit.SECONDS);
                assertNotEquals(200, response.statusCode());
                return this;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted");
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }

        var event = client.waitForFirstEvent();

        URI uriBase;
        try {
            uriBase = new URI(sseEndpoint.getScheme(), null, sseEndpoint.getHost(), sseEndpoint.getPort(), null, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }

        URI endpoint = URI.create(uriBase.toString() + event.data().strip());
        this.messageEndpoint = endpoint;
        LOG.infof("Message endpoint received: %s", endpoint);
        connected.set(true);

        JsonObject initMessage = newInitMessage();
        send(initMessage, additionalHeaders.apply(initMessage), clientBasicAuth).then().statusCode(200);

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
        InitResult r = new InitResult(initResult.getString("protocolVersion"), serverInfo.getString("name"),
                serverInfo.getString("version"), capabilities);
        if (assertFunction != null) {
            assertFunction.accept(r);
        }
        this.initResult = r;

        // Send "notifications/initialized"
        JsonObject nofitication = newMessage("notifications/initialized");
        send(nofitication, additionalHeaders.apply(nofitication), clientBasicAuth).then().statusCode(200);
        return this;
    }

    @Override
    public void disconnect() {
        JsonObject message = newMessage("q/close");
        send(message, additionalHeaders.apply(message), clientBasicAuth).then().statusCode(200);
        messageEndpoint = null;
        connected.set(false);
        client = null;
    }

    @Override
    public McpSseAssert when() {
        if (!isConnected()) {
            throw notConnected();
        }
        return new McpSseAssertImpl();
    }

    @Override
    public McpSseAssert whenBatch() {
        if (!isConnected()) {
            throw notConnected();
        }
        return new McpSseAssertBatch();
    }

    @Override
    protected McpClientState clientState() {
        return client.state;
    }

    @Override
    public void sendAndForget(JsonObject message) {
        send(message, additionalHeaders.apply(message), clientBasicAuth);
    }

    class McpSseAssertImpl extends McpAssertBase implements McpSseAssert {

        static final Consumer<HttpResponse> DEFAULT_HTTP_RESPONSE_VALIDATOR = httpResponse -> assertEquals(200,
                httpResponse.statusCode());

        protected final AtomicReference<Consumer<HttpResponse>> httpResponseValidator;
        protected final AtomicReference<MultiMap> additionalHeaders;
        protected final AtomicReference<BasicAuth> basicAuth;

        McpSseAssertImpl() {
            this.additionalHeaders = new AtomicReference<>(MultiMap.caseInsensitiveMultiMap());
            this.basicAuth = new AtomicReference<>();
            this.httpResponseValidator = new AtomicReference<>(DEFAULT_HTTP_RESPONSE_VALIDATOR);
        }

        @Override
        public McpSseAssert addHeader(String name, String value) {
            this.additionalHeaders.get().add(name, value);
            return this;
        }

        @Override
        public McpSseAssert addHeaders(MultiMap additionalHeaders) {
            this.additionalHeaders.set(additionalHeaders);
            return this;
        }

        @Override
        public McpSseAssert validateHttpResponse(Consumer<HttpResponse> validator) {
            this.httpResponseValidator.set(validator);
            return this;
        }

        @Override
        public McpSseAssert basicAuth(String username, String password) {
            this.basicAuth.set(new BasicAuth(username, password));
            return this;
        }

        @Override
        public McpSseAssert noBasicAuth() {
            this.basicAuth.set(new BasicAuth(null, null));
            return this;
        }

        protected void doSend(JsonObject message) {
            Response response = send(message, additionalHeaders(message), basicAuth.get());
            Consumer<HttpResponse> validator = httpResponseValidator.get();
            if (validator != null) {
                MultiMap responseHeaders = MultiMap.caseInsensitiveMultiMap();
                for (Header header : response.headers()) {
                    responseHeaders.add(header.getName(), header.getValue());
                }
                validator.accept(new HttpResponse(response.statusCode(), responseHeaders, response.body().asString()));
            }
        }

        @Override
        protected McpSseAssert self() {
            return this;
        }

        protected MultiMap additionalHeaders(JsonObject message) {
            MultiMap headers = McpSseTestClientImpl.this.additionalHeaders.apply(message);
            MultiMap additionalHeaders = this.additionalHeaders.get();
            if (additionalHeaders != null) {
                headers.addAll(additionalHeaders);
            }
            return headers;
        }

    }

    class McpSseAssertBatch extends McpSseAssertImpl {

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
            for (JsonObject request : requests) {
                headers.addAll(McpSseTestClientImpl.this.additionalHeaders.apply(request));
            }
            MultiMap additionalHeaders = this.additionalHeaders.get();
            if (additionalHeaders != null) {
                headers.addAll(additionalHeaders);
            }
            Response response = send(batch, headers, basicAuth.get());
            Consumer<HttpResponse> validator = httpResponseValidator.get();
            if (validator != null) {
                MultiMap responseHeaders = MultiMap.caseInsensitiveMultiMap();
                for (Header header : response.headers()) {
                    responseHeaders.add(header.getName(), header.getValue());
                }
                validator.accept(new HttpResponse(response.statusCode(), responseHeaders, response.body().asString()));
            }
            return super.thenAssertResults();
        }

    }

    static class BuilderImpl implements McpSseTestClient.Builder {

        private String name = "test-client";
        private String version = "1.0";
        private String protocolVersion = "2024-11-05";
        private String ssePath = "/mcp/sse";
        private Set<ClientCapability> clientCapabilities = Set.of();
        private URI baseUri = McpAssured.baseUri;
        private Function<JsonObject, MultiMap> additionalHeaders = m -> MultiMap.caseInsensitiveMultiMap();
        private boolean autoPong = true;
        private BasicAuth basicAuth;
        private boolean expectSseConnectionFailure;

        @Override
        public McpSseTestClient.Builder setName(String clientName) {
            if (clientName == null) {
                throw mustNotBeNull("clientName");
            }
            this.name = clientName;
            return this;
        }

        @Override
        public McpSseTestClient.Builder setVersion(String clientVersion) {
            if (clientVersion == null) {
                throw mustNotBeNull("clientVersion");
            }
            this.version = clientVersion;
            return this;
        }

        @Override
        public McpSseTestClient.Builder setProtocolVersion(String protocolVersion) {
            if (protocolVersion == null) {
                throw mustNotBeNull("protocolVersion");
            }
            this.protocolVersion = protocolVersion;
            return this;
        }

        @Override
        public McpSseTestClient.Builder setClientCapabilities(ClientCapability... capabilities) {
            this.clientCapabilities = new HashSet<>(Arrays.asList(capabilities));
            return this;
        }

        @Override
        public McpSseTestClient.Builder setAutoPong(boolean val) {
            this.autoPong = val;
            return this;
        }

        @Override
        public McpSseTestClient.Builder setBaseUri(URI baseUri) {
            if (baseUri == null) {
                throw mustNotBeNull("testUri");
            }
            this.baseUri = baseUri;
            return this;
        }

        @Override
        public McpSseTestClient.Builder setSsePath(String ssePath) {
            if (ssePath == null) {
                throw mustNotBeNull("ssePath");
            }
            this.ssePath = ssePath;
            return this;
        }

        @Override
        public McpSseTestClient.Builder setAdditionalHeaders(Function<JsonObject, MultiMap> additionalHeaders) {
            if (additionalHeaders == null) {
                throw mustNotBeNull("additionalHeaders");
            }
            this.additionalHeaders = additionalHeaders;
            return this;
        }

        @Override
        public McpSseTestClient.Builder setBasicAuth(String username, String password) {
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
        public McpSseTestClient.Builder setExpectSseConnectionFailure() {
            this.expectSseConnectionFailure = true;
            return this;
        }

        @Override
        public McpSseTestClient build() {
            if (baseUri == null) {
                throw new IllegalArgumentException("Base URI must be set");
            }
            return new McpSseTestClientImpl(this);
        }

    }

    private Response send(JsonObject message, MultiMap additionalHeaders, BasicAuth basicAuth) {
        return send(message.encode(), additionalHeaders, basicAuth);
    }

    private Response send(JsonArray batch, MultiMap additionalHeaders, BasicAuth basicAuth) {
        return send(batch.encode(), additionalHeaders, basicAuth);
    }

    private Response send(String data, MultiMap additionalHeaders, BasicAuth basicAuth) {
        if (!connected.get()) {
            throw new IllegalStateException("Client is not connected");
        }
        RequestSpecification request = RestAssured.given()
                .urlEncodingEnabled(false);
        if (basicAuth != null) {
            if (!basicAuth.isEmpty()) {
                request.auth()
                        .preemptive()
                        .basic(basicAuth.username(), basicAuth.password());
            }
        } else if (clientBasicAuth != null) {
            request.auth()
                    .preemptive()
                    .basic(clientBasicAuth.username(), clientBasicAuth.password());
        }
        for (Entry<String, String> e : additionalHeaders) {
            request.header(new Header(e.getKey(), e.getValue()));
        }
        return request.body(data)
                .post(messageEndpoint);
    }

    @Override
    protected int nextRequestId() {
        if (!isConnected()) {
            throw notConnected();
        }
        return client.state.nextRequestId();
    }

}
