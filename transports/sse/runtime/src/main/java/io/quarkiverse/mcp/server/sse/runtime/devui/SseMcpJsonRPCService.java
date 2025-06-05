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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.CompletionManager;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateCompletionManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.sse.client.SseClient;
import io.quarkiverse.mcp.server.sse.runtime.config.McpSseServerBuildTimeConfig;
import io.quarkiverse.mcp.server.sse.runtime.config.McpSseServersBuildTimeConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class SseMcpJsonRPCService {

    private static final Logger LOG = Logger.getLogger(SseMcpJsonRPCService.class);

    private final ToolManager toolManager;
    private final PromptManager promptManager;
    private final PromptCompletionManager promptCompletionManager;
    private final ResourceManager resourceManager;
    private final ResourceTemplateManager resourceTemplateManager;
    private final ResourceTemplateCompletionManager resourceTemplateCompletionManager;

    private final Map<String, ServerClient> serverClients;

    record ServerClient(URI sseEndpoint, DevUIClient client, HttpClient httpClient) {

        public ServerClient(URI sseEndpoint, HttpClient httpClient) {
            this(sseEndpoint, new DevUIClient(sseEndpoint, httpClient), httpClient);
        }
    }

    public SseMcpJsonRPCService(ToolManager toolManager, PromptManager promptManager, ResourceManager resourceManager,
            ResourceTemplateManager resourceTemplateManager, PromptCompletionManager promptCompletionManager,
            ResourceTemplateCompletionManager resourceTemplateCompletionManager,
            @ConfigProperty(name = "quarkus.http.host") String host, @ConfigProperty(name = "quarkus.http.port") int port,
            @ConfigProperty(name = "quarkus.http.root-path") String rootPath, McpSseServersBuildTimeConfig config) {
        this.toolManager = toolManager;
        this.promptManager = promptManager;
        this.promptCompletionManager = promptCompletionManager;
        this.resourceManager = resourceManager;
        this.resourceTemplateManager = resourceTemplateManager;
        this.resourceTemplateCompletionManager = resourceTemplateCompletionManager;
        this.serverClients = new HashMap<>();
        for (Entry<String, McpSseServerBuildTimeConfig> e : config.servers().entrySet()) {
            serverClients.put(e.getKey(), new ServerClient(URI.create(new StringBuilder("http://")
                    .append(host)
                    .append(":")
                    .append(port)
                    .append(rootPath)
                    .append(pathToAppend(rootPath, e.getValue().rootPath()))
                    .append(pathToAppend(e.getValue().rootPath(), "sse")).toString()),
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build()));
        }
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
            ret.add(resource.asJson().put("serverName", resource.serverName()));
        }
        return ret;
    }

    public JsonArray getResourceTemplatesData() {
        JsonArray ret = new JsonArray();
        for (ResourceTemplateManager.ResourceTemplateInfo resourceTemplate : resourceTemplateManager) {
            ret.add(resourceTemplate.asJson()
                    .put("serverName", resourceTemplate.serverName()));
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

    public JsonObject callTool(String name, JsonObject args, String bearerToken, boolean forceNewSession)
            throws IOException, InterruptedException {
        ToolManager.ToolInfo info = toolManager.getTool(name);
        if (info == null) {
            return new JsonObject().put("error", "Tool not found: " + name);
        }
        ServerClient serverClient = serverClients.get(info.serverName());
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("method", "tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", args));
        return serverClient.client().sendRequest(message, bearerToken, forceNewSession);
    }

    public JsonObject getPrompt(String name, JsonObject args, String bearerToken, boolean forceNewSession)
            throws IOException, InterruptedException {
        PromptManager.PromptInfo info = promptManager.getPrompt(name);
        if (info == null) {
            return new JsonObject().put("error", "Prompt not found: " + name);
        }
        ServerClient serverClient = serverClients.get(info.serverName());
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("method", "prompts/get")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", args));
        return serverClient.client().sendRequest(message, bearerToken, forceNewSession);
    }

    public JsonObject completePrompt(String name, String argumentName, String argumentValue, String bearerToken,
            boolean forceNewSession)
            throws IOException, InterruptedException {
        CompletionManager.CompletionInfo info = promptCompletionManager.getCompletion(name, argumentName);
        if (info == null) {
            return new JsonObject().put("error", "Prompt completion not found: " + name);
        }
        ServerClient serverClient = serverClients.get(info.serverName());
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("method", "completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", name))
                        .put("argument", new JsonObject()
                                .put("name", argumentName)
                                .put("value", argumentValue)));
        return serverClient.client().sendRequest(message, bearerToken, forceNewSession);
    }

    public JsonObject readResource(String serverName, String uri, String bearerToken, boolean forceNewSession)
            throws IOException, InterruptedException {
        if (serverName == null || serverName.isBlank()) {
            return new JsonObject().put("error", "Server name must be set");
        }
        if (uri == null || uri.isBlank()) {
            return new JsonObject().put("error", "Resource uri must be set");
        }
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("method", "resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));
        return serverClients.get(serverName).client().sendRequest(message, bearerToken, forceNewSession);
    }

    public JsonObject completeResourceTemplate(String name, String argumentName, String argumentValue, String bearerToken,
            boolean forceNewSession)
            throws IOException, InterruptedException {
        CompletionManager.CompletionInfo info = resourceTemplateCompletionManager.getCompletion(name, argumentName);
        if (info == null) {
            return new JsonObject().put("error", "Resource template completion not found: " + name);
        }
        ServerClient serverClient = serverClients.get(info.serverName());
        JsonObject message = new JsonObject()
                .put("jsonrpc", JsonRPC.VERSION)
                .put("method", "completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/resource")
                                .put("name", name))
                        .put("argument", new JsonObject()
                                .put("name", argumentName)
                                .put("value", argumentValue)));

        return serverClient.client().sendRequest(message, bearerToken, forceNewSession);
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

    static class DevUIClient {

        private final URI sseEndpoint;

        private final HttpClient httpClient;

        private final Lock lock = new ReentrantLock();

        private final AtomicInteger idGenerator = new AtomicInteger();

        private volatile DevUISseClient sseClient;

        private volatile CompletableFuture<HttpResponse<Void>> sseFuture;

        private final AtomicReference<URI> messageEndpoint = new AtomicReference<>();

        DevUIClient(URI sseEndpoint, HttpClient httpClient) {
            this.sseEndpoint = sseEndpoint;
            this.httpClient = httpClient;
        }

        JsonObject sendRequest(JsonObject message, String bearerToken, boolean forceNewSession)
                throws InterruptedException, IOException {
            lock.lock();
            try {
                boolean init = sseClient == null || forceNewSession;
                if (init) {
                    if (sseFuture != null) {
                        try {
                            sseFuture.cancel(true);
                        } catch (Throwable e) {
                            LOG.warnf(e, "Unable to close the SSE connection");
                        }
                    }
                    sseClient = new DevUISseClient(sseEndpoint, me -> messageEndpoint.set(me));
                    Map<String, String> headers = new HashMap<>();
                    if (bearerToken != null && !bearerToken.isBlank()) {
                        headers.put("Authorization", "Bearer " + bearerToken);
                    }
                    sseFuture = sseClient.connect(httpClient, headers);
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

                    HttpRequest request = newRequest(bearerToken, initMessage.encode());
                    HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
                    if (response.statusCode() != 200) {
                        return new JsonObject().put("error", "Init failed with invalid HTTP status: " + response.statusCode());
                    }
                    sseClient.awaitResponse(initId);

                    // Send "notifications/initialized"
                    JsonObject nofitication = new JsonObject()
                            .put("jsonrpc", JsonRPC.VERSION)
                            .put("method", "notifications/initialized");
                    request = newRequest(bearerToken, nofitication.encode());
                    response = httpClient.send(request, BodyHandlers.discarding());
                    if (response.statusCode() != 200) {
                        return new JsonObject().put("error",
                                "Init notification failed with invalid HTTP status: " + response.statusCode());
                    }
                }

                Integer requestId = idGenerator.incrementAndGet();
                message.put("id", requestId);
                HttpRequest request = newRequest(bearerToken, message.encode());
                HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
                if (response.statusCode() != 200) {
                    return new JsonObject().put("error", "Invalid HTTP status: " + response.statusCode());
                }
                // Wait for the response
                return new JsonObject().put("response", sseClient.awaitResponse(requestId));
            } finally {
                lock.unlock();
            }
        }

        private HttpRequest newRequest(String bearerToken, String body) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(messageEndpoint.get())
                    .version(Version.HTTP_1_1)
                    .POST(BodyPublishers.ofString(body));
            if (bearerToken != null && !bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            return builder.build();
        }

    }

    static class DevUISseClient extends SseClient {

        private static final int AWAIT_ATTEMPTS = 50;
        private static final int AWAIT_SLEEP = 100; // ms

        private final CountDownLatch endpointLatch = new CountDownLatch(1);

        private final Consumer<URI> messageEndpointConsumer;

        private final ConcurrentMap<Integer, JsonObject> responses;

        public DevUISseClient(URI sseUri, Consumer<URI> messageEndpointConsumer) {
            super(sseUri);
            this.responses = new ConcurrentHashMap<Integer, JsonObject>();
            this.messageEndpointConsumer = messageEndpointConsumer;
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
                messageEndpointConsumer.accept(connectUri.resolve(endpoint));
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
