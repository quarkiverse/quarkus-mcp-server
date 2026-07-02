package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.FeatureManager;
import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.InitialResponseInfo;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.NotificationManager;
import io.quarkiverse.mcp.server.RequestId;
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

    private final ServerRequests serverRequests;
    private final List<InitialCheck> initialChecks;
    private final List<InitialResponseInfo> initialResponseInfos;

    protected final McpServersRuntimeConfig config;

    protected final Vertx vertx;

    private final Set<String> ongoingRequests;

    private final McpMetadata metadata;

    private final McpMetrics mcpMetrics;

    private final McpTracing mcpTracing;

    private final McpRequestValidator mcpRequestValidator;

    private final CancellationRequests cancellationRequests;

    protected McpMessageHandler(McpServersRuntimeConfig config, ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager, ResourceManagerImpl resourceManager,
            PromptCompletionManagerImpl promptCompletionManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompletionManager,
            NotificationManagerImpl notificationManager,
            ServerRequests serverRequests,
            McpMetadata metadata, Vertx vertx,
            List<InitialCheck> initialChecks,
            List<InitialResponseInfo> initialResponseInfos,
            McpMetrics mcpMetrics,
            McpTracing mcpTracing,
            McpRequestValidator mcpRequestValidator,
            CancellationRequests cancellationRequests) {
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
        this.serverRequests = serverRequests;
        this.initialChecks = initialChecks;
        this.initialResponseInfos = initialResponseInfos;
        this.config = config;
        this.metadata = metadata;
        this.vertx = vertx;
        this.mcpMetrics = mcpMetrics;
        this.mcpTracing = mcpTracing;
        this.mcpRequestValidator = mcpRequestValidator;
        this.cancellationRequests = cancellationRequests;
        this.ongoingRequests = ConcurrentHashMap.newKeySet();

        if (config.invalidServerNameStrategy() == InvalidServerNameStrategy.FAIL) {
            validateServerConfigs();
        }
    }

    public Future<?> handle(MCP_REQUEST mcpRequest) {
        JsonObject message = mcpRequest.message();
        mcpRequest.markReceived();
        if (JsonRpc.validate(message, mcpRequest.sender())) {
            return Messages.isResponse(message) ? handleResponse(message)
                    : handleRequest(message, mcpRequest);
        } else {
            jsonrpcValidationFailed(mcpRequest);
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
        return serverRequests.handleResponse(Messages.getId(message), message);
    }

    private Future<Void> handleRequest(JsonObject message, MCP_REQUEST mcpRequest) {
        McpMethod method = McpMethod.from(message.getString("method"));
        if (method == null) {
            return unsupportedMethod(message, mcpRequest);
        }
        long start = System.nanoTime();
        // Prepare tracing - starts the span immediately
        mcpRequest.prepareTracing(mcpTracing, method, message, transport());

        Future<Void> ret = handleRequest(message, mcpRequest, method);
        ret.onComplete(ar -> {
            if (mcpMetrics != null) {
                mcpMetrics.mcpRequestCompleted(method, message, mcpRequest,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), ar.cause());
            }
            // End the tracing span - contextEnd() may have already ended it,
            // in which case this is a no-op since tracingSpan is set to null
            mcpRequest.endTracing(ar.cause());
        });
        return ret;
    }

    private Future<Void> handleRequest(JsonObject message, MCP_REQUEST mcpRequest, McpMethod method) {
        if (mcpRequestValidator != null) {
            return mcpRequestValidator.validate(message, mcpRequest, method)
                    .compose(valid -> {
                        if (valid) {
                            return doHandleRequest(message, mcpRequest, method);
                        }
                        mcpRequest.setTracingErrorResponse(false, JsonRpcErrorCodes.INVALID_REQUEST,
                                "Schema validation failed");
                        return Future.succeededFuture();
                    });
        }
        return doHandleRequest(message, mcpRequest, method);
    }

    private Future<Void> doHandleRequest(JsonObject message, MCP_REQUEST mcpRequest, McpMethod method) {
        if (method == McpMethod.SERVER_DISCOVER) {
            return serverDiscover(message, mcpRequest);
        }
        return switch (mcpRequest.connection().status()) {
            case NEW -> initializeNew(method, message, mcpRequest);
            case INITIALIZING -> initializing(method, message, mcpRequest);
            case IN_OPERATION -> operation(method, message, mcpRequest);
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

    private Future<Void> initializeNew(McpMethod method, JsonObject message, MCP_REQUEST mcpRequest) {
        Object id = Messages.getId(message);
        JsonObject params = Messages.getParams(message);

        if (McpMethod.INITIALIZE != method) {
            // Normally the first message must be "initialize"
            // However, automatic initialization can be performed in the dev mode
            // and the transport can support other ways to supply auto initial requests
            InitialRequest autoInit = null;
            if (LaunchMode.current() == LaunchMode.DEVELOPMENT
                    && serverConfig(mcpRequest).devMode().dummyInit()) {
                // In the dev mode, perform an automatic initialization
                autoInit = new InitialRequest(new Implementation("dummy", "1", null),
                        McpProtocolVersion.LATEST_STATEFUL,
                        List.of(), transport(), true);
            } else {
                autoInit = autoInitialRequest(mcpRequest);
            }

            if (autoInit != null
                    && mcpRequest.connection().initialize(autoInit)
                    && mcpRequest.connection().setInitialized()) {
                LOG.debugf("Connection initialized with auto initial request: %s [%s]", autoInit.implementation().name(),
                        mcpRequest.connection().id());
                return operation(method, message, mcpRequest);
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
                return mcpRequest.sender().sendResult(id, initResult(mcpRequest, initialRequest, message));
            } else {
                initializeFailed(mcpRequest);
                String msg = "Unable to initialize connection [connectionId: " + mcpRequest.connection().id() + "]";
                return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INTERNAL_ERROR, msg);
            }
        }).onComplete(r -> {
            mcpRequest.contextEnd(r.cause());
        });
    }

    protected InitialRequest autoInitialRequest(MCP_REQUEST mcpRequest) {
        return null;
    }

    private static Uni<InitialCheck.CheckResult> checkInit(InitialRequest initialRequest, List<InitialCheck> checks, int idx) {
        if (checks.isEmpty()) {
            return InitialCheck.CheckResult.success();
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

    private Future<Void> initializing(McpMethod method, JsonObject message, MCP_REQUEST mcpRequest) {
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
        } else {
            return operation(method, message, mcpRequest);
        }
    }

    static IllegalStateException clientNotInitialized(McpConnection connection) {
        return new IllegalStateException("Client not initialized yet: " + connection);
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
            FeatureExecutionContext featureExecutionContext, MCP_REQUEST mcpRequest) {
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
                    mcpRequest.contextEnd(r.cause());
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

    private Future<Void> operation(McpMethod method, JsonObject message, MCP_REQUEST mcpRequest) {
        McpProtocolVersion protocolVersion = mcpRequest.protocolVersion();
        if (protocolVersion != null && protocolVersion.isStateless() && STATELESS_REMOVED_METHODS.contains(method)) {
            return mcpRequest.sender().sendError(Messages.getId(message), JsonRpcErrorCodes.METHOD_NOT_FOUND,
                    "Method not available in stateless protocol version: " + method.jsonRpcName());
        }
        // Few operations do not involve user code
        // and don't need a new duplicated context
        if (method == McpMethod.PING) {
            return ping(message, mcpRequest);
        } else if (method == McpMethod.Q_CLOSE) {
            return close(message, mcpRequest);
        } else if (method == McpMethod.LOGGING_SET_LEVEL) {
            return setLogLevel(message, mcpRequest);
        } else if (method == McpMethod.NOTIFICATIONS_PROGRESS) {
            return progress(message, mcpRequest);
        } else if (method == McpMethod.SUBSCRIPTIONS_LISTEN) {
            return subscriptionsListen(message, mcpRequest);
        }
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
            Future<?> future = switch (method) {
                case PROMPTS_LIST -> promptHandler.promptsList(message, mcpRequest);
                case PROMPTS_GET -> promptHandler.promptsGet(message, mcpRequest);
                case TOOLS_LIST -> toolHandler.toolsList(message, mcpRequest);
                case TOOLS_CALL -> toolHandler.toolsCall(message, mcpRequest);
                case RESOURCES_LIST -> resourceHandler.resourcesList(message, mcpRequest);
                case RESOURCES_READ -> resourceHandler.resourcesRead(message, mcpRequest);
                case RESOURCES_SUBSCRIBE -> resourceHandler.resourcesSubscribe(message, mcpRequest);
                case RESOURCES_UNSUBSCRIBE -> resourceHandler.resourcesUnsubscribe(message, mcpRequest);
                case RESOURCE_TEMPLATES_LIST -> resourceTemplateHandler.resourceTemplatesList(message, mcpRequest);
                case COMPLETION_COMPLETE -> complete(message, mcpRequest);
                case NOTIFICATIONS_ROOTS_LIST_CHANGED -> rootsListChanged(message, mcpRequest);
                case NOTIFICATIONS_CANCELLED -> cancelRequest(message, mcpRequest);
                case NOTIFICATIONS_INITIALIZED -> alreadyInitialized(mcpRequest);
                default -> unsupportedMethod(message, mcpRequest);
            };
            future.onComplete(r -> {
                mcpRequest.contextEnd(r.cause());
                if (ongoingId != null) {
                    ongoingRequests.remove(ongoingId);
                    cancellationRequests.remove(mcpRequest.connection(), message);
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

    protected Future<Void> unsupportedMethod(JsonObject message, MCP_REQUEST mcpRequest) {
        return mcpRequest.sender().sendError(Messages.getId(message), JsonRpcErrorCodes.METHOD_NOT_FOUND,
                "Unsupported method: " + message.getString("method"));
    }

    private Future<Void> rootsListChanged(JsonObject message, MCP_REQUEST mcpRequest) {
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

    private Future<Void> cancelRequest(JsonObject message, MCP_REQUEST mcpRequest) {
        JsonObject params = Messages.getParams(message);
        if (params != null) {
            Object requestId = params.getValue("requestId");
            // Unknown, completed and invalid requests should be just ignored
            if (requestId != null
                    && ongoingRequests.contains(ongoingId(requestId, mcpRequest))
                    && cancellationRequests.add(mcpRequest.connection(), new RequestId(requestId),
                            params.getString("reason"))) {
                String reason = params.getString("reason");
                LOG.debugf("Cancel request with id %s: %s [%s]", requestId, reason != null ? reason : "no reason",
                        mcpRequest.connection().id());
            } else if (requestId != null && mcpRequest.connection().removeSubscription(requestId)) {
                LOG.debugf("Subscription %s cancelled [%s]", requestId, mcpRequest.connection().id());
            } else {
                LOG.warnf("Ignored unknown/completed/invalid cancel request with id %s [%s]", requestId,
                        mcpRequest.connection().id());
            }
        }
        return Future.succeededFuture();
    }

    private Future<Void> subscriptionsListen(JsonObject message, MCP_REQUEST mcpRequest) {
        Object id = Messages.getId(message);
        JsonObject params = Messages.getParams(message);
        if (params == null) {
            return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_PARAMS, "Missing params");
        }
        JsonObject notifications = params.getJsonObject("notifications");
        if (notifications == null) {
            return mcpRequest.sender().sendError(id, JsonRpcErrorCodes.INVALID_PARAMS,
                    "Missing notifications in params");
        }
        SubscriptionFilter filter = SubscriptionFilter.parse(notifications);
        Subscription subscription = new Subscription(id, filter);
        mcpRequest.connection().addSubscription(subscription);
        LOG.debugf("Subscription %s opened [%s]", id, mcpRequest.connection().id());
        return onSubscriptionOpened(message, mcpRequest, subscription);
    }

    /**
     * Called after a {@code subscriptions/listen} request is processed and the subscription is registered.
     * Transports override this to handle transport-specific stream setup.
     * <p>
     * The default implementation registers transient connections in the {@link ConnectionManager} and sends the
     * {@code notifications/subscriptions/acknowledged} notification via the request sender.
     */
    protected Future<Void> onSubscriptionOpened(JsonObject message, MCP_REQUEST mcpRequest, Subscription subscription) {
        if (mcpRequest.connection().isTransient()) {
            connectionManager.add(mcpRequest.connection());
        }
        JsonObject params = new JsonObject().put("notifications", subscription.filter().toAcknowledgedJson());
        JsonObject acknowledged = Messages.newNotification(
                McpMethod.NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED.jsonRpcName(), params);
        McpConnectionBase.injectSubscriptionId(acknowledged, subscription.subscriptionId());
        return mcpRequest.sender().send(acknowledged);
    }

    private String ongoingId(JsonObject message, MCP_REQUEST mcpRequest) {
        return ongoingId(Messages.isRequest(message) ? Messages.getId(message) : null, mcpRequest);
    }

    private String ongoingId(Object requestId, MCP_REQUEST mcpRequest) {
        if (requestId != null) {
            return requestId + "::" + mcpRequest.connection().id();
        } else {
            return null;
        }
    }

    private Future<Void> setLogLevel(JsonObject message, MCP_REQUEST mcpRequest) {
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

    private Future<Void> complete(JsonObject message, MCP_REQUEST mcpRequest) {
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

    private Future<Void> ping(JsonObject message, MCP_REQUEST mcpRequest) {
        Object id = Messages.getId(message);
        LOG.debugf("Ping [id: %s]", id);
        return mcpRequest.sender().sendResult(id, new JsonObject());
    }

    private Future<Void> alreadyInitialized(MCP_REQUEST mcpRequest) {
        LOG.debugf("Ignoring notifications/initialized on already initialized connection [%s]",
                mcpRequest.connection().id());
        return Future.succeededFuture();
    }

    private Future<Void> progress(JsonObject message, MCP_REQUEST mcpRequest) {
        JsonObject params = Messages.getParams(message);
        String token = "n/a";
        if (params != null) {
            token = params.getString("progressToken", "n/a");
        }
        LOG.debugf("Progress notification ignored [token: %s]", token);
        return Future.succeededFuture();
    }

    private Future<Void> close(JsonObject message, MCP_REQUEST mcpRequest) {
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
        McpProtocolVersion protocolVersion = McpProtocolVersion.from(params.getString("protocolVersion"));
        if (protocolVersion == null) {
            protocolVersion = McpProtocolVersion.LATEST_STATEFUL;
        }
        List<ClientCapability> clientCapabilities = new ArrayList<>();
        JsonObject capabilities = params.getJsonObject("capabilities");
        if (capabilities != null) {
            for (String name : capabilities.fieldNames()) {
                Map<String, Object> properties;
                Object value = capabilities.getValue(name);
                if (value instanceof JsonObject obj && !obj.isEmpty()) {
                    properties = new HashMap<>();
                    for (String key : obj.fieldNames()) {
                        properties.put(key, obj.getValue(key));
                    }
                } else {
                    properties = Map.of();
                }
                clientCapabilities.add(new ClientCapability(name, properties));
            }
        }
        return new InitialRequest(implementation, protocolVersion, List.copyOf(clientCapabilities), transport());
    }

    public static final String FIRST_STATELESS_PROTOCOL_VERSION = McpProtocolVersion.FIRST_STATELESS.version();

    /**
     * @return {@code true} if the given protocol version uses the stateless (per-request metadata) model,
     *         i.e. is {@value #FIRST_STATELESS_PROTOCOL_VERSION} or later
     */
    public static boolean isStateless(String protocolVersion) {
        return McpProtocolVersion.isStateless(protocolVersion);
    }

    private static final EnumSet<McpMethod> STATELESS_REMOVED_METHODS = EnumSet.of(
            McpMethod.INITIALIZE,
            McpMethod.NOTIFICATIONS_INITIALIZED,
            McpMethod.PING,
            McpMethod.LOGGING_SET_LEVEL,
            McpMethod.NOTIFICATIONS_ROOTS_LIST_CHANGED,
            McpMethod.RESOURCES_SUBSCRIBE,
            McpMethod.RESOURCES_UNSUBSCRIBE);

    private static final Map<String, Object> LIST_CHANGED_PROPERTIES = Map.of("listChanged", true);
    private static final Map<String, Object> SUBSCRIBE_PROPERTIES = Map.of("subscribe", true);

    /**
     * @return the {@code _meta} object from the message params, or {@code null}
     */
    protected static JsonObject findMeta(JsonObject message) {
        JsonObject params = Messages.getParams(message);
        if (params != null) {
            return params.getJsonObject("_meta");
        }
        return null;
    }

    /**
     * @return {@code true} if the message uses the stateless protocol (based on method name or {@code _meta} protocol version)
     */
    protected static boolean isStatelessMessage(JsonObject message) {
        return isStatelessMessage(message, findMeta(message));
    }

    /**
     * @return {@code true} if the message uses the stateless protocol (based on method name or {@code _meta} protocol version)
     */
    protected static boolean isStatelessMessage(JsonObject message, JsonObject meta) {
        String method = message.getString("method");
        if ("server/discover".equals(method)) {
            return true;
        }
        if ("initialize".equals(method)) {
            return false;
        }
        if (meta != null) {
            String metaVersion = meta.getString(MetaKey.PROTOCOL_VERSION.toString());
            if (metaVersion != null && isStateless(metaVersion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies the per-request log level from the {@code _meta} object to the connection.
     *
     * @throws McpException if the log level value is not valid
     */
    protected static void applyMetaLogLevel(JsonObject meta, McpConnectionBase connection) {
        if (meta == null) {
            return;
        }
        String logLevelStr = meta.getString(MetaKey.LOG_LEVEL.toString());
        if (logLevelStr != null) {
            LogLevel logLevel = LogLevel.from(logLevelStr);
            if (logLevel == null) {
                throw new McpException("Invalid log level: " + logLevelStr, JsonRpcErrorCodes.INVALID_PARAMS);
            }
            connection.setLogLevel(logLevel);
        }
    }

    /**
     * Builds an {@link InitialRequest} for a stateless request by extracting client info and capabilities from {@code _meta}.
     *
     * @param meta the {@code _meta} object from the JSON-RPC message
     * @param protocolVersion the protocol version to use, or {@code null} to use the value from {@code _meta}
     * @param transport the transport type
     * @return the initial request
     * @throws McpException with {@link JsonRpcErrorCodes#INVALID_PARAMS} if any required {@code _meta} field is missing
     */
    protected static InitialRequest buildStatelessInitialRequest(JsonObject meta, String protocolVersionStr,
            InitialRequest.Transport transport) {
        validateStatelessMeta(meta);
        String versionStr = protocolVersionStr != null ? protocolVersionStr
                : meta.getString(MetaKey.PROTOCOL_VERSION.toString());
        McpProtocolVersion protocolVersion = McpProtocolVersion.from(versionStr);
        if (protocolVersion == null) {
            protocolVersion = McpProtocolVersion.FIRST_STATELESS;
        }
        Implementation implementation = Messages.decodeImplementation(meta.getJsonObject(MetaKey.CLIENT_INFO.toString()));
        JsonObject capabilities = meta.getJsonObject(MetaKey.CLIENT_CAPABILITIES.toString());
        List<ClientCapability> clientCapabilities = new ArrayList<>();
        for (String name : capabilities.fieldNames()) {
            clientCapabilities.add(new ClientCapability(name, Map.of()));
        }
        return new InitialRequest(implementation, protocolVersion, List.copyOf(clientCapabilities), transport, true);
    }

    /**
     * Validates that all required per-request {@code _meta} fields are present.
     *
     * @param meta the {@code _meta} object from the JSON-RPC message
     * @throws McpException with {@link JsonRpcErrorCodes#INVALID_PARAMS} if any required field is missing
     */
    static void validateStatelessMeta(JsonObject meta) {
        if (meta == null) {
            throw new McpException("Stateless request must include _meta with required fields: "
                    + MetaKey.PROTOCOL_VERSION + ", " + MetaKey.CLIENT_INFO + ", " + MetaKey.CLIENT_CAPABILITIES,
                    JsonRpcErrorCodes.INVALID_PARAMS);
        }
        List<String> missing = null;
        if (meta.getString(MetaKey.PROTOCOL_VERSION.toString()) == null) {
            missing = new ArrayList<>();
            missing.add(MetaKey.PROTOCOL_VERSION.toString());
        }
        if (meta.getJsonObject(MetaKey.CLIENT_INFO.toString()) == null) {
            if (missing == null) {
                missing = new ArrayList<>();
            }
            missing.add(MetaKey.CLIENT_INFO.toString());
        }
        if (meta.getJsonObject(MetaKey.CLIENT_CAPABILITIES.toString()) == null) {
            if (missing == null) {
                missing = new ArrayList<>();
            }
            missing.add(MetaKey.CLIENT_CAPABILITIES.toString());
        }
        if (missing != null) {
            throw new McpException("Stateless request is missing required _meta fields: " + String.join(", ", missing),
                    JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }

    private Future<Void> serverDiscover(JsonObject message, MCP_REQUEST mcpRequest) {
        Object id = Messages.getId(message);
        FilterContextImpl filterContext = FilterContextImpl.of(McpMethod.SERVER_DISCOVER, message, mcpRequest);
        Map<String, Object> ret = new HashMap<>();
        ret.put("supportedVersions", McpProtocolVersion.SUPPORTED_VERSIONS);
        ret.put("capabilities", buildCapabilities(filterContext));
        ret.put("serverInfo", buildServerInfo(mcpRequest));
        Optional<String> instructions = buildInstructions(mcpRequest);
        if (instructions.isPresent()) {
            ret.put("instructions", instructions.get());
        }
        return mcpRequest.sender().sendResult(id, ret);
    }

    private Map<String, Object> initResult(MCP_REQUEST mcpRequest, InitialRequest initialRequest, JsonObject message) {
        Map<String, Object> ret = new HashMap<>();

        ret.put("protocolVersion", initialRequest.protocolVersion().version());

        FilterContextImpl filterContext = FilterContextImpl.of(McpMethod.INITIALIZE, message, mcpRequest);
        ret.put("capabilities", buildCapabilities(filterContext));
        ret.put("serverInfo", buildServerInfo(mcpRequest));

        Optional<String> instructions = buildInstructions(mcpRequest);
        if (instructions.isPresent()) {
            ret.put("instructions", instructions.get());
        }

        for (InitialResponseInfo info : initialResponseInfos) {
            Optional<Map<MetaKey, Object>> meta = info.meta(mcpRequest.serverName());
            if (meta != null && meta.isPresent()) {
                ret.put("_meta", meta.get());
                break;
            }
        }
        return ret;
    }

    private Map<String, Map<String, Object>> buildCapabilities(FilterContextImpl filterContext) {
        Map<String, Map<String, Object>> capabilities = new HashMap<>();
        if (promptManager.hasInfos(filterContext)) {
            capabilities.put("prompts", metadata.isPromptManagerUsed() ? LIST_CHANGED_PROPERTIES : Map.of());
        }
        if (toolManager.hasInfos(filterContext)) {
            capabilities.put("tools", metadata.isToolManagerUsed() ? LIST_CHANGED_PROPERTIES : Map.of());
        }
        if (resourceManager.hasInfos(filterContext)
                || resourceTemplateManager.hasInfos(filterContext)) {
            var props = SUBSCRIBE_PROPERTIES;
            if (metadata.isResourceManagerUsed() || metadata.isResourceTemplateManagerUsed()) {
                props = new HashMap<>();
                props.putAll(LIST_CHANGED_PROPERTIES);
                props.putAll(SUBSCRIBE_PROPERTIES);
            }
            capabilities.put("resources", props);
        }
        if (promptCompletionManager.hasInfos(filterContext)
                || resourceTemplateCompletionManager.hasInfos(filterContext)) {
            capabilities.put("completions", Map.of());
        }
        capabilities.put("logging", Map.of());
        return capabilities;
    }

    private Object buildServerInfo(MCP_REQUEST mcpRequest) {
        for (InitialResponseInfo info : initialResponseInfos) {
            Optional<Implementation> impl = info.implementation(mcpRequest.serverName());
            if (impl != null && impl.isPresent()) {
                return impl.get();
            }
        }
        ServerInfo serverInfo = serverConfig(mcpRequest).serverInfo();
        JsonObject impl = new JsonObject();
        String serverName = serverInfo.name()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.name", String.class)
                        .orElse("N/A"));
        impl.put("name", serverName);
        impl.put("version", serverInfo.version()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class)
                        .orElse("N/A")));
        impl.put("title", serverInfo.title().orElse(serverName));
        if (serverInfo.description().isPresent()) {
            impl.put("description", serverInfo.description().get());
        }
        if (serverInfo.websiteUrl().isPresent()) {
            impl.put("websiteUrl", serverInfo.websiteUrl().get());
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
            impl.put("icons", icons);
        }
        return impl;
    }

    private Optional<String> buildInstructions(MCP_REQUEST mcpRequest) {
        ServerInfo serverInfo = serverConfig(mcpRequest).serverInfo();
        Optional<String> instructions = serverInfo.instructions();
        for (InitialResponseInfo info : initialResponseInfos) {
            Optional<String> instr = info.instructions(mcpRequest.serverName());
            if (instr != null && instr.isPresent()) {
                return instr;
            }
        }
        return instructions;
    }

    private void validateServerConfigs() {
        List<FeatureInfo> invalid = new ArrayList<>();
        Set<String> serverNames = new HashSet<>(metadata.serverNames());
        serverNames.addAll(config.servers().keySet());

        collectFeaturesWithInvalidServerNames(serverNames, toolManager, invalid);
        collectFeaturesWithInvalidServerNames(serverNames, promptManager, invalid);
        collectFeaturesWithInvalidServerNames(serverNames, resourceManager, invalid);
        collectFeaturesWithInvalidServerNames(serverNames, resourceTemplateManager, invalid);
        collectFeaturesWithInvalidServerNames(serverNames, notificationManager, invalid);
        collectFeaturesWithInvalidServerNames(serverNames, promptCompletionManager, invalid);
        collectFeaturesWithInvalidServerNames(serverNames, resourceTemplateCompletionManager, invalid);

        if (!invalid.isEmpty()) {
            IllegalStateException ise = new IllegalStateException("Invalid server name");
            for (FeatureInfo info : invalid) {
                Set<String> invalidServerName = findInvalidServerNames(serverNames, info.serverNames());
                ise.addSuppressed(new IllegalStateException(
                        String.format("Invalid server names [%s] used for: %s#%s", invalidServerName,
                                info.getClass().getSimpleName(), info.name())));
            }
            throw ise;
        }
    }

    private <INFO extends FeatureManager.FeatureInfo> void collectFeaturesWithInvalidServerNames(Set<String> knownServerNames,
            Iterable<INFO> features, List<FeatureInfo> invalid) {
        for (FeatureInfo feature : features) {
            if (!findInvalidServerNames(knownServerNames, feature.serverNames()).isEmpty()) {
                invalid.add(feature);
            }
        }
    }

    private Set<String> findInvalidServerNames(Set<String> knownServerNames, Set<String> serverNames) {
        Set<String> ret = new HashSet<String>();
        for (String serverName : serverNames) {
            if (!knownServerNames.contains(serverName)) {
                ret.add(serverName);
            }
        }
        return ret;
    }

}
