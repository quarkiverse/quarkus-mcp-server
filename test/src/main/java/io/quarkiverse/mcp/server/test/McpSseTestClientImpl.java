package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured.HttpResponse;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class McpSseTestClientImpl extends McpTestClientBase<McpSseAssert, McpSseTestClient> implements McpSseTestClient {

    private static final Logger LOG = Logger.getLogger(McpSseTestClientImpl.class);

    private final URI sseEndpoint;
    private final HttpClient httpClient;
    private final boolean expectSseConnectionFailure;

    private volatile McpSseClient client;
    private volatile URI messageEndpoint;

    McpSseTestClientImpl(BuilderImpl builder) {
        super(builder.name, builder.version, builder.protocolVersion, builder.clientCapabilities, builder.additionalHeaders,
                builder.autoPong, builder.basicAuth, builder.title, builder.description, builder.websiteUrl, builder.icons);
        this.sseEndpoint = createEndpointUri(builder.baseUri, builder.ssePath);
        this.expectSseConnectionFailure = builder.expectSseConnectionFailure;
        this.httpClient = HttpClient.newHttpClient();
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
                    sendAndForget(pong);
                }
            });
        }

        Map<String, String> headers = new HashMap<>();
        if (clientBasicAuth != null) {
            headers.put(HEADER_AUTHORIZATION,
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

        JsonObject initMessage = newInitMessage();
        HttpResponse response = sendSync(initMessage, additionalHeaders.apply(initMessage), clientBasicAuth);
        assertEquals(200, response.statusCode(), "Invalid HTTP response status: " + response.statusCode());

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
        response = sendSync(nofitication, additionalHeaders.apply(nofitication), clientBasicAuth);
        assertEquals(200, response.statusCode());
        connected.set(true);
        return this;
    }

    @Override
    public void disconnect() {
        JsonObject message = newMessage("q/close");
        HttpResponse response = sendSync(message, additionalHeaders.apply(message), clientBasicAuth);
        assertEquals(200, response.statusCode());
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
        sendAsync(message.encode(), additionalHeaders.apply(message), clientBasicAuth);
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
            BasicAuth basicAuth = this.basicAuth.get();
            if (basicAuth == null) {
                basicAuth = clientBasicAuth;
            }
            HttpResponse response = sendSync(message, additionalHeaders(message), basicAuth);
            Consumer<HttpResponse> validator = httpResponseValidator.get();
            if (validator != null) {
                validator.accept(response);
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
            BasicAuth basicAuth = this.basicAuth.get();
            if (basicAuth == null) {
                basicAuth = clientBasicAuth;
            }
            HttpResponse response = sendSync(batch, headers, basicAuth);
            Consumer<HttpResponse> validator = httpResponseValidator.get();
            if (validator != null) {
                validator.accept(response);
            }
            return super.thenAssertResults();
        }

    }

    static class BuilderImpl extends McpTestClientBuilder<McpSseTestClient.Builder> implements McpSseTestClient.Builder {

        private String ssePath = "/mcp/sse";
        private URI baseUri = McpAssured.baseUri;
        private Function<JsonObject, MultiMap> additionalHeaders = m -> MultiMap.caseInsensitiveMultiMap();
        private BasicAuth basicAuth;
        private boolean expectSseConnectionFailure;

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

    private HttpResponse sendSync(JsonObject message, MultiMap additionalHeaders, BasicAuth basicAuth) {
        return sendSync(message.encode(), additionalHeaders, basicAuth);
    }

    private HttpResponse sendSync(JsonArray batch, MultiMap additionalHeaders, BasicAuth basicAuth) {
        return sendSync(batch.encode(), additionalHeaders, basicAuth);
    }

    private CompletableFuture<java.net.http.HttpResponse<String>> sendAsync(String data, MultiMap additionalHeaders,
            BasicAuth basicAuth) {
        if (messageEndpoint == null) {
            throw new IllegalStateException("Message endpoint not ready");
        }
        return httpClient.sendAsync(buildRequest(data, additionalHeaders, basicAuth), BodyHandlers.ofString());
    }

    private HttpResponse sendSync(String data, MultiMap additionalHeaders, BasicAuth basicAuth) {
        if (messageEndpoint == null) {
            throw new IllegalStateException("Message endpoint not ready");
        }
        java.net.http.HttpResponse<String> r;
        try {
            r = httpClient.send(buildRequest(data, additionalHeaders, basicAuth), BodyHandlers.ofString());
            MultiMap responseHeaders = MultiMap.caseInsensitiveMultiMap();
            for (Entry<String, List<String>> e : r.headers().map().entrySet()) {
                for (String val : e.getValue()) {
                    responseHeaders.add(e.getKey(), val);
                }
            }
            return new HttpResponse(r.statusCode(), responseHeaders, r.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private HttpRequest buildRequest(String data, MultiMap additionalHeaders, BasicAuth basicAuth) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(messageEndpoint)
                .version(Version.HTTP_1_1)
                .header("Accept", "application/json")
                .POST(BodyPublishers.ofString(data));
        additionalHeaders.forEach(builder::header);

        if (basicAuth != null && !basicAuth.isEmpty()) {
            builder.header(HEADER_AUTHORIZATION,
                    McpTestClientBase.getBasicAuthenticationHeader(basicAuth.username(), basicAuth.password()));
        }
        return builder.build();
    }

}
