package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.CompletionManager;
import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.NotificationManager;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig.InvalidServerNameStrategy;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class McpMessageHandler<MCP_REQUEST extends McpRequest> {

    private static final Logger LOG = Logger.getLogger(McpMessageHandler.class);

    protected final ConnectionManager connectionManager;
    protected final PromptManagerImpl promptManager;
    protected final ToolManagerImpl toolManager;
    protected final ResourceManagerImpl resourceManager;
    protected final PromptCompletionManagerImpl promptCompletionManager;
    protected final ResourceTemplateManagerImpl resourceTemplateManager;
    protected final ResourceTemplateCompletionManagerImpl resourceTemplateCompletionManager;
    protected final NotificationManagerImpl notificationManager;

    private final ToolMessageHandler toolHandler;
    private final PromptMessageHandler promptHandler;
    private final PromptCompleteMessageHandler promptCompleteHandler;
    private final ResourceMessageHandler resourceHandler;
    private final ResourceTemplateMessageHandler resourceTemplateHandler;
    private final ResourceTemplateCompleteMessageHandler resourceTemplateCompleteHandler;

    private final ResponseHandlers responseHandlers;
    private final List<InitialCheck> initialChecks;

    protected final McpServersRuntimeConfig config;

    protected final Vertx vertx;

    private final Set<String> ongoingRequests;

    private final McpMetadata metadata;

    protected McpMessageHandler(McpServersRuntimeConfig config, ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager, ResourceManagerImpl resourceManager,
            PromptCompletionManagerImpl promptCompletionManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompletionManager,
            NotificationManagerImpl notificationManager,
            ResponseHandlers responseHandlers,
            McpMetadata metadata, Vertx vertx, List<InitialCheck> initialChecks) {
        this.connectionManager = connectionManager;
        this.promptManager = promptManager;
        this.toolManager = toolManager;
        this.resourceManager = resourceManager;
        this.resourceTemplateManager = resourceTemplateManager;
        this.promptCompletionManager = promptCompletionManager;
        this.resourceTemplateCompletionManager = resourceTemplateCompletionManager;
        this.toolHandler = new ToolMessageHandler(toolManager, config);
        this.promptHandler = new PromptMessageHandler(promptManager, config);
        this.promptCompleteHandler = new PromptCompleteMessageHandler(promptCompletionManager);
        this.resourceHandler = new ResourceMessageHandler(resourceManager, config);
        this.resourceTemplateHandler = new ResourceTemplateMessageHandler(resourceTemplateManager, config);
        this.resourceTemplateCompleteHandler = new ResourceTemplateCompleteMessageHandler(resourceTemplateCompletionManager);
        this.notificationManager = notificationManager;
        this.responseHandlers = responseHandlers;
        this.initialChecks = initialChecks;
        this.config = config;
        this.metadata = metadata;
        this.vertx = vertx;
        this.ongoingRequests = ConcurrentHashMap.newKeySet();

        if (config.invalidServerNameStrategy() == InvalidServerNameStrategy.FAIL) {
            validateServerConfigs();
        }
    }

    public Future<?> handle(MCP_REQUEST mcpRequest) {
        Object json = mcpRequest.json();
        if (json instanceof JsonObject message) {
            // Single request, notification, or response
            mcpRequest.messageReceived(message);
            if (JsonRPC.validate(message, mcpRequest.sender())) {
                return Messages.isResponse(message) ? handleResponse(message)
                        : handleRequest(message, mcpRequest);
            } else {
                jsonrpcValidationFailed(mcpRequest);
            }
        } else if (json instanceof JsonArray batch) {
            // Batch of messages
            if (!batch.isEmpty()) {
                List<Future<Void>> all = new ArrayList<>();
                if (Messages.isResponse(batch.getJsonObject(0))) {
                    // Batch of responses
                    for (Object e : batch) {
                        JsonObject response = (JsonObject) e;
                        mcpRequest.messageReceived(response);
                        if (JsonRPC.validate(response, mcpRequest.sender())) {
                            all.add(handleResponse(response));
                        } else {
                            jsonrpcValidationFailed(mcpRequest);
                        }
                    }
                } else {
                    // Batch of requests/notifications
                    for (Object e : batch) {
                        JsonObject requestOrNotification = (JsonObject) e;
                        mcpRequest.messageReceived(requestOrNotification);
                        if (JsonRPC.validate(requestOrNotification, mcpRequest.sender())) {
                            all.add(handleRequest(requestOrNotification, mcpRequest));
                        } else {
                            jsonrpcValidationFailed(mcpRequest);
                        }
                    }
                }
                return Future.join(all);
            }
        }
        return Future.failedFuture("Invalid jsonrpc message");
    }

    protected void jsonrpcValidationFailed(MCP_REQUEST mcpRequest) {
        // No-op
    }

    protected void initializeFailed(MCP_REQUEST mcpRequest) {
        // No-op
    }

    protected void afterInitialize(MCP_REQUEST mcpRequest) {
        // No-op
    }

    protected abstract InitialRequest.Transport transport();

    private Future<Void> handleResponse(JsonObject message) {
        return responseHandlers.handleResponse(message.getValue("id"), message);
    }

    private Future<Void> handleRequest(JsonObject message, MCP_REQUEST mcpRequest) {
        return switch (mcpRequest.connection().status()) {
            case NEW -> initializeNew(message, mcpRequest);
            case INITIALIZING -> initializing(message, mcpRequest);
            case IN_OPERATION -> operation(message, mcpRequest);
            case CLOSED -> mcpRequest.sender().send(
                    Messages.newError(message.getValue("id"), JsonRPC.INTERNAL_ERROR, "Connection is closed"));
        };
    }

    private McpServerRuntimeConfig serverConfig(MCP_REQUEST mcpRequest) {
        McpServerRuntimeConfig serverConfig = config.servers().get(mcpRequest.serverName());
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + mcpRequest.serverName());
        }
        return serverConfig;
    }

    private Future<Void> initializeNew(JsonObject message, MCP_REQUEST mcpRequest) {
        Object id = message.getValue("id");
        String method = message.getString("method");
        JsonObject params = message.getJsonObject("params");

        // The first message must be "initialize"
        // However, in the dev mode if an MCP client attempts to reconnect an SSE connection but does not reinitialize propertly,
        // we could perform a "dummy" initialization
        if (!INITIALIZE.equals(method)) {
            if (LaunchMode.current() == LaunchMode.DEVELOPMENT && serverConfig(mcpRequest).devMode().dummyInit()) {
                InitialRequest dummy = new InitialRequest(new Implementation("dummy", "1", null),
                        SUPPORTED_PROTOCOL_VERSIONS.get(0),
                        List.of(), transport());
                if (mcpRequest.connection().initialize(dummy) && mcpRequest.connection().setInitialized()) {
                    LOG.infof("Connection initialized with dummy info [%s]", mcpRequest.connection().id());
                    return operation(message, mcpRequest);
                }
            }

            String msg = "The first message from the client must be \"initialize\": " + method;
            initializeFailed(mcpRequest);
            return mcpRequest.sender().sendError(id, JsonRPC.METHOD_NOT_FOUND, msg);
        }

        if (params == null) {
            String msg = "Initialization params not found";
            initializeFailed(mcpRequest);
            return mcpRequest.sender().sendError(id, JsonRPC.INVALID_PARAMS, msg);
        }

        InitialRequest initialRequest = decodeInitializeRequest(params);
        // Start the context first
        mcpRequest.contextStart();
        // Then apply init checks
        return UniHelper.toFuture(checkInit(initialRequest, initialChecks, 0)).compose(res -> {
            if (res.error()) {
                // An init check failed - send the error message
                initializeFailed(mcpRequest);
                return mcpRequest.sender().sendError(id, JsonRPC.INTERNAL_ERROR, res.message());
            }
            // Init checks passed - attempt to initialize the connection
            if (mcpRequest.connection().initialize(initialRequest)) {
                // The server MUST respond with its own capabilities and information
                afterInitialize(mcpRequest);
                return mcpRequest.sender().sendResult(id, serverInfo(mcpRequest, initialRequest));
            } else {
                initializeFailed(mcpRequest);
                String msg = "Unable to initialize connection [connectionId: " + mcpRequest.connection().id() + "]";
                return mcpRequest.sender().sendError(id, JsonRPC.INTERNAL_ERROR, msg);
            }
        }).onComplete(r -> {
            mcpRequest.contextEnd();
        });
    }

    private static Uni<InitialCheck.CheckResult> checkInit(InitialRequest initialRequest, List<InitialCheck> checks, int idx) {
        if (checks.isEmpty()) {
            return InitialCheck.CheckResult.successs();
        }
        try {
            return checks.get(idx).perform(initialRequest).chain(res -> {
                if ((idx < checks.size() - 1)
                        && !res.error()) {
                    return checkInit(initialRequest, checks, idx + 1);
                }
                return Uni.createFrom().item(res);
            });
        } catch (Throwable t) {
            return Uni.createFrom().failure(t);
        }
    }

    private Future<Void> initializing(JsonObject message, McpRequest mcpRequest) {
        String method = message.getString("method");
        if (NOTIFICATIONS_INITIALIZED.equals(method)) {
            if (mcpRequest.connection().setInitialized()) {
                LOG.debugf("Client successfully initialized [%s]", mcpRequest.connection().id());
                // Call init methods asynchronously
                List<NotificationManager.NotificationInfo> infos = notificationManager.infosForRequest(mcpRequest)
                        .filter(n -> n.type() == Type.INITIALIZED).toList();
                if (!infos.isEmpty()) {
                    ArgumentProviders argProviders = new ArgumentProviders(message, Map.of(), mcpRequest.connection(), null,
                            null,
                            // For notifications/initialized we always use the connection as a sender
                            mcpRequest.connection(),
                            null, responseHandlers, mcpRequest.serverName());
                    FeatureExecutionContext featureExecutionContext = new FeatureExecutionContext(argProviders, mcpRequest);
                    for (NotificationManager.NotificationInfo notification : infos) {
                        callNotification(notification, featureExecutionContext);
                    }
                }
            }
            return Future.succeededFuture();
        } else if (PING.equals(method)) {
            return ping(message, mcpRequest);
        } else {
            return mcpRequest.sender().send(Messages.newError(message.getValue("id"), JsonRPC.INTERNAL_ERROR,
                    "Client not initialized yet [" + mcpRequest.connection().id() + "]"));
        }
    }

    private Future<Void> callNotification(NotificationManager.NotificationInfo notification,
            FeatureExecutionContext featureExecutionContext) {
        try {
            Future<Void> fu = notificationManager.execute(notificationManager.key(notification),
                    featureExecutionContext);
            return fu.onComplete(new Handler<AsyncResult<Void>>() {
                @Override
                public void handle(AsyncResult<Void> ar) {
                    if (ar.failed()) {
                        LOG.errorf(ar.cause(), "Unable to call notification method: %s", notification);
                    }
                }
            });
        } catch (McpException e) {
            LOG.errorf(e, "Unable to call notification method: %s", notification);
            throw e;
        }
    }

    public static final String INITIALIZE = "initialize";
    public static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    public static final String NOTIFICATIONS_MESSAGE = "notifications/message";
    public static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
    public static final String NOTIFICATIONS_CANCELLED = "notifications/cancelled";
    public static final String NOTIFICATIONS_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
    public static final String NOTIFICATIONS_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
    public static final String NOTIFICATIONS_PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";
    public static final String NOTIFICATIONS_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";
    public static final String PROMPTS_LIST = "prompts/list";
    public static final String PROMPTS_GET = "prompts/get";
    public static final String TOOLS_LIST = "tools/list";
    public static final String TOOLS_CALL = "tools/call";
    public static final String RESOURCES_LIST = "resources/list";
    public static final String RESOURCE_TEMPLATES_LIST = "resources/templates/list";
    public static final String RESOURCES_READ = "resources/read";
    public static final String RESOURCES_SUBSCRIBE = "resources/subscribe";
    public static final String RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
    public static final String PING = "ping";
    public static final String ROOTS_LIST = "roots/list";
    public static final String SAMPLING_CREATE_MESSAGE = "sampling/createMessage";
    public static final String ELICITATION_CREATE = "elicitation/create";
    public static final String COMPLETION_COMPLETE = "completion/complete";
    public static final String LOGGING_SET_LEVEL = "logging/setLevel";
    // non-standard messages
    public static final String Q_CLOSE = "q/close";

    private Future<Void> operation(JsonObject message, McpRequest mcpRequest) {
        // Create a new duplicated context and process the operation on this context
        Context context = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);
        Promise<Void> ret = Promise.promise();
        String ongoingId = ongoingId(message, mcpRequest);
        if (ongoingId != null) {
            ongoingRequests.add(ongoingId);
        }

        context.runOnContext(v -> {
            mcpRequest.contextStart();
            String method = message.getString("method");
            Future<?> future = switch (method) {
                case PROMPTS_LIST -> promptHandler.promptsList(message, mcpRequest);
                case PROMPTS_GET -> promptHandler.promptsGet(message, mcpRequest);
                case TOOLS_LIST -> toolHandler.toolsList(message, mcpRequest);
                case TOOLS_CALL -> toolHandler.toolsCall(message, mcpRequest);
                case PING -> ping(message, mcpRequest);
                case RESOURCES_LIST -> resourceHandler.resourcesList(message, mcpRequest);
                case RESOURCES_READ -> resourceHandler.resourcesRead(message, mcpRequest);
                case RESOURCES_SUBSCRIBE -> resourceHandler.resourcesSubscribe(message, mcpRequest);
                case RESOURCES_UNSUBSCRIBE -> resourceHandler.resourcesUnsubscribe(message, mcpRequest);
                case RESOURCE_TEMPLATES_LIST -> resourceTemplateHandler.resourceTemplatesList(message, mcpRequest);
                case COMPLETION_COMPLETE -> complete(message, mcpRequest);
                case LOGGING_SET_LEVEL -> setLogLevel(message, mcpRequest);
                case Q_CLOSE -> close(message, mcpRequest);
                case NOTIFICATIONS_ROOTS_LIST_CHANGED -> rootsListChanged(message, mcpRequest);
                case NOTIFICATIONS_CANCELLED -> cancelRequest(message, mcpRequest);
                default -> mcpRequest.sender().send(
                        Messages.newError(message.getValue("id"), JsonRPC.METHOD_NOT_FOUND, "Unsupported method: " + method));
            };
            future.onComplete(r -> {
                mcpRequest.contextEnd();
                if (ongoingId != null) {
                    ongoingRequests.remove(ongoingId);
                    mcpRequest.connection().removeCancellationRequest(message);
                }
                if (r.failed()) {
                    ret.fail(r.cause());
                } else {
                    ret.complete();
                }
            });
        });
        return ret.future();
    }

    private Future<Void> rootsListChanged(JsonObject message, McpRequest mcpRequest) {
        List<NotificationManager.NotificationInfo> infos = notificationManager.infosForRequest(mcpRequest)
                .filter(n -> n.type() == Type.ROOTS_LIST_CHANGED).toList();
        if (!infos.isEmpty()) {
            ArgumentProviders argProviders = new ArgumentProviders(message, Map.of(), mcpRequest.connection(), null, null,
                    mcpRequest.sender(), null, responseHandlers, mcpRequest.serverName());
            FeatureExecutionContext featureExecutionContext = new FeatureExecutionContext(argProviders, mcpRequest);
            for (NotificationManager.NotificationInfo notification : infos) {
                callNotification(notification, featureExecutionContext);
            }
        }
        return Future.succeededFuture();

    }

    private Future<Void> cancelRequest(JsonObject message, McpRequest mcpRequest) {
        JsonObject params = message.getJsonObject("params");
        if (params != null) {
            Object requestId = params.getValue("requestId");
            // Unknown, completed and invalid requests should be just ignored
            if (requestId != null
                    && ongoingRequests.contains(ongoingId(requestId, mcpRequest))
                    && mcpRequest.connection().addCancellationRequest(new RequestId(requestId), params.getString("reason"))) {
                String reason = params.getString("reason");
                LOG.debugf("Cancel request with id %s: %s [%s]", requestId, reason != null ? reason : "no reason",
                        mcpRequest.connection().id());
            } else {
                LOG.warnf("Ignored unknown/completed/invalid cancel request with id %s [%s]", requestId,
                        mcpRequest.connection().id());
            }
        }
        return Future.succeededFuture();
    }

    private String ongoingId(JsonObject message, McpRequest mcpRequest) {
        return ongoingId(Messages.isRequest(message) ? message.getValue("id") : null, mcpRequest);
    }

    private String ongoingId(Object requestId, McpRequest mcpRequest) {
        if (requestId != null) {
            return requestId + "::" + mcpRequest.connection().id();
        } else {
            return null;
        }
    }

    private Future<Void> setLogLevel(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String level = params.getString("level");
        if (level == null) {
            return mcpRequest.sender().sendError(id, JsonRPC.INVALID_REQUEST, "Log level not set");
        } else {
            LogLevel logLevel = LogLevel.from(level);
            if (logLevel == null) {
                return mcpRequest.sender().sendError(id, JsonRPC.INVALID_REQUEST, "Invalid log level set: " + level);
            } else {
                mcpRequest.connection().setLogLevel(logLevel);
                // Send empty result
                return mcpRequest.sender().sendResult(id, new JsonObject());
            }
        }

    }

    private Future<Void> complete(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        JsonObject ref = params.getJsonObject("ref");
        if (ref == null) {
            return mcpRequest.sender().sendError(id, JsonRPC.INVALID_REQUEST, "Reference not found");
        } else {
            String referenceType = ref.getString("type");
            if (referenceType == null) {
                return mcpRequest.sender().sendError(id, JsonRPC.INVALID_REQUEST, "Reference type not found");
            } else {
                JsonObject argument = params.getJsonObject("argument");
                if (argument == null) {
                    return mcpRequest.sender().sendError(id, JsonRPC.INVALID_REQUEST, "Argument not found");
                } else {
                    if ("ref/prompt".equals(referenceType)) {
                        return promptCompleteHandler.complete(message, id, ref, argument, mcpRequest.sender(), mcpRequest);
                    } else if ("ref/resource".equals(referenceType)) {
                        return resourceTemplateCompleteHandler.complete(message, id, ref, argument, mcpRequest.sender(),
                                mcpRequest);
                    } else {
                        return mcpRequest.sender().sendError(id, JsonRPC.INVALID_REQUEST,
                                "Unsupported reference found: " + ref.getString("type"));
                    }
                }
            }
        }
    }

    private Future<Void> ping(JsonObject message, McpRequest mcpRequest) {
        Object id = message.getValue("id");
        LOG.debugf("Ping [id: %s]", id);
        return mcpRequest.sender().sendResult(id, new JsonObject());
    }

    private Future<Void> close(JsonObject message, McpRequest mcpRequest) {
        if (connectionManager.remove(mcpRequest.connection().id())) {
            LOG.debugf("Connection %s explicitly closed ", mcpRequest.connection().id());
            return Future.succeededFuture();
        } else {
            return mcpRequest.sender().sendError(message.getValue("id"), JsonRPC.INTERNAL_ERROR,
                    "Unable to obtain the connection to be closed:" + mcpRequest.connection().id());
        }
    }

    private InitialRequest decodeInitializeRequest(JsonObject params) {
        JsonObject clientInfo = params.getJsonObject("clientInfo");
        Implementation implementation = new Implementation(clientInfo.getString("name"), clientInfo.getString("version"),
                clientInfo.getString("title"));
        String protocolVersion = params.getString("protocolVersion");
        List<ClientCapability> clientCapabilities = new ArrayList<>();
        JsonObject capabilities = params.getJsonObject("capabilities");
        if (capabilities != null) {
            for (String name : capabilities.fieldNames()) {
                // TODO capability properties
                clientCapabilities.add(new ClientCapability(name, Map.of()));
            }
        }
        return new InitialRequest(implementation, protocolVersion, List.copyOf(clientCapabilities), transport());
    }

    private static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");

    private Map<String, Object> serverInfo(MCP_REQUEST mcpRequest, InitialRequest initialRequest) {
        Map<String, Object> info = new HashMap<>();

        // Note that currently the protocol version does not affect the behavior of the server in any way
        String version = SUPPORTED_PROTOCOL_VERSIONS.get(0);
        if (SUPPORTED_PROTOCOL_VERSIONS.contains(initialRequest.protocolVersion())) {
            version = initialRequest.protocolVersion();
        }
        info.put("protocolVersion", version);

        McpServerRuntimeConfig serverConfig = serverConfig(mcpRequest);
        String serverName = serverConfig.serverInfo().name()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.name", String.class)
                        .orElse("N/A"));
        String serverVersion = serverConfig.serverInfo().version()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class)
                        .orElse("N/A"));
        String serverTitle = serverConfig.serverInfo().title().orElse(serverName);
        info.put("serverInfo", Map.of("name", serverName, "version", serverVersion, "title", serverTitle));

        Map<String, Map<String, Object>> capabilities = new HashMap<>();
        if (promptManager.hasInfos(mcpRequest)) {
            capabilities.put("prompts", metadata.isPromptManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        if (toolManager.hasInfos(mcpRequest)) {
            capabilities.put("tools", metadata.isToolManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        if (resourceManager.hasInfos(mcpRequest)
                || resourceTemplateManager.hasInfos(mcpRequest)) {
            capabilities.put("resources", metadata.isResourceManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        if (promptCompletionManager.hasInfos(mcpRequest)
                || resourceTemplateCompletionManager.hasInfos(mcpRequest)) {
            capabilities.put("completions", Map.of());
        }
        capabilities.put("logging", Map.of());
        info.put("capabilities", capabilities);
        return info;
    }

    private void validateServerConfigs() {
        List<FeatureInfo> invalid = new ArrayList<>();
        Set<String> serverNames = new HashSet<>(metadata.serverNames());
        serverNames.addAll(config.servers().keySet());
        for (ToolManager.ToolInfo info : toolManager) {
            if (!serverNames.contains(info.serverName())) {
                invalid.add(info);
            }
        }
        for (PromptManager.PromptInfo info : promptManager) {
            if (!serverNames.contains(info.serverName())) {
                invalid.add(info);
            }
        }
        for (ResourceManager.ResourceInfo info : resourceManager) {
            if (!serverNames.contains(info.serverName())) {
                invalid.add(info);
            }
        }
        for (ResourceTemplateManager.ResourceTemplateInfo info : resourceTemplateManager) {
            if (!serverNames.contains(info.serverName())) {
                invalid.add(info);
            }
        }
        for (NotificationManager.NotificationInfo info : notificationManager) {
            if (!serverNames.contains(info.serverName())) {
                invalid.add(info);
            }
        }
        for (CompletionManager.CompletionInfo info : promptCompletionManager) {
            if (!serverNames.contains(info.serverName())) {
                invalid.add(info);
            }
        }
        for (CompletionManager.CompletionInfo info : resourceTemplateCompletionManager) {
            if (!serverNames.contains(info.serverName())) {
                invalid.add(info);
            }
        }

        if (!invalid.isEmpty()) {
            IllegalStateException ise = new IllegalStateException("Invalid server name");
            for (FeatureInfo info : invalid) {
                ise.addSuppressed(new IllegalStateException(
                        String.format("Invalid server name [%s] used for: %s", info.serverName(), info)));
            }
            throw ise;
        }
    }

}
