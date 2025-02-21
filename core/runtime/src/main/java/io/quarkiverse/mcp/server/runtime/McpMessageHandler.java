package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitializeRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkus.runtime.LaunchMode;
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

    protected final McpRuntimeConfig config;

    private final Map<String, Object> serverInfo;

    protected McpMessageHandler(McpRuntimeConfig config, ConnectionManager connectionManager, PromptManagerImpl promptManager,
            ToolManagerImpl toolManager, ResourceManagerImpl resourceManager, PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompleteManagerImpl resourceTemplateCompleteManager,
            McpMetadata metadata) {
        this.connectionManager = connectionManager;
        this.toolHandler = new ToolMessageHandler(toolManager, config.tools().pageSize());
        this.promptHandler = new PromptMessageHandler(promptManager, config.prompts().pageSize());
        this.promptCompleteHandler = new PromptCompleteMessageHandler(promptCompleteManager);
        this.resourceHandler = new ResourceMessageHandler(resourceManager, config.resources().pageSize());
        this.resourceTemplateHandler = new ResourceTemplateMessageHandler(resourceTemplateManager,
                config.resourceTemplates().pageSize());
        this.resourceTemplateCompleteHandler = new ResourceTemplateCompleteMessageHandler(resourceTemplateCompleteManager);
        this.config = config;
        this.serverInfo = serverInfo(promptManager, toolManager, resourceManager, resourceTemplateManager, metadata);
    }

    public void handle(JsonObject message, McpConnection connection, Responder responder) {
        if (Messages.isResponse(message)) {
            // Response from a client
            // Currently we discard all responses, including pong responses
            LOG.debugf("Discard client response: %s", message);
        } else {
            switch (connection.status()) {
                case NEW -> initializeNew(message, responder, connection);
                case INITIALIZING -> initializing(message, responder, connection);
                case IN_OPERATION -> operation(message, responder, connection);
                case SHUTDOWN -> responder.send(
                        Messages.newError(message.getValue("id"), JsonRPC.INTERNAL_ERROR, "Connection was already shut down"));
            }
        }
    }

    private void initializeNew(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        // The first message must be "initialize"
        String method = message.getString("method");
        if (!INITIALIZE.equals(method)) {
            // In the dev mode, if an MCP client attempts to reconnect an SSE connection but does not reinitialize propertly,
            // we could perform a "dummy" initialization
            if (LaunchMode.current() == LaunchMode.DEVELOPMENT && config.devMode().dummyInit()) {
                InitializeRequest dummy = new InitializeRequest(new Implementation("dummy", "1"), DEFAULT_PROTOCOL_VERSION,
                        List.of());
                if (connection.initialize(dummy) && connection.setInitialized()) {
                    LOG.infof("Connection initialized with dummy info [%s]", connection.id());
                    operation(message, responder, connection);
                    return;
                }
            }
            responder.sendError(id, JsonRPC.METHOD_NOT_FOUND,
                    "The first message from the client must be \"initialize\": " + method);
            return;
        }
        JsonObject params = message.getJsonObject("params");
        if (params == null) {
            responder.sendError(id, JsonRPC.INVALID_PARAMS, "Initialization params not found");
            return;
        }
        // TODO schema validation?
        if (connection.initialize(decodeInitializeRequest(params))) {
            // The server MUST respond with its own capabilities and information
            responder.sendResult(id, serverInfo);
        } else {
            responder.sendError(id, JsonRPC.INTERNAL_ERROR,
                    "Unable to initialize connection [connectionId: " + connection.id() + "]");
        }
    }

    private void initializing(JsonObject message, Responder responder, McpConnection connection) {
        String method = message.getString("method");
        if (NOTIFICATIONS_INITIALIZED.equals(method)) {
            if (connection.setInitialized()) {
                LOG.debugf("Client successfully initialized [%s]", connection.id());
            }
        } else if (PING.equals(method)) {
            ping(message, responder);
        } else {
            responder.send(Messages.newError(message.getValue("id"), JsonRPC.INTERNAL_ERROR,
                    "Client not initialized yet [" + connection.id() + "]"));
        }
    }

    static final String INITIALIZE = "initialize";
    static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    static final String NOTIFICATIONS_MESSAGE = "notifications/message";
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
    static final String COMPLETION_COMPLETE = "completion/complete";
    static final String LOGGING_SET_LEVEL = "logging/setLevel";
    // non-standard messages
    static final String Q_CLOSE = "q/close";

    private void operation(JsonObject message, Responder responder, McpConnection connection) {
        String method = message.getString("method");
        switch (method) {
            case PROMPTS_LIST -> promptHandler.promptsList(message, responder);
            case PROMPTS_GET -> promptHandler.promptsGet(message, responder, connection);
            case TOOLS_LIST -> toolHandler.toolsList(message, responder);
            case TOOLS_CALL -> toolHandler.toolsCall(message, responder, connection);
            case PING -> ping(message, responder);
            case RESOURCES_LIST -> resourceHandler.resourcesList(message, responder);
            case RESOURCES_READ -> resourceHandler.resourcesRead(message, responder, connection);
            case RESOURCES_SUBSCRIBE -> resourceHandler.resourcesSubscribe(message, responder, connection);
            case RESOURCES_UNSUBSCRIBE -> resourceHandler.resourcesUnsubscribe(message, responder, connection);
            case RESOURCE_TEMPLATES_LIST -> resourceTemplateHandler.resourceTemplatesList(message, responder);
            case COMPLETION_COMPLETE -> complete(message, responder, connection);
            case LOGGING_SET_LEVEL -> setLogLevel(message, responder, connection);
            case Q_CLOSE -> close(message, responder, connection);
            default -> responder.send(
                    Messages.newError(message.getValue("id"), JsonRPC.METHOD_NOT_FOUND, "Unsupported method: " + method));
        }
    }

    private void setLogLevel(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String level = params.getString("level");
        if (level == null) {
            responder.sendError(id, JsonRPC.INVALID_REQUEST, "Log level not set");
        } else {
            LogLevel logLevel = LogLevel.from(level);
            if (logLevel == null) {
                responder.sendError(id, JsonRPC.INVALID_REQUEST, "Invalid log level set: " + level);
            } else {
                if (connection instanceof McpConnectionBase connectionBase) {
                    connectionBase.setLogLevel(logLevel);
                    // Send empty result
                    responder.sendResult(id, new JsonObject());
                } else {
                    throw new IllegalStateException();
                }
            }
        }

    }

    private void complete(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        JsonObject ref = params.getJsonObject("ref");
        if (ref == null) {
            responder.sendError(id, JsonRPC.INVALID_REQUEST, "Reference not found");
        } else {
            String referenceType = ref.getString("type");
            if (referenceType == null) {
                responder.sendError(id, JsonRPC.INVALID_REQUEST, "Reference type not found");
            } else {
                JsonObject argument = params.getJsonObject("argument");
                if (argument == null) {
                    responder.sendError(id, JsonRPC.INVALID_REQUEST, "Argument not found");
                } else {
                    if ("ref/prompt".equals(referenceType)) {
                        promptCompleteHandler.complete(id, ref, argument, responder, connection);
                    } else if ("ref/resource".equals(referenceType)) {
                        resourceTemplateCompleteHandler.complete(id, ref, argument, responder, connection);
                    } else {
                        responder.sendError(id, JsonRPC.INVALID_REQUEST,
                                "Unsupported reference found: " + ref.getString("type"));
                    }
                }
            }
        }
    }

    private void ping(JsonObject message, Responder responder) {
        // https://spec.modelcontextprotocol.io/specification/2024-11-05/basic/utilities/ping/
        Object id = message.getValue("id");
        LOG.debugf("Ping [id: %s]", id);
        responder.sendResult(id, new JsonObject());
    }

    private void close(JsonObject message, Responder responder, McpConnection connection) {
        if (connectionManager.remove(connection.id())) {
            LOG.debugf("Connection %s explicitly closed ", connection.id());
        } else {
            responder.sendError(message.getValue("id"), JsonRPC.INTERNAL_ERROR,
                    "Unable to obtain the connection to be closed:" + connection.id());
        }
    }

    private InitializeRequest decodeInitializeRequest(JsonObject params) {
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
        return new InitializeRequest(implementation, protocolVersion, clientCapabilities);
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
