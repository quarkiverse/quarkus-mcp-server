package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.NotificationManager;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkus.runtime.LaunchMode;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class McpMessageHandler {

    private static final Logger LOG = Logger.getLogger(McpMessageHandler.class);

    protected final ConnectionManager connectionManager;

    private final ToolMessageHandler toolHandler;
    private final PromptMessageHandler promptHandler;
    private final PromptCompleteMessageHandler promptCompleteHandler;
    private final ResourceMessageHandler resourceHandler;
    private final ResourceTemplateMessageHandler resourceTemplateHandler;
    private final ResourceTemplateCompleteMessageHandler resourceTemplateCompleteHandler;

    private final NotificationManagerImpl notificationManager;

    private final ResponseHandlers responseHandlers;

    protected final McpRuntimeConfig config;

    private final Map<String, Object> serverInfo;

    protected McpMessageHandler(McpRuntimeConfig config, ConnectionManager connectionManager, PromptManagerImpl promptManager,
            ToolManagerImpl toolManager, ResourceManagerImpl resourceManager, PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompleteManagerImpl resourceTemplateCompleteManager, NotificationManagerImpl notificationManager,
            ResponseHandlers responseHandlers,
            McpMetadata metadata) {
        this.connectionManager = connectionManager;
        this.toolHandler = new ToolMessageHandler(toolManager, config.tools().pageSize());
        this.promptHandler = new PromptMessageHandler(promptManager, config.prompts().pageSize());
        this.promptCompleteHandler = new PromptCompleteMessageHandler(promptCompleteManager);
        this.resourceHandler = new ResourceMessageHandler(resourceManager, config.resources().pageSize());
        this.resourceTemplateHandler = new ResourceTemplateMessageHandler(resourceTemplateManager,
                config.resourceTemplates().pageSize());
        this.resourceTemplateCompleteHandler = new ResourceTemplateCompleteMessageHandler(resourceTemplateCompleteManager);
        this.notificationManager = notificationManager;
        this.responseHandlers = responseHandlers;
        this.config = config;
        this.serverInfo = serverInfo(promptManager, toolManager, resourceManager, resourceTemplateManager, metadata);
    }

    public void handle(JsonObject message, McpConnectionBase connection, Sender sender, SecuritySupport securitySupport) {
        if (Messages.isResponse(message)) {
            // Response from a client
            responseHandlers.handleResponse(message.getValue("id"), message);
        } else {
            switch (connection.status()) {
                case NEW -> initializeNew(message, sender, connection, securitySupport);
                case INITIALIZING -> initializing(message, sender, connection, securitySupport);
                case IN_OPERATION -> operation(message, sender, connection, securitySupport);
                case CLOSED -> sender.send(
                        Messages.newError(message.getValue("id"), JsonRPC.INTERNAL_ERROR, "Connection is closed"));
            }
        }
    }

    private void initializeNew(JsonObject message, Sender sender, McpConnectionBase connection,
            SecuritySupport securitySupport) {
        Object id = message.getValue("id");
        // The first message must be "initialize"
        String method = message.getString("method");
        if (!INITIALIZE.equals(method)) {
            // In the dev mode, if an MCP client attempts to reconnect an SSE connection but does not reinitialize propertly,
            // we could perform a "dummy" initialization
            if (LaunchMode.current() == LaunchMode.DEVELOPMENT && config.devMode().dummyInit()) {
                InitialRequest dummy = new InitialRequest(new Implementation("dummy", "1"), DEFAULT_PROTOCOL_VERSION,
                        List.of());
                if (connection.initialize(dummy) && connection.setInitialized()) {
                    LOG.infof("Connection initialized with dummy info [%s]", connection.id());
                    operation(message, sender, connection, securitySupport);
                    return;
                }
            }
            sender.sendError(id, JsonRPC.METHOD_NOT_FOUND,
                    "The first message from the client must be \"initialize\": " + method);
            return;
        }
        JsonObject params = message.getJsonObject("params");
        if (params == null) {
            sender.sendError(id, JsonRPC.INVALID_PARAMS, "Initialization params not found");
            return;
        }
        // TODO schema validation?
        if (connection.initialize(decodeInitializeRequest(params))) {
            // The server MUST respond with its own capabilities and information
            sender.sendResult(id, serverInfo);
        } else {
            sender.sendError(id, JsonRPC.INTERNAL_ERROR,
                    "Unable to initialize connection [connectionId: " + connection.id() + "]");
        }
    }

    private void initializing(JsonObject message, Sender sender, McpConnectionBase connection,
            SecuritySupport securitySupport) {
        String method = message.getString("method");
        if (NOTIFICATIONS_INITIALIZED.equals(method)) {
            if (connection.setInitialized()) {
                LOG.debugf("Client successfully initialized [%s]", connection.id());
                // Call init methods
                List<NotificationManager.NotificationInfo> infos = notificationManager.infoStream()
                        .filter(n -> n.type() == Type.INITIALIZED).toList();
                if (!infos.isEmpty()) {
                    ArgumentProviders argProviders = new ArgumentProviders(Map.of(), connection, null, null, sender, null,
                            responseHandlers);
                    FeatureExecutionContext featureExecutionContext = new FeatureExecutionContext(argProviders,
                            securitySupport);
                    for (NotificationManager.NotificationInfo notification : infos) {
                        try {
                            Future<Void> fu = notificationManager.execute(notificationManager.key(notification),
                                    featureExecutionContext);
                            fu.onComplete(new Handler<AsyncResult<Void>>() {
                                @Override
                                public void handle(AsyncResult<Void> ar) {
                                    if (ar.failed()) {
                                        LOG.errorf(ar.cause(), "Unable to call notification method: %s", notification);
                                    }
                                }
                            });
                        } catch (McpException e) {
                            LOG.errorf(e, "Unable to call notification method: %s", notification);
                        }
                    }
                }
            }
        } else if (PING.equals(method)) {
            ping(message, sender);
        } else {
            sender.send(Messages.newError(message.getValue("id"), JsonRPC.INTERNAL_ERROR,
                    "Client not initialized yet [" + connection.id() + "]"));
        }
    }

    static final String INITIALIZE = "initialize";
    static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    static final String NOTIFICATIONS_MESSAGE = "notifications/message";
    static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
    static final String NOTIFICATIONS_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
    static final String NOTIFICATIONS_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
    static final String NOTIFICATIONS_PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";
    static final String NOTIFICATIONS_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";
    static final String PROMPTS_LIST = "prompts/list";
    static final String PROMPTS_GET = "prompts/get";
    static final String TOOLS_LIST = "tools/list";
    static final String TOOLS_CALL = "tools/call";
    static final String RESOURCES_LIST = "resources/list";
    static final String RESOURCE_TEMPLATES_LIST = "resources/templates/list";
    static final String RESOURCES_READ = "resources/read";
    static final String RESOURCES_SUBSCRIBE = "resources/subscribe";
    static final String RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
    static final String PING = "ping";
    static final String ROOTS_LIST = "roots/list";
    static final String SAMPLING_CREATE_MESSAGE = "sampling/createMessage";
    static final String COMPLETION_COMPLETE = "completion/complete";
    static final String LOGGING_SET_LEVEL = "logging/setLevel";
    // non-standard messages
    static final String Q_CLOSE = "q/close";

    private void operation(JsonObject message, Sender sender, McpConnection connection, SecuritySupport securitySupport) {
        String method = message.getString("method");
        switch (method) {
            case PROMPTS_LIST -> promptHandler.promptsList(message, sender);
            case PROMPTS_GET -> promptHandler.promptsGet(message, sender, connection, securitySupport);
            case TOOLS_LIST -> toolHandler.toolsList(message, sender);
            case TOOLS_CALL -> toolHandler.toolsCall(message, sender, connection, securitySupport);
            case PING -> ping(message, sender);
            case RESOURCES_LIST -> resourceHandler.resourcesList(message, sender);
            case RESOURCES_READ -> resourceHandler.resourcesRead(message, sender, connection, securitySupport);
            case RESOURCES_SUBSCRIBE -> resourceHandler.resourcesSubscribe(message, sender, connection);
            case RESOURCES_UNSUBSCRIBE -> resourceHandler.resourcesUnsubscribe(message, sender, connection);
            case RESOURCE_TEMPLATES_LIST -> resourceTemplateHandler.resourceTemplatesList(message, sender);
            case COMPLETION_COMPLETE -> complete(message, sender, connection, securitySupport);
            case LOGGING_SET_LEVEL -> setLogLevel(message, sender, connection);
            case Q_CLOSE -> close(message, sender, connection);
            case NOTIFICATIONS_ROOTS_LIST_CHANGED -> rootsListChanged(sender, connection, securitySupport);
            default -> sender.send(
                    Messages.newError(message.getValue("id"), JsonRPC.METHOD_NOT_FOUND, "Unsupported method: " + method));
        }
    }

    private Object rootsListChanged(Sender sender, McpConnection connection, SecuritySupport securitySupport) {
        // Call init methods
        List<NotificationManager.NotificationInfo> infos = notificationManager.infoStream()
                .filter(n -> n.type() == Type.ROOTS_LIST_CHANGED).toList();
        if (!infos.isEmpty()) {
            ArgumentProviders argProviders = new ArgumentProviders(Map.of(), connection, null, null, sender, null,
                    responseHandlers);
            FeatureExecutionContext featureExecutionContext = new FeatureExecutionContext(argProviders,
                    securitySupport);
            for (NotificationManager.NotificationInfo notification : infos) {
                try {
                    Future<Void> fu = notificationManager.execute(notificationManager.key(notification),
                            featureExecutionContext);
                    fu.onComplete(new Handler<AsyncResult<Void>>() {
                        @Override
                        public void handle(AsyncResult<Void> ar) {
                            if (ar.failed()) {
                                LOG.errorf(ar.cause(), "Unable to call notification method: %s", notification);
                            }
                        }
                    });
                } catch (McpException e) {
                    LOG.errorf(e, "Unable to call notification method: %s", notification);
                }
            }
        }
        return null;
    }

    private void setLogLevel(JsonObject message, Sender sender, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String level = params.getString("level");
        if (level == null) {
            sender.sendError(id, JsonRPC.INVALID_REQUEST, "Log level not set");
        } else {
            LogLevel logLevel = LogLevel.from(level);
            if (logLevel == null) {
                sender.sendError(id, JsonRPC.INVALID_REQUEST, "Invalid log level set: " + level);
            } else {
                if (connection instanceof McpConnectionBase connectionBase) {
                    connectionBase.setLogLevel(logLevel);
                    // Send empty result
                    sender.sendResult(id, new JsonObject());
                } else {
                    throw new IllegalStateException();
                }
            }
        }

    }

    private void complete(JsonObject message, Sender sender, McpConnection connection, SecuritySupport securitySupport) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        JsonObject ref = params.getJsonObject("ref");
        if (ref == null) {
            sender.sendError(id, JsonRPC.INVALID_REQUEST, "Reference not found");
        } else {
            String referenceType = ref.getString("type");
            if (referenceType == null) {
                sender.sendError(id, JsonRPC.INVALID_REQUEST, "Reference type not found");
            } else {
                JsonObject argument = params.getJsonObject("argument");
                if (argument == null) {
                    sender.sendError(id, JsonRPC.INVALID_REQUEST, "Argument not found");
                } else {
                    if ("ref/prompt".equals(referenceType)) {
                        promptCompleteHandler.complete(message, id, ref, argument, sender, connection, securitySupport);
                    } else if ("ref/resource".equals(referenceType)) {
                        resourceTemplateCompleteHandler.complete(message, id, ref, argument, sender, connection,
                                securitySupport);
                    } else {
                        sender.sendError(id, JsonRPC.INVALID_REQUEST,
                                "Unsupported reference found: " + ref.getString("type"));
                    }
                }
            }
        }
    }

    private void ping(JsonObject message, Sender sender) {
        Object id = message.getValue("id");
        LOG.debugf("Ping [id: %s]", id);
        sender.sendResult(id, new JsonObject());
    }

    private void close(JsonObject message, Sender sender, McpConnection connection) {
        if (connectionManager.remove(connection.id())) {
            LOG.debugf("Connection %s explicitly closed ", connection.id());
        } else {
            sender.sendError(message.getValue("id"), JsonRPC.INTERNAL_ERROR,
                    "Unable to obtain the connection to be closed:" + connection.id());
        }
    }

    private InitialRequest decodeInitializeRequest(JsonObject params) {
        JsonObject clientInfo = params.getJsonObject("clientInfo");
        Implementation implementation = new Implementation(clientInfo.getString("name"), clientInfo.getString("version"));
        String protocolVersion = params.getString("protocolVersion");
        List<ClientCapability> clientCapabilities = new ArrayList<>();
        JsonObject capabilities = params.getJsonObject("capabilities");
        if (capabilities != null) {
            for (String name : capabilities.fieldNames()) {
                // TODO capability properties
                clientCapabilities.add(new ClientCapability(name, Map.of()));
            }
        }
        return new InitialRequest(implementation, protocolVersion, List.copyOf(clientCapabilities));
    }

    private static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";

    private Map<String, Object> serverInfo(PromptManagerImpl promptManager, ToolManagerImpl toolManager,
            ResourceManagerImpl resourceManager, ResourceTemplateManagerImpl resourceTemplateManager, McpMetadata metadata) {
        Map<String, Object> info = new HashMap<>();
        info.put("protocolVersion", DEFAULT_PROTOCOL_VERSION);

        String serverName = config.serverInfo().name()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.name", String.class).orElse("N/A"));
        String serverVersion = config.serverInfo().version()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class).orElse("N/A"));
        info.put("serverInfo", Map.of("name", serverName, "version", serverVersion));

        Map<String, Map<String, Object>> capabilities = new HashMap<>();
        if (!promptManager.isEmpty() || metadata.isPromptManagerUsed()) {
            capabilities.put("prompts", metadata.isPromptManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        if (!toolManager.isEmpty() || metadata.isToolManagerUsed()) {
            capabilities.put("tools", metadata.isToolManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        if (!resourceManager.isEmpty() || !resourceTemplateManager.isEmpty() || metadata.isResourceManagerUsed()) {
            capabilities.put("resources", metadata.isResourceManagerUsed() ? Map.of("listChanged", true) : Map.of());
        }
        capabilities.put("logging", Map.of());
        info.put("capabilities", capabilities);
        return info;
    }

}
