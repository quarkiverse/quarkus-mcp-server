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
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.vertx.core.json.JsonObject;

public class McpMessageHandler {

    private static final Logger LOG = Logger.getLogger(McpMessageHandler.class);

    protected final ConnectionManager connectionManager;

    private final ToolMessageHandler toolHandler;

    private final PromptMessageHandler promptHandler;

    private final PromptCompletionMessageHandler promptCompleteHandler;

    private final ResourceMessageHandler resourceHandler;

    protected final McpRuntimeConfig config;

    private final Map<String, Object> serverInfo;

    protected McpMessageHandler(McpRuntimeConfig config, ConnectionManager connectionManager, PromptManager promptManager,
            ToolManager toolManager, ResourceManager resourceManager, PromptCompleteManager promptCompleteManager) {
        this.connectionManager = connectionManager;
        this.toolHandler = new ToolMessageHandler(toolManager);
        this.promptHandler = new PromptMessageHandler(promptManager);
        this.promptCompleteHandler = new PromptCompletionMessageHandler(promptCompleteManager);
        this.resourceHandler = new ResourceMessageHandler(resourceManager);
        this.config = config;
        this.serverInfo = serverInfo(promptManager, toolManager, resourceManager);
    }

    public void handle(JsonObject message, McpConnection connection, Responder responder) {
        switch (connection.status()) {
            case NEW -> initializeNew(message, responder, connection);
            case INITIALIZING -> initializing(message, responder, connection);
            case IN_OPERATION -> operation(message, responder, connection);
            case SHUTDOWN -> responder.send(
                    Messages.newError(message.getValue("id"), JsonRPC.INTERNAL_ERROR, "Connection was already shut down"));
        }
    }

    private void initializeNew(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        // The first message must be "initialize"
        String method = message.getString("method");
        if (!INITIALIZE.equals(method)) {
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
                LOG.infof("Client successfully initialized [%s]", connection.id());
            }
        } else if (PING.equals(method)) {
            ping(message, responder);
        } else {
            responder.send(Messages.newError(message.getValue("id"), JsonRPC.INTERNAL_ERROR,
                    "Client not initialized yet [" + connection.id() + "]"));
        }
    }

    private static final String INITIALIZE = "initialize";
    private static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    private static final String PROMPTS_LIST = "prompts/list";
    private static final String PROMPTS_GET = "prompts/get";
    private static final String TOOLS_LIST = "tools/list";
    private static final String TOOLS_CALL = "tools/call";
    private static final String RESOURCES_LIST = "resources/list";
    private static final String RESOURCES_READ = "resources/read";
    private static final String PING = "ping";
    private static final String COMPLETION_COMPLETE = "completion/complete";
    // non-standard messages
    private static final String Q_CLOSE = "q/close";

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
            case COMPLETION_COMPLETE -> complete(message, responder, connection);
            case Q_CLOSE -> close(message, responder, connection);
            default -> responder.send(
                    Messages.newError(message.getValue("id"), JsonRPC.METHOD_NOT_FOUND, "Unsupported method: " + method));
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
                        promptCompleteHandler.promptComplete(id, ref, argument, responder, connection);
                    } else {
                        responder.sendError(id, JsonRPC.INVALID_REQUEST,
                                "Unsupported reference found: " + ref.getString("type"));
                    }
                }
            }
        }
    }

    private void ping(JsonObject message, Responder responder) {
        // https://spec.modelcontextprotocol.io/specification/basic/utilities/ping/
        Object id = message.getValue("id");
        LOG.infof("Ping [id: %s]", id);
        responder.sendResult(id, new JsonObject());
    }

    private void close(JsonObject message, Responder responder, McpConnection connection) {
        if (connectionManager.remove(connection.id())) {
            LOG.infof("Connection %s closed", connection.id());
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

    private Map<String, Object> serverInfo(PromptManager promptManager, ToolManager toolManager,
            ResourceManager resourceManager) {
        Map<String, Object> info = new HashMap<>();
        info.put("protocolVersion", "2024-11-05");

        String serverName = config.serverInfo().name()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.name", String.class).orElse("N/A"));
        String serverVersion = config.serverInfo().version()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class).orElse("N/A"));
        info.put("serverInfo", Map.of("name", serverName, "version", serverVersion));

        Map<String, Map<String, Object>> capabilities = new HashMap<>();
        if (!promptManager.isEmpty()) {
            capabilities.put("prompts", Map.of());
        }
        if (!toolManager.isEmpty()) {
            capabilities.put("tools", Map.of());
        }
        if (!resourceManager.isEmpty()) {
            capabilities.put("resources", Map.of());
        }
        info.put("capabilities", capabilities);
        return info;
    }

}
