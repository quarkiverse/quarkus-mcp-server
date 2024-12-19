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
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

class McpMessagesHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(McpMessagesHandler.class);

    private final ConnectionManager connectionManager;

    private final ToolMessageHandler toolHandler;

    private final PromptMessageHandler promptHandler;

    private final ResourceMessageHandler resourceHandler;

    private final McpRuntimeConfig config;

    private final Map<String, Object> serverInfo;

    private final TrafficLogger trafficLogger;

    McpMessagesHandler(McpRuntimeConfig config, ConnectionManager connectionManager, PromptManager promptManager,
            ToolManager toolManager, ResourceManager resourceManager) {
        this.connectionManager = connectionManager;
        this.toolHandler = new ToolMessageHandler(toolManager);
        this.promptHandler = new PromptMessageHandler(promptManager);
        this.resourceHandler = new ResourceMessageHandler(resourceManager);
        this.config = config;
        this.serverInfo = serverInfo(promptManager, toolManager, resourceManager);
        this.trafficLogger = config.trafficLogging().enabled() ? new TrafficLogger(config.trafficLogging().textLimit()) : null;
    }

    @Override
    public void handle(RoutingContext ctx) {
        Responder responder = new Responder(trafficLogger, ctx);
        HttpServerRequest request = ctx.request();
        String id = ctx.pathParam("id");
        if (id == null) {
            responder.badRequest("Connection id is missing");
            return;
        }
        if (request.method() != HttpMethod.POST) {
            ctx.response().putHeader(HttpHeaders.ALLOW, "POST");
            responder.failure(405, id, "Invalid HTTP method %s [connectionId: %s]", ctx.request().method(), id);
            return;
        }
        McpConnectionImpl connection = connectionManager.get(id);
        if (connection == null) {
            responder.badRequest("Connection %s not found", id);
            return;
        }

        JsonObject message = ctx.body().asJsonObject();
        if (trafficLogger != null) {
            trafficLogger.messageReceived(message);
        }
        String jsonrpc = message.getString("jsonrpc");
        if (!JsonRPC.VERSION.equals(jsonrpc)) {
            responder.badRequest("Invalid jsonrpc version %s [connectionId: %s]", message, id);
            return;
        }

        switch (connection.status()) {
            case NEW -> initializeNew(message, responder, connection);
            case INITIALIZING -> initializing(message, responder, connection);
            case IN_OPERATION -> operation(message, responder, connection);
            case SHUTDOWN -> ctx.fail(400);
        }
    }

    private void initializeNew(JsonObject message, Responder responder, McpConnectionImpl connection) {
        // The first message must be "initialize"
        String method = message.getString("method");
        if (!INITIALIZE.equals(method)) {
            responder.badRequest("The first message from the client must be \"initialize\": %s", method);
            return;
        }
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        if (params == null) {
            responder.badRequest("Initialization params not found");
            return;
        }
        // TODO schema validation?
        if (connection.initialize(decodeInitializeRequest(params))) {
            // The server MUST respond with its own capabilities and information
            responder.ok(Messages.newResult(id, serverInfo));
        } else {
            responder.error("Unable to initialize connection [connectionId: %s]", connection.id());
        }
    }

    private void initializing(JsonObject message, Responder responder, McpConnectionImpl connection) {
        String method = message.getString("method");
        if (NOTIFICATIONS_INITIALIZED.equals(method)) {
            if (connection.initialized()) {
                responder.ok();
                LOG.infof("Client successfully initialized [%s]", connection.id());
            }
        } else if (PING.equals(method)) {
            ping(message, responder);
        } else {
            responder.badRequest("Client not initialized yet [%s]", connection.id());
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
    // non-standard messages
    private static final String Q_CLOSE = "q/close";

    private void operation(JsonObject message, Responder responder, McpConnectionImpl connection) {
        String method = message.getString("method");
        switch (method) {
            case PROMPTS_LIST -> promptHandler.promptsList(message, responder);
            case PROMPTS_GET -> promptHandler.promptsGet(message, responder, connection);
            case TOOLS_LIST -> toolHandler.toolsList(message, responder);
            case TOOLS_CALL -> toolHandler.toolsCall(message, responder, connection);
            case PING -> ping(message, responder);
            case RESOURCES_LIST -> resourceHandler.resourcesList(message, responder);
            case RESOURCES_READ -> resourceHandler.resourcesRead(message, responder, connection);
            case Q_CLOSE -> close(responder, connection);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
    }

    private void ping(JsonObject message, Responder responder) {
        // https://spec.modelcontextprotocol.io/specification/basic/utilities/ping/
        Object id = message.getValue("id");
        LOG.infof("Ping [id: %s]", id);
        responder.ok(Messages.newResult(id, new JsonObject()));
    }

    private void close(Responder responder, McpConnectionImpl connection) {
        if (connectionManager.remove(connection.id())) {
            LOG.infof("Connection %s closed", connection.id());
            responder.ok();
        } else {
            responder.badRequest("Unable to obain connection to be closed: %s", connection.id());
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

    static void setJsonContentType(RoutingContext ctx) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
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

    class Responder {

        final RoutingContext ctx;
        final TrafficLogger trafficLogger;

        Responder(TrafficLogger trafficLogger, RoutingContext ctx) {
            this.trafficLogger = trafficLogger;
            this.ctx = ctx;
        }

        void ok() {
            ctx.end();
        }

        void ok(JsonObject message) {
            if (trafficLogger != null) {
                trafficLogger.messageSent(message);
            }
            setJsonContentType(ctx);
            ctx.end(message.toBuffer());
        }

        void badRequest(String logMessage, Object... params) {
            LOG.errorf(logMessage, params);
            ctx.fail(400);
        }

        void badRequest(Throwable throwable, String logMessage, Object... params) {
            LOG.errorf(throwable, logMessage, params);
            ctx.fail(400);
        }

        void error(String logMessage, Object... params) {
            LOG.errorf(logMessage, params);
            ctx.fail(500);
        }

        void failure(int statusCode, String logMessage, Object... params) {
            LOG.errorf(logMessage, params);
            ctx.fail(statusCode);
        }

    }
}
