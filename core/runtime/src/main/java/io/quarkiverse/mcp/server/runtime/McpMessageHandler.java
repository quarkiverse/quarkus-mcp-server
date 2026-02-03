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
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.NotificationManager;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig.Icon;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig.ServerInfo;
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
            if (JsonRpc.validate(message, mcpRequest.sender())) {
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
                        if (JsonRpc.validate(response, mcpRequest.sender())) {
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
                        if (JsonRpc.validate(requestOrNotification, mcpRequest.sender())) {
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
        return responseHandlers.handleResponse(Messages.getId(message), message);
    }

    private Future<Void> handleRequest(JsonObject message, MCP_REQUEST mcpRequest) {
        return switch (mcpRequest.connection().status()) {
            case NEW -> initializeNew(message, mcpRequest);
            case INITIALIZING -> initializing(message, mcpRequest);
            case IN_OPERATION -> operation(message, mcpRequest);
            case CLOSED -> mcpRequest.sender().sendError(Messages.getId(message), JsonRpcErrorCodes.INTERNAL_ERROR,
                    "Connection is closed");
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
        Object id = Messages.getId(message);
        McpMethod method = McpMethod.from(message.getString("method"));
        JsonObject params = Messages.getParams(message);

        if (McpMethod.INITIALIZE != method) {
            // Normally the first message must be "initialize"

            // However, we could create a synthetic initial request and perform a dummy initialization
            InitialRequest dummy = null;
            if (LaunchMode.current() == LaunchMode.DEVELOPMENT
                    && serverConfig(mcpRequest).devMode().dummyInit()) {
                // In the dev mode, if an MCP client attempts to reconnect an SSE connection but does not reinitialize properly
                dummy = new InitialRequest(new Implementation("dummy", "1", null),
                        SUPPORTED_PROTOCOL_VERSIONS.get(0),
                        List.of(), transport());
            } else {
                // A transport can support other ways to supply dummy initial requests
                dummy = dummyInitialRequest(mcpRequest);
            }

            if (dummy != null
                    && mcpRequest.connection().initialize(dummy)
                    && mcpRequest.connection().setInitialized()) {
                LOG.debugf("Connection initialized with dummy initial request: %s [%s]", dummy.implementation().name(),
                        mcpRequest.connection().id());
                return operation(message, mcpRequest);
            }

            String msg = "The first message from the client must be \"initialize\": " + message.getString("method");
            initializeFailed(mcpRequest);
            return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.METHOD_NOT_FOUND, msg);
        }

        if (params == null) {
            String msg = "Initialization params not found";
            initializeFailed(mcpRequest);
            return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_PARAMS, msg);
        }

        InitialRequest initialRequest = decodeInitializeRequest(params);
        // Start the context first
        mcpRequest.contextStart();
        // Then apply init checks
        return UniHelper.toFuture(checkInit(initialRequest, initialChecks, 0)).compose(res -> {
            if (res.error()) {
                // An init check failed - send the error message
                initializeFailed(mcpRequest);
                return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INTERNAL_ERROR, res.message());
            }
            // Init checks passed - attempt to initialize the connection
            if (mcpRequest.connection().initialize(initialRequest)) {
                // The server MUST respond with its own capabilities and information
                afterInitialize(mcpRequest);
                return mcpRequest.sender().sendResult(id, serverInfo(mcpRequest, initialRequest, message));
            } else {
                initializeFailed(mcpRequest);
                String msg = "Unable to initialize connection [connectionId: " + mcpRequest.connection().id() + "]";
                return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INTERNAL_ERROR, msg);
            }
        }).onComplete(r -> {
            mcpRequest.contextEnd();
        });
    }

    protected InitialRequest dummyInitialRequest(MCP_REQUEST mcpRequest) {
        return null;
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
        McpMethod method = McpMethod.from(message.getString("method"));
        if (McpMethod.NOTIFICATIONS_INITIALIZED == method) {
            if (mcpRequest.connection().setInitialized()) {
                LOG.debugf("Client successfully initialized [%s]", mcpRequest.connection().id());
                // Call init methods asynchronously
                List<NotificationManager.NotificationInfo> infos = notificationManager
                        .infosForRequest(FilterContextImpl.of(method, message, mcpRequest))
                        .filter(n -> n.type() == Type.INITIALIZED).toList();
                if (!infos.isEmpty()) {
                    FeatureExecutionContext featureExecutionContext = new FeatureExecutionContext(message, mcpRequest);
                    for (NotificationManager.NotificationInfo notification : infos) {
                        callNotification(notification, featureExecutionContext, mcpRequest);
                    }
                }
            }
            return Future.succeededFuture();
        } else if (McpMethod.PING == method) {
            return ping(message, mcpRequest);
        } else {
            return mcpRequest.sender().sendError(Messages.getId(message), JsonRpcErrorCodes.INTERNAL_ERROR,
                    "Client not initialized yet [" + mcpRequest.connection().id() + "]");
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

    private Future<Void> callNotification(NotificationManager.NotificationInfo notification,
            FeatureExecutionContext featureExecutionContext, McpRequest mcpRequest) {
        // Create a new duplicated context and process the notification on this context
        Context context = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);
        Promise<Void> ret = Promise.promise();

        context.runOnContext(v -> {
            mcpRequest.contextStart();
            try {
                Future<Void> fu = notificationManager.execute(notificationManager.key(notification),
                        featureExecutionContext);
                fu.onComplete(r -> {
                    mcpRequest.contextEnd();
                    if (r.failed()) {
                        LOG.errorf(r.cause(), "Unable to call notification method: %s", notification);
                        ret.fail(r.cause());
                    } else {
                        ret.complete();
                    }
                });
            } catch (McpException e) {
                LOG.errorf(e, "Unable to call notification method: %s", notification);
                throw e;
            }

        });
        return ret.future();
    }

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
            McpMethod method = McpMethod.from(message.getString("method"));
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
                default -> mcpRequest.sender().sendError(Messages.getId(message), JsonRpcErrorCodes.METHOD_NOT_FOUND,
                        "Unsupported method: " + method);
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
        List<NotificationManager.NotificationInfo> infos = notificationManager
                .infosForRequest(FilterContextImpl.of(McpMethod.NOTIFICATIONS_ROOTS_LIST_CHANGED, message, mcpRequest))
                .filter(n -> n.type() == Type.ROOTS_LIST_CHANGED).toList();
        if (!infos.isEmpty()) {
            FeatureExecutionContext featureExecutionContext = new FeatureExecutionContext(message, mcpRequest);
            for (NotificationManager.NotificationInfo notification : infos) {
                callNotification(notification, featureExecutionContext);
            }
        }
        return Future.succeededFuture();

    }

    private Future<Void> cancelRequest(JsonObject message, McpRequest mcpRequest) {
        JsonObject params = Messages.getParams(message);
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
        return ongoingId(Messages.isRequest(message) ? Messages.getId(message) : null, mcpRequest);
    }

    private String ongoingId(Object requestId, McpRequest mcpRequest) {
        if (requestId != null) {
            return requestId + "::" + mcpRequest.connection().id();
        } else {
            return null;
        }
    }

    private Future<Void> setLogLevel(JsonObject message, McpRequest mcpRequest) {
        Object id = Messages.getId(message);
        JsonObject params = Messages.getParams(message);
        String level = params.getString("level");
        if (level == null) {
            return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Log level not set");
        } else {
            LogLevel logLevel = LogLevel.from(level);
            if (logLevel == null) {
                return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Invalid log level set: " + level);
            } else {
                mcpRequest.connection().setLogLevel(logLevel);
                // Send empty result
                return mcpRequest.sender().sendResult(id, new JsonObject());
            }
        }

    }

    private Future<Void> complete(JsonObject message, McpRequest mcpRequest) {
        Object id = Messages.getId(message);
        JsonObject params = Messages.getParams(message);
        JsonObject ref = params.getJsonObject("ref");
        if (ref == null) {
            return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Reference not found");
        } else {
            String referenceType = ref.getString("type");
            if (referenceType == null) {
                return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Reference type not found");
            } else {
                JsonObject argument = params.getJsonObject("argument");
                if (argument == null) {
                    return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Argument not found");
                } else {
                    if (Messages.isPromptRef(referenceType)) {
                        return promptCompleteHandler.complete(message, id, ref, argument, mcpRequest.sender(), mcpRequest);
                    } else if (Messages.isResourceRef(referenceType)) {
                        return resourceTemplateCompleteHandler.complete(message, id, ref, argument, mcpRequest.sender(),
                                mcpRequest);
                    } else {
                        return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_REQUEST,
                                "Unsupported reference found: " + ref.getString("type"));
                    }
                }
            }
        }
    }

    private Future<Void> ping(JsonObject message, McpRequest mcpRequest) {
        Object id = Messages.getId(message);
        LOG.debugf("Ping [id: %s]", id);
        return mcpRequest.sender().sendResult(id, new JsonObject());
    }

    private Future<Void> close(JsonObject message, McpRequest mcpRequest) {
        if (connectionManager.remove(mcpRequest.connection().id())) {
            LOG.debugf("Connection %s explicitly closed ", mcpRequest.connection().id());
            return Future.succeededFuture();
        } else {
            return mcpRequest.sender().sendError(Messages.getId(message), JsonRpcErrorCodes.INTERNAL_ERROR,
                    "Unable to obtain the connection to be closed:" + mcpRequest.connection().id());
        }
    }

    private InitialRequest decodeInitializeRequest(JsonObject params) {
        JsonObject clientInfo = params.getJsonObject("clientInfo");
        Implementation implementation = Messages.decodeImplementation(clientInfo);
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

    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of(
            "2025-11-25",
            "2025-06-18",
            "2025-03-26",
            "2024-11-05");

    private Map<String, Object> serverInfo(MCP_REQUEST mcpRequest, InitialRequest initialRequest, JsonObject message) {
        Map<String, Object> info = new HashMap<>();

        // Note that currently the protocol version does not affect the behavior of the server in any way
        String version = SUPPORTED_PROTOCOL_VERSIONS.get(0);
        if (SUPPORTED_PROTOCOL_VERSIONS.contains(initialRequest.protocolVersion())) {
            version = initialRequest.protocolVersion();
        }
        info.put("protocolVersion", version);

        ServerInfo serverInfo = serverConfig(mcpRequest).serverInfo();
        JsonObject implementation = new JsonObject();
        String serverName = serverInfo.name()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.name", String.class)
                        .orElse("N/A"));
        implementation.put("name", serverName);
        implementation.put("version", serverInfo.version()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class)
                        .orElse("N/A")));
        implementation.put("title", serverInfo.title().orElse(serverName));
        if (serverInfo.description().isPresent()) {
            implementation.put("description", serverInfo.description().get());
        }
        if (serverInfo.websiteUrl().isPresent()) {
            implementation.put("websiteUrl", serverInfo.websiteUrl().get());
        }
        if (!serverInfo.icons().isEmpty()) {
            JsonArray icons = new JsonArray();
            for (Icon icon : serverInfo.icons()) {
                JsonObject i = new JsonObject()
                        .put("src", icon.src());
                if (icon.mimeType().isPresent()) {
                    i.put("mimeType", icon.mimeType().get());
                }
                if (icon.theme().isPresent()) {
                    i.put("theme", icon.theme().get().toString().toLowerCase());
                }
                if (!icon.sizes().isEmpty()) {
                    i.put("sizes", icon.sizes());
                }
                icons.add(i);
            }
            implementation.put("icons", icons);
        }
        info.put("serverInfo", implementation);

        FilterContextImpl filterContext = FilterContextImpl.of(McpMethod.INITIALIZE, message, mcpRequest);
        Map<String, Map<String, Object>> capabilities = new HashMap<>();
        if (promptManager.hasInfos(filterContext)) {
            capabilities.put("prompts", metadata.isPromptManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        if (toolManager.hasInfos(filterContext)) {
            capabilities.put("tools", metadata.isToolManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        if (resourceManager.hasInfos(filterContext)
                || resourceTemplateManager.hasInfos(filterContext)) {
            capabilities.put("resources", metadata.isResourceManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        if (promptCompletionManager.hasInfos(filterContext)
                || resourceTemplateCompletionManager.hasInfos(filterContext)) {
            capabilities.put("completions", Map.of());
        }
        capabilities.put("logging", Map.of());
        info.put("capabilities", capabilities);
        if (serverInfo.instructions().isPresent()) {
            info.put("instructions", serverInfo.instructions().get());
        }
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
                        String.format("Invalid server name [%s] used for: %s#%s", info.serverName(),
                                info.getClass().getSimpleName(), info.name())));
            }
            throw ise;
        }
    }

}
