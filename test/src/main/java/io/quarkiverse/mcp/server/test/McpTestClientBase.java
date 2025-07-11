package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.Content.Type;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpError;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptArgument;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkiverse.mcp.server.test.McpAssured.PromptsPage;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ResourcesPage;
import io.quarkiverse.mcp.server.test.McpAssured.ResourcesTemplatesPage;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.quarkiverse.mcp.server.test.McpAssured.ToolAnnotations;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ToolsPage;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

abstract class McpTestClientBase<ASSERT extends McpAssert<ASSERT>, CLIENT extends McpTestClient<ASSERT, CLIENT>>
        implements McpTestClient<ASSERT, CLIENT> {

    protected final String name;
    protected final String version;
    protected final String protocolVersion;
    protected final Set<ClientCapability> clientCapabilities;
    protected final Function<JsonObject, MultiMap> additionalHeaders;
    protected final boolean autoPong;
    protected final BasicAuth clientBasicAuth;

    protected final AtomicBoolean connected;
    protected volatile InitResult initResult;

    McpTestClientBase(String name, String version, String protocolVersion, Set<ClientCapability> clientCapabilities,
            Function<JsonObject, MultiMap> additionalHeaders, boolean autoPong, BasicAuth clientBasicAuth) {
        super();
        this.name = name;
        this.version = version;
        this.protocolVersion = protocolVersion;
        this.clientCapabilities = clientCapabilities;
        this.additionalHeaders = additionalHeaders;
        this.autoPong = autoPong;
        this.clientBasicAuth = clientBasicAuth;
        this.connected = new AtomicBoolean();
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public InitResult initResult() {
        return initResult;
    }

    @Override
    public JsonObject newMessage(String method) {
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("id", nextRequestId());
    }

    @Override
    public Snapshot waitForNotifications(int count) {
        if (!isConnected()) {
            throw notConnected();
        }
        clientState().waitForNotifications(count);
        return snapshot();
    }

    @Override
    public Snapshot waitForRequests(int count) {
        if (!isConnected()) {
            throw notConnected();
        }
        clientState().waitForRequests(count);
        return snapshot();
    }

    @Override
    public JsonObject waitForResponse(JsonObject request) {
        if (!isConnected()) {
            throw notConnected();
        }
        return clientState().waitForResponse(request);
    }

    protected abstract McpClientState clientState();

    @Override
    public Snapshot snapshot() {
        return clientState().toSnapshot();
    }

    protected static void assertResponse(JsonObject request, JsonObject response) {
        assertEquals(request.getInteger("id"), response.getInteger("id"));
        assertEquals("2.0", response.getString("jsonrpc"));
    }

    protected static JsonObject assertResultResponse(JsonObject request, JsonObject response) {
        assertResponse(request, response);
        JsonObject result = response.getJsonObject("result");
        assertNotNull(result, "Expected result response but received: " + response);
        return result;
    }

    protected static JsonObject assertErrorResponse(JsonObject request, JsonObject response) {
        assertResponse(request, response);
        JsonObject error = response.getJsonObject("error");
        assertNotNull(error, "Expected error response but received: " + response);
        return error;
    }

    protected JsonObject newMessage(String method, Consumer<JsonObject> paramsConsumer) {
        JsonObject params = new JsonObject();
        paramsConsumer.accept(params);
        return newMessage(method)
                .put("params", params);
    }

    protected JsonObject newToolsCallMessage(String toolName, Map<String, Object> arguments, Map<String, Object> meta) {
        return newMessage(McpAssured.TOOLS_CALL, p -> {
            p.put("name", toolName);
            addMeta(p, meta);
            if (!arguments.isEmpty()) {
                p.put("arguments", arguments);
            }
        });
    }

    protected JsonObject newListMessage(String method, Map<String, Object> meta, String cursor) {
        return newMessage(method, p -> {
            addMeta(p, meta);
            if (cursor != null) {
                p.put("cursor", cursor);
            }
        });
    }

    protected void addMeta(JsonObject params, Map<String, Object> meta) {
        if (!meta.isEmpty()) {
            params.put("_meta", meta);
        }
    }

    protected JsonObject newPromptsGetMessage(String promptName, Map<String, String> args, Map<String, Object> meta) {
        return newMessage(McpAssured.PROMPTS_GET, p -> {
            p.put("name", promptName);
            addMeta(p, meta);
            if (!args.isEmpty()) {
                p.put("arguments", args);
            }
        });
    }

    protected JsonObject newResourcesReadMessage(String uri, Map<String, Object> meta) {
        return newMessage(McpAssured.RESOURCES_READ, p -> {
            p.put("uri", uri);
            addMeta(p, meta);
        });
    }

    @Override
    public JsonObject newInitMessage() {
        JsonObject initMessage = newMessage("initialize");
        JsonObject params = new JsonObject()
                .put("clientInfo", new JsonObject()
                        .put("name", name)
                        .put("version", version))
                .put("protocolVersion", protocolVersion);
        if (!clientCapabilities.isEmpty()) {
            JsonObject capabilities = new JsonObject();
            for (ClientCapability capability : clientCapabilities) {
                capabilities.put(capability.name(), capability.properties());
            }
            params.put("capabilities", capabilities);
        }
        initMessage.put("params", params);
        return initMessage;
    }

    protected abstract int nextRequestId();

    static ResourceContents parseResourceContents(JsonObject resourceContent) {
        if (resourceContent.containsKey("text")) {
            return new TextResourceContents(resourceContent.getString("uri"), resourceContent.getString("text"),
                    resourceContent.getString("mime"));
        } else if (resourceContent.containsKey("blob")) {
            return new BlobResourceContents(resourceContent.getString("uri"), resourceContent.getString("blob"),
                    resourceContent.getString("mime"));
        } else {
            throw new IllegalStateException("Unsupported resource content type");
        }
    }

    private static Content parseContent(JsonObject c) {
        Content.Type type = Content.Type.valueOf(c.getString("type").toUpperCase());
        if (type == Type.TEXT) {
            return new TextContent(c.getString("text"));
        } else if (type == Type.AUDIO) {
            return new AudioContent(c.getString("data"), c.getString("mimeType"));
        } else if (type == Type.IMAGE) {
            return new ImageContent(c.getString("data"), c.getString("mimeType"));
        } else if (type == Type.RESOURCE) {
            return new EmbeddedResource(McpTestClientBase.parseResourceContents(c.getJsonObject("resource")));
        } else {
            throw new IllegalStateException("Unsupported content type: " + type);
        }
    }

    static final String getBasicAuthenticationHeader(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes());
    }

    protected static IllegalStateException notConnected() {
        return new IllegalStateException("Client is not connected yet");
    }

    protected static IllegalArgumentException mustNotBeNull(String name) {
        return new IllegalArgumentException(name + " must not be null");
    }

    protected static URI createEndpointUri(URI baseUri, String path) {
        String endpointUri = baseUri.toString();
        if (endpointUri.endsWith("/")) {
            if (path.startsWith("/")) {
                endpointUri += path.substring(1);
            } else {
                endpointUri += path;
            }
        } else {
            if (path.startsWith("/")) {
                endpointUri += path;
            } else {
                endpointUri = endpointUri + "/" + path;
            }
        }
        return URI.create(endpointUri);
    }

    record BasicAuth(String username, String password) {

        boolean isEmpty() {
            return username == null;
        }
    }

    abstract class McpAssertBase implements McpAssert<ASSERT> {

        protected final List<McpTestClientBase.ResponseAssert> asserts = new ArrayList<>();

        protected abstract ASSERT self();

        protected abstract void doSend(JsonObject message);

        @Override
        public Snapshot thenAssertResults() {
            for (McpTestClientBase.ResponseAssert responseAssert : asserts) {
                JsonObject response = clientState().waitForResponse(responseAssert.request());
                assertNotNull(response);
                responseAssert.doAssert(response);
            }
            return snapshot();
        }

        @Override
        public PingMessage<ASSERT> ping() {
            return new PingMessageImpl();
        }

        @Override
        public ToolsListMessage<ASSERT> toolsList() {
            return new ToolsListMessageImpl();
        }

        @Override
        public ToolsCallMessage<ASSERT> toolsCall(String toolName) {
            if (toolName == null) {
                throw mustNotBeNull("toolName");
            }
            return new ToolsCallMessageImpl(toolName);
        }

        @Override
        public PromptsListMessage<ASSERT> promptsList() {
            return new PromptsListMessageImpl();
        }

        @Override
        public PromptsGetMessage<ASSERT> promptsGet(String promptName) {
            if (promptName == null) {
                throw mustNotBeNull("promptName");
            }
            return new PromptsGetMessageImpl(promptName);
        }

        @Override
        public ResourcesReadMessage<ASSERT> resourcesRead(String uri) {
            if (uri == null) {
                throw mustNotBeNull("uri");
            }
            return new ResourcesReadMessageImpl(uri);
        }

        @Override
        public ResourcesListMessage<ASSERT> resourcesList() {
            return new ResourcesListMessageImpl();
        }

        @Override
        public ResourcesTemplatesListMessage<ASSERT> resourcesTemplatesList() {
            return new ResourcesTemplatesListMessageImpl();
        }

        @Override
        public GenericMessage<ASSERT> message(JsonObject message) {
            if (message == null) {
                throw mustNotBeNull("message");
            }
            return new GenericMessageImpl(message);
        }

        class PingMessageImpl implements PingMessage<ASSERT> {

            private boolean pongAssert;
            private Consumer<McpError> errorAssertFunction;

            @Override
            public PingMessage<ASSERT> withPongAssert() {
                this.pongAssert = true;
                return this;
            }

            @Override
            public PingMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                if (errorAssertFunction == null) {
                    throw mustNotBeNull("errorAssertFunction");
                }
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                JsonObject message = newMessage(McpAssured.PING);
                doSend(message);
                if (pongAssert) {
                    asserts.add(new PongAssert(message));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }

        class ToolsListMessageImpl implements ToolsListMessage<ASSERT> {

            private String cursor;
            private Map<String, Object> meta = Map.of();
            private Consumer<ToolsPage> assertFunction;
            private Consumer<McpError> errorAssertFunction;

            @Override
            public ToolsListMessage<ASSERT> withCursor(String cursor) {
                this.cursor = cursor;
                return this;
            }

            @Override
            public ToolsListMessage<ASSERT> withMetadata(Map<String, Object> meta) {
                this.meta = meta;
                return this;
            }

            @Override
            public ToolsListMessage<ASSERT> withAssert(Consumer<ToolsPage> assertFunction) {
                if (assertFunction == null) {
                    throw mustNotBeNull("assertFunction");
                }
                this.assertFunction = assertFunction;
                return this;
            }

            @Override
            public ToolsListMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                if (errorAssertFunction == null) {
                    throw mustNotBeNull("errorAssertFunction");
                }
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                JsonObject message = newListMessage(McpAssured.TOOLS_LIST, meta, cursor);
                doSend(message);
                if (assertFunction != null) {
                    asserts.add(new ToolsListAssert(message, assertFunction));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }

        class ResourcesReadMessageImpl implements ResourcesReadMessage<ASSERT> {

            private final String uri;
            private Map<String, Object> meta = Map.of();
            private Consumer<ResourceResponse> assertFunction;
            private Consumer<McpError> errorAssertFunction;

            ResourcesReadMessageImpl(String uri) {
                this.uri = uri;
            }

            @Override
            public ResourcesReadMessage<ASSERT> withMetadata(Map<String, Object> meta) {
                if (meta == null) {
                    throw mustNotBeNull("meta");
                }
                this.meta = meta;
                return this;
            }

            @Override
            public ResourcesReadMessage<ASSERT> withAssert(Consumer<ResourceResponse> assertFunction) {
                if (assertFunction == null) {
                    throw mustNotBeNull("assertFunction");
                }
                this.assertFunction = assertFunction;
                return this;
            }

            @Override
            public ResourcesReadMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                if (errorAssertFunction == null) {
                    throw mustNotBeNull("errorAssertFunction");
                }
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                JsonObject message = newResourcesReadMessage(uri, meta);
                doSend(message);
                if (assertFunction != null) {
                    asserts.add(new ResourcesReadAssert(message, assertFunction));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }

        class PromptsGetMessageImpl implements PromptsGetMessage<ASSERT> {

            private final String promptName;
            private Map<String, String> args = Map.of();
            private Map<String, Object> meta = Map.of();
            private Consumer<PromptResponse> assertFunction;
            private Consumer<McpError> errorAssertFunction;

            PromptsGetMessageImpl(String promptName) {
                this.promptName = promptName;
            }

            @Override
            public PromptsGetMessage<ASSERT> withArguments(Map<String, String> args) {
                this.args = args;
                return this;
            }

            @Override
            public PromptsGetMessage<ASSERT> withMetadata(Map<String, Object> meta) {
                if (meta == null) {
                    throw mustNotBeNull("meta");
                }
                this.meta = meta;
                return this;
            }

            @Override
            public PromptsGetMessage<ASSERT> withAssert(Consumer<PromptResponse> assertFunction) {
                if (assertFunction == null) {
                    throw mustNotBeNull("assertFunction");
                }
                this.assertFunction = assertFunction;
                return this;
            }

            @Override
            public PromptsGetMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                if (errorAssertFunction == null) {
                    throw mustNotBeNull("errorAssertFunction");
                }
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                JsonObject message = newPromptsGetMessage(promptName, args, meta);
                doSend(message);
                if (assertFunction != null) {
                    asserts.add(new PromptsGetAssert(message, assertFunction));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }

        class ToolsCallMessageImpl implements ToolsCallMessage<ASSERT> {

            private final String toolName;
            private Map<String, Object> args = Map.of();
            private Map<String, Object> meta = Map.of();
            private Consumer<ToolResponse> assertFunction;
            private Consumer<McpError> errorAssertFunction;

            ToolsCallMessageImpl(String toolName) {
                this.toolName = toolName;
            }

            @Override
            public ToolsCallMessage<ASSERT> withArguments(Map<String, Object> args) {
                if (args == null) {
                    throw mustNotBeNull("args");
                }
                this.args = args;
                return this;
            }

            @Override
            public ToolsCallMessage<ASSERT> withMetadata(Map<String, Object> meta) {
                if (meta == null) {
                    throw mustNotBeNull("meta");
                }
                this.meta = meta;
                return this;
            }

            @Override
            public ToolsCallMessage<ASSERT> withAssert(Consumer<ToolResponse> assertFunction) {
                if (assertFunction == null) {
                    throw mustNotBeNull("assertFunction");
                }
                this.assertFunction = assertFunction;
                return this;
            }

            @Override
            public ToolsCallMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                if (errorAssertFunction == null) {
                    throw mustNotBeNull("errorAssertFunction");
                }
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                JsonObject message = newToolsCallMessage(toolName, args, meta);
                doSend(message);
                if (assertFunction != null) {
                    asserts.add(new ToolsCallAssert(message, assertFunction));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }

        class PromptsListMessageImpl implements PromptsListMessage<ASSERT> {

            private String cursor;
            private Map<String, Object> meta = Map.of();
            private Consumer<PromptsPage> assertFunction;
            private Consumer<McpError> errorAssertFunction;

            @Override
            public PromptsListMessage<ASSERT> withCursor(String cursor) {
                this.cursor = cursor;
                return this;
            }

            @Override
            public PromptsListMessage<ASSERT> withMetadata(Map<String, Object> meta) {
                this.meta = meta;
                return this;
            }

            @Override
            public PromptsListMessage<ASSERT> withAssert(Consumer<PromptsPage> assertFunction) {
                this.assertFunction = assertFunction;
                return this;
            }

            @Override
            public PromptsListMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                JsonObject message = newListMessage(McpAssured.PROMPTS_LIST, meta, cursor);
                doSend(message);
                if (assertFunction != null) {
                    asserts.add(new PromptsListAssert(message, assertFunction));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }

        class ResourcesTemplatesListMessageImpl implements ResourcesTemplatesListMessage<ASSERT> {

            private String cursor;
            private Map<String, Object> meta = Map.of();
            private Consumer<ResourcesTemplatesPage> assertFunction;
            private Consumer<McpError> errorAssertFunction;

            @Override
            public ResourcesTemplatesListMessage<ASSERT> withCursor(String cursor) {
                this.cursor = cursor;
                return this;
            }

            @Override
            public ResourcesTemplatesListMessage<ASSERT> withMetadata(Map<String, Object> meta) {
                this.meta = meta;
                return this;
            }

            @Override
            public ResourcesTemplatesListMessage<ASSERT> withAssert(Consumer<ResourcesTemplatesPage> assertFunction) {
                this.assertFunction = assertFunction;
                return this;
            }

            @Override
            public ResourcesTemplatesListMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                JsonObject message = newListMessage(McpAssured.RESOURCES_TEMPLATES_LIST, meta, cursor);
                doSend(message);
                if (assertFunction != null) {
                    asserts.add(new ResourcesTemplatesListAssert(message, assertFunction));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }

        class ResourcesListMessageImpl implements ResourcesListMessage<ASSERT> {

            private String cursor;
            private Map<String, Object> meta = Map.of();
            private Consumer<ResourcesPage> assertFunction;
            private Consumer<McpError> errorAssertFunction;

            @Override
            public ResourcesListMessage<ASSERT> withCursor(String cursor) {
                this.cursor = cursor;
                return this;
            }

            @Override
            public ResourcesListMessage<ASSERT> withMetadata(Map<String, Object> meta) {
                this.meta = meta;
                return this;
            }

            @Override
            public ResourcesListMessage<ASSERT> withAssert(Consumer<ResourcesPage> assertFunction) {
                this.assertFunction = assertFunction;
                return this;
            }

            @Override
            public ResourcesListMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                JsonObject message = newListMessage(McpAssured.RESOURCES_LIST, meta, cursor);
                doSend(message);
                if (assertFunction != null) {
                    asserts.add(new ResourcesListAssert(message, assertFunction));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }

        class GenericMessageImpl implements GenericMessage<ASSERT> {

            private final JsonObject message;
            private Consumer<JsonObject> assertFunction;
            private Consumer<McpError> errorAssertFunction;

            GenericMessageImpl(JsonObject message) {
                this.message = message;
            }

            @Override
            public GenericMessage<ASSERT> withAssert(Consumer<JsonObject> assertFunction) {
                if (assertFunction == null) {
                    throw mustNotBeNull("assertFunction");
                }
                this.assertFunction = assertFunction;
                return this;
            }

            @Override
            public GenericMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction) {
                if (assertFunction == null) {
                    throw mustNotBeNull("errorAssertFunction");
                }
                this.errorAssertFunction = errorAssertFunction;
                return this;
            }

            @Override
            public ASSERT send() {
                doSend(message);
                if (assertFunction != null) {
                    asserts.add(new MessageAssert(message, assertFunction));
                } else if (errorAssertFunction != null) {
                    asserts.add(new ErrorAssert(message, errorAssertFunction));
                }
                return self();
            }

        }
    }

    interface ResponseAssert {

        JsonObject request();

        void doAssert(JsonObject response);

    }

    record MessageAssert(JsonObject request, Consumer<JsonObject> responseAssert) implements McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            responseAssert.accept(response);
        }

    }

    record ErrorAssert(JsonObject request, Consumer<McpError> errorMessageAssert) implements McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            JsonObject error = assertErrorResponse(response, response);
            errorMessageAssert.accept(new McpError(error.getInteger("code"), error.getString("message")));
        }

    }

    record PongAssert(JsonObject request) implements McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            JsonObject result = assertResultResponse(request, response);
            assertTrue(result.isEmpty());
        }

    }

    record ResourcesListAssert(JsonObject request, Consumer<ResourcesPage> assertFunction)
            implements
                McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            JsonObject result = assertResultResponse(request, response);
            JsonArray resources = result.getJsonArray("resources");
            assertNotNull(resources);
            List<ResourceInfo> infos = new ArrayList<>();
            for (int i = 0; i < resources.size(); i++) {
                JsonObject info = resources.getJsonObject(i);
                infos.add(parseResource(info));
            }
            assertFunction.accept(new ResourcesPage(infos, result.getString("nextCursor")));
        }

        private ResourceInfo parseResource(JsonObject resource) {
            return new ResourceInfo(resource.getString("uri"), resource.getString("mimeType"), resource.getString("name"),
                    resource.getString("description"));
        }
    }

    record ResourcesTemplatesListAssert(JsonObject request, Consumer<ResourcesTemplatesPage> assertFunction)
            implements
                McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            JsonObject result = assertResultResponse(request, response);
            JsonArray templates = result.getJsonArray("resourceTemplates");
            assertNotNull(templates);
            List<ResourceTemplateInfo> infos = new ArrayList<>();
            for (int i = 0; i < templates.size(); i++) {
                JsonObject info = templates.getJsonObject(i);
                infos.add(parseResourceTemplate(info));
            }
            assertFunction.accept(new ResourcesTemplatesPage(infos, result.getString("nextCursor")));
        }

        private ResourceTemplateInfo parseResourceTemplate(JsonObject resource) {
            return new ResourceTemplateInfo(resource.getString("uriTemplate"), resource.getString("mimeType"),
                    resource.getString("name"),
                    resource.getString("description"));
        }
    }

    record PromptsListAssert(JsonObject request, Consumer<PromptsPage> assertFunction)
            implements
                McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            JsonObject result = assertResultResponse(request, response);
            JsonArray prompts = result.getJsonArray("prompts");
            assertNotNull(prompts);
            List<PromptInfo> infos = new ArrayList<>();
            for (int i = 0; i < prompts.size(); i++) {
                JsonObject prompt = prompts.getJsonObject(i);
                infos.add(parsePrompt(prompt));
            }
            assertFunction.accept(new PromptsPage(infos, result.getString("nextCursor")));
        }

        private PromptInfo parsePrompt(JsonObject prompt) {
            JsonArray args = prompt.getJsonArray("arguments");
            List<PromptArgument> promptArgs = null;
            if (!args.isEmpty()) {
                promptArgs = new ArrayList<>();
                for (int i = 0; i < args.size(); i++) {
                    JsonObject arg = args.getJsonObject(i);
                    promptArgs.add(new PromptArgument(arg.getString("name"), arg.getString("description"),
                            arg.getBoolean("required", false)));
                }
            }
            return new PromptInfo(prompt.getString("name"),
                    prompt.getString("description"), promptArgs);
        }
    }

    record PromptsGetAssert(JsonObject request, Consumer<PromptResponse> assertFunction)
            implements
                McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            if (assertFunction == null) {
                return;
            }
            JsonObject result = assertResultResponse(request, response);
            String description = result.getString("description");
            List<PromptMessage> promptMessages = new ArrayList<>();
            JsonArray messages = result.getJsonArray("messages");
            if (!messages.isEmpty()) {
                for (int i = 0; i < messages.size(); i++) {
                    JsonObject message = messages.getJsonObject(i);
                    Role role = Role.valueOf(message.getString("role").toUpperCase());
                    JsonObject content = message.getJsonObject("content");
                    Content messageContent = parseContent(content);
                    promptMessages.add(new PromptMessage(role, messageContent));
                }
            }
            assertFunction.accept(new PromptResponse(description, promptMessages));
        }
    }

    record ToolsListAssert(JsonObject request, Consumer<ToolsPage> assertFunction) implements McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            JsonObject result = assertResultResponse(request, response);
            JsonArray tools = result.getJsonArray("tools");
            assertNotNull(tools);
            List<ToolInfo> infos = new ArrayList<>();
            for (int i = 0; i < tools.size(); i++) {
                JsonObject tool = tools.getJsonObject(i);
                infos.add(parseTool(tool));
            }
            assertFunction.accept(new ToolsPage(infos, result.getString("nextCursor")));
        }

        private ToolInfo parseTool(JsonObject tool) {
            Optional<ToolAnnotations> toolAnnotations;
            JsonObject annotations = tool.getJsonObject("annotations");
            if (annotations == null) {
                toolAnnotations = Optional.empty();
            } else {
                toolAnnotations = Optional.of(new ToolAnnotations(annotations.getString("title", ""),
                        annotations.getBoolean("readOnlyHint", false),
                        annotations.getBoolean("destructiveHint", true),
                        annotations.getBoolean("idempotentHint", false),
                        annotations.getBoolean("openWorldHint", true)));
            }
            return new ToolInfo(tool.getString("name"),
                    tool.getString("description"),
                    tool.getJsonObject("inputSchema"),
                    toolAnnotations);
        }
    }

    record ResourcesReadAssert(JsonObject request, Consumer<ResourceResponse> assertFunction)
            implements
                McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            JsonObject result = assertResultResponse(request, response);
            JsonArray contents = result.getJsonArray("contents");
            List<ResourceContents> resourceContents = new ArrayList<>();
            if (!contents.isEmpty()) {
                for (int i = 0; i < contents.size(); i++) {
                    resourceContents.add(parseResourceContents(contents.getJsonObject(i)));
                }
            }
            assertFunction.accept(new ResourceResponse(resourceContents));
        }

    }

    record ToolsCallAssert(JsonObject request, Consumer<ToolResponse> assertFunction)
            implements
                McpTestClientBase.ResponseAssert {

        @Override
        public void doAssert(JsonObject response) {
            JsonObject result = assertResultResponse(request, response);
            boolean isError = result.getBoolean("isError");
            JsonArray contentArray = result.getJsonArray("content");
            List<Content> content = new ArrayList<>(contentArray.size());
            for (int i = 0; i < contentArray.size(); i++) {
                content.add(parseContent(contentArray.getJsonObject(i)));
            }
            assertFunction.accept(new ToolResponse(isError, content));
        }
    }

}
