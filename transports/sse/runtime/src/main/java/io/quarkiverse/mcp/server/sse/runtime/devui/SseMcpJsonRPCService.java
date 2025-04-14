package io.quarkiverse.mcp.server.sse.runtime.devui;

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.CompletionManager;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateCompletionManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.sse.client.SseClient;
import io.quarkiverse.mcp.server.sse.runtime.config.McpSseBuildTimeConfig;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.VertxHttpConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class SseMcpJsonRPCService {

    private final ToolManager toolManager;
    private final PromptManager promptManager;
    private final PromptCompletionManager promptCompletionManager;
    private final ResourceManager resourceManager;
    private final ResourceTemplateManager resourceTemplateManager;
    private final ResourceTemplateCompletionManager resourceTemplateCompletionManager;

    private final DevUISseClient sseClient;
    private final AtomicReference<URI> messageEndpoint;
    private final HttpClient httpClient;
    private final AtomicBoolean initialized;
    private final AtomicInteger idGenerator;

    public SseMcpJsonRPCService(ToolManager toolManager, PromptManager promptManager, ResourceManager resourceManager,
            ResourceTemplateManager resourceTemplateManager, PromptCompletionManager promptCompletionManager,
            ResourceTemplateCompletionManager resourceTemplateCompletionManager, VertxHttpConfig httpConfig,
            VertxHttpBuildTimeConfig httpBuildConfig, McpSseBuildTimeConfig mcpSseBuildConfig) {
        this.toolManager = toolManager;
        this.promptManager = promptManager;
        this.promptCompletionManager = promptCompletionManager;
        this.resourceManager = resourceManager;
        this.resourceTemplateManager = resourceTemplateManager;
        this.resourceTemplateCompletionManager = resourceTemplateCompletionManager;
        this.sseClient = new DevUISseClient(URI.create(new StringBuilder("http://")
                .append(httpConfig.host())
                .append(":")
                .append(httpConfig.port())
                .append(httpBuildConfig.rootPath())
                .append(pathToAppend(httpBuildConfig.rootPath(), mcpSseBuildConfig.rootPath()))
                .append(pathToAppend(mcpSseBuildConfig.rootPath(), "sse")).toString()));
        this.messageEndpoint = new AtomicReference<>();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.initialized = new AtomicBoolean();
        this.idGenerator = new AtomicInteger();
    }

    public JsonArray getToolsData() {
        JsonArray ret = new JsonArray();
        for (ToolManager.ToolInfo tool : toolManager) {
            JsonObject toolJson = tool.asJson();
            // The inputSchema contains the same information but we add this to simplify the UI
            if (!tool.arguments().isEmpty()) {
                JsonArray args = new JsonArray();
                for (ToolManager.ToolArgument arg : tool.arguments()) {
                    JsonObject argJson = new JsonObject();
                    argJson.put("name", arg.name());
                    argJson.put("description", arg.description());
                    argJson.put("required", arg.required());
                    argJson.put("type", arg.type().getTypeName());
                    args.add(argJson);
                }
                toolJson.put("args", args);
            }
            // The prototype showed in tools/call UI
            toolJson.put("inputPrototype", createInputPrototype(tool));
            ret.add(toolJson);
        }
        return ret;
    }

    public JsonArray getPromptsData() {
        JsonArray ret = new JsonArray();
        for (PromptManager.PromptInfo prompt : promptManager) {
            JsonObject promptJson = prompt.asJson();
            JsonObject inputPrototype = new JsonObject();
            for (PromptManager.PromptArgument arg : prompt.arguments()) {
                inputPrototype.put(arg.name(), arg.description());
            }
            promptJson.put("inputPrototype", inputPrototype);
            ret.add(promptJson);
        }
        return ret;
    }

    public JsonArray getPromptCompletionsData() {
        JsonArray ret = new JsonArray();
        for (CompletionManager.CompletionInfo completion : promptCompletionManager) {
            JsonObject completionJson = new JsonObject();
            completionJson.put("name", completion.name());
            completionJson.put("argumentName", completion.argumentName());
            ret.add(completionJson);
        }
        return ret;
    }

    public JsonArray getResourcesData() {
        JsonArray ret = new JsonArray();
        for (ResourceManager.ResourceInfo resource : resourceManager) {
            ret.add(resource.asJson());
        }
        return ret;
    }

    public JsonArray getResourceTemplatesData() {
        JsonArray ret = new JsonArray();
        for (ResourceTemplateManager.ResourceTemplateInfo resourceTemplate : resourceTemplateManager) {
            ret.add(resourceTemplate.asJson());
        }
        return ret;
    }

    public JsonArray getResourceTemplateCompletionsData() {
        JsonArray ret = new JsonArray();
        for (CompletionManager.CompletionInfo completion : resourceTemplateCompletionManager) {
            JsonObject completionJson = new JsonObject();
            completionJson.put("name", completion.name());
            completionJson.put("argumentName", completion.argumentName());
            ret.add(completionJson);
        }
        return ret;
    }

    private JsonObject createInputPrototype(ToolManager.ToolInfo tool) {
        JsonObject inputPrototype = new JsonObject();
        if (!tool.arguments().isEmpty()) {
            for (ToolManager.ToolArgument arg : tool.arguments()) {
                if (arg.type() instanceof Class<?> clazz) {
                    if (clazz.isPrimitive()) {
                        if (int.class.equals(clazz)
                                || double.class.equals(clazz)
                                || float.class.equals(clazz)
                                || byte.class.equals(clazz)) {
                            inputPrototype.put(arg.name(), 42);
                        } else if (boolean.class.equals(clazz)) {
                            inputPrototype.put(arg.name(), true);
                        } else {
                            unsupportedType(inputPrototype, arg);
                        }
                    } else if (String.class.equals(arg.type())) {
                        inputPrototype.put(arg.name(), arg.description());
                    } else if (clazz.isAssignableFrom(Number.class)) {
                        inputPrototype.put(arg.name(), 42);
                    } else if (Boolean.class.equals(clazz)) {
                        inputPrototype.put(arg.name(), true);
                    } else {
                        unsupportedType(inputPrototype, arg);
                    }
                } else if (arg.type() instanceof ParameterizedType pt) {
                    if (pt.getRawType() instanceof Class<?> clazz && Collection.class.isAssignableFrom(clazz)) {
                        inputPrototype.put(arg.name(), List.of());
                    } else {
                        unsupportedType(inputPrototype, arg);
                    }
                } else if (arg.type() instanceof GenericArrayType) {
                    inputPrototype.put(arg.name(), List.of());
                } else {
                    unsupportedType(inputPrototype, arg);
                }
            }
        }
        return inputPrototype;
    }

    private void unsupportedType(JsonObject inputPrototype, ToolManager.ToolArgument arg) {
        inputPrototype.put(arg.name(), arg.type().getTypeName() + ": " + arg.description());
    }

    public JsonObject callTool(String name, JsonObject args) throws IOException, InterruptedException {
        if (toolManager.getTool(name) == null) {
            return new JsonObject().put("error", "Tool not found: " + name);
        }
        JsonObject initRet = ensureInitialized();
        if (initRet != null) {
            return initRet;
        }
        Integer requestId = idGenerator.incrementAndGet();
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("id", requestId)
                .put("method", "tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", args));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(messageEndpoint.get())
                .version(Version.HTTP_1_1)
                .POST(BodyPublishers.ofString(message.encode()))
                .build();
        HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            return new JsonObject().put("error", "Invalid HTTP status: " + response.statusCode());
        }
        // Wait for the response
        return new JsonObject().put("response", sseClient.awaitResponse(requestId));
    }

    public JsonObject getPrompt(String name, JsonObject args) throws IOException, InterruptedException {
        if (promptManager.getPrompt(name) == null) {
            return new JsonObject().put("error", "Prompt not found: " + name);
        }
        JsonObject initRet = ensureInitialized();
        if (initRet != null) {
            return initRet;
        }
        Integer requestId = idGenerator.incrementAndGet();
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("id", requestId)
                .put("method", "prompts/get")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", args));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(messageEndpoint.get())
                .version(Version.HTTP_1_1)
                .POST(BodyPublishers.ofString(message.encode()))
                .build();
        HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            return new JsonObject().put("error", "Invalid HTTP status: " + response.statusCode());
        }
        // Wait for the response
        return new JsonObject().put("response", sseClient.awaitResponse(requestId));
    }

    public JsonObject completePrompt(String name, String argumentName, String argumentValue)
            throws IOException, InterruptedException {
        if (promptCompletionManager.getCompletion(name, argumentName) == null) {
            return new JsonObject().put("error", "Prompt completion not found: " + name);
        }
        JsonObject initRet = ensureInitialized();
        if (initRet != null) {
            return initRet;
        }
        Integer requestId = idGenerator.incrementAndGet();
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("id", requestId)
                .put("method", "completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", name))
                        .put("argument", new JsonObject()
                                .put("name", argumentName)
                                .put("value", argumentValue)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(messageEndpoint.get())
                .version(Version.HTTP_1_1)
                .POST(BodyPublishers.ofString(message.encode()))
                .build();
        HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            return new JsonObject().put("error", "Invalid HTTP status: " + response.statusCode());
        }
        // Wait for the response
        return new JsonObject().put("response", sseClient.awaitResponse(requestId));
    }

    public JsonObject readResource(String uri) throws IOException, InterruptedException {
        if (uri == null || uri.isBlank()) {
            return new JsonObject().put("error", "Resource uri must be set");
        }
        JsonObject initRet = ensureInitialized();
        if (initRet != null) {
            return initRet;
        }
        Integer requestId = idGenerator.incrementAndGet();
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("id", requestId)
                .put("method", "resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(messageEndpoint.get())
                .version(Version.HTTP_1_1)
                .POST(BodyPublishers.ofString(message.encode()))
                .build();
        HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            return new JsonObject().put("error", "Invalid HTTP status: " + response.statusCode());
        }
        // Wait for the response
        return new JsonObject().put("response", sseClient.awaitResponse(requestId));
    }

    public JsonObject completeResourceTemplate(String name, String argumentName, String argumentValue)
            throws IOException, InterruptedException {
        if (resourceTemplateCompletionManager.getCompletion(name, argumentName) == null) {
            return new JsonObject().put("error", "Resource template completion not found: " + name);
        }
        JsonObject initRet = ensureInitialized();
        if (initRet != null) {
            return initRet;
        }
        Integer requestId = idGenerator.incrementAndGet();
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("id", requestId)
                .put("method", "completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/resource")
                                .put("name", name))
                        .put("argument", new JsonObject()
                                .put("name", argumentName)
                                .put("value", argumentValue)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(messageEndpoint.get())
                .version(Version.HTTP_1_1)
                .POST(BodyPublishers.ofString(message.encode()))
                .build();
        HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            return new JsonObject().put("error", "Invalid HTTP status: " + response.statusCode());
        }
        // Wait for the response
        return new JsonObject().put("response", sseClient.awaitResponse(requestId));
    }

    private JsonObject ensureInitialized() throws InterruptedException, IOException {
        if (initialized.compareAndSet(false, true)) {
            sseClient.connect(httpClient, Map.of());
            sseClient.awaitEndpoint();

            Integer initId = idGenerator.incrementAndGet();
            JsonObject initMessage = new JsonObject()
                    .put("jsonrpc", JsonRPC.VERSION)
                    .put("id", initId)
                    .put("method", "initialize")
                    .put("params",
                            new JsonObject()
                                    .put("clientInfo", new JsonObject()
                                            .put("name", "devui-client")
                                            .put("version", "1.0"))
                                    .put("protocolVersion", "2024-11-05"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(messageEndpoint.get())
                    .version(Version.HTTP_1_1)
                    .POST(BodyPublishers.ofString(initMessage.encode()))
                    .build();
            HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                return new JsonObject().put("error", "Init failed with invalid HTTP status: " + response.statusCode());
            }
            sseClient.awaitResponse(initId);

            // Send "notifications/initialized"
            JsonObject nofitication = new JsonObject()
                    .put("jsonrpc", JsonRPC.VERSION)
                    .put("method", "notifications/initialized");
            request = HttpRequest.newBuilder()
                    .uri(messageEndpoint.get())
                    .version(Version.HTTP_1_1)
                    .POST(BodyPublishers.ofString(nofitication.encode()))
                    .build();
            response = httpClient.send(request, BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                return new JsonObject().put("error",
                        "Init notification failed with invalid HTTP status: " + response.statusCode());
            }
        }
        return null;
    }

    private String pathToAppend(String prev, String path) {
        if (prev.endsWith("/")) {
            if (path.startsWith("/")) {
                return path.substring(1);
            } else {
                return path;
            }
        } else {
            if (path.startsWith("/")) {
                return path;
            } else {
                return "/" + path;
            }
        }
    }

    class DevUISseClient extends SseClient {

        private static final int AWAIT_ATTEMPTS = 50;
        private static final int AWAIT_SLEEP = 100; // ms

        private final CountDownLatch endpointLatch = new CountDownLatch(1);

        private final ConcurrentMap<Integer, JsonObject> responses;

        public DevUISseClient(URI sseUri) {
            super(sseUri);
            this.responses = new ConcurrentHashMap<Integer, JsonObject>();
        }

        void awaitEndpoint() throws InterruptedException {
            if (!endpointLatch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Endpoint not received");
            }
        }

        JsonObject awaitResponse(Integer id) throws InterruptedException {
            JsonObject response = responses.get(id);
            int attempts = 0;
            while (response == null && attempts++ < AWAIT_ATTEMPTS) {
                TimeUnit.MILLISECONDS.sleep(AWAIT_SLEEP);
                response = responses.remove(id);
            }
            return response;
        }

        @Override
        protected void process(SseEvent event) {
            if ("endpoint".equals(event.name())) {
                String endpoint = event.data().strip();
                messageEndpoint.set(connectUri.resolve(endpoint));
                endpointLatch.countDown();
            } else if ("message".equals(event.name())) {
                JsonObject json = new JsonObject(event.data());
                Integer id = json.getInteger("id");
                if (id != null
                        && (json.containsKey("result") || json.containsKey("error"))) {
                    // Requests from the server and notifications are just ignored for now
                    responses.put(id, json);
                }
            }
        }

    }

}
