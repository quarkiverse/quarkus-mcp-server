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

    private final McpBuildTimeConfig config;

    McpMessagesHandler(ConnectionManager connectionManager, McpBuildTimeConfig config) {
        this.connectionManager = connectionManager;
        this.toolHandler = new ToolMessageHandler();
        this.promptHandler = new PromptMessageHandler();
        this.config = config;
    }

    @Override
    public void handle(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        String id = ctx.request().getParam("id");
        if (id == null) {
            LOG.error("Connection id is missing");
            ctx.fail(400);
            return;
        }
        if (request.method() != HttpMethod.POST) {
            LOG.errorf("Invalid HTTP method %s [connectionId: %s]", ctx.request().method(), id);
            ctx.fail(400);
            return;
        }
        McpConnectionImpl connection = connectionManager.get(id);
        if (connection == null) {
            LOG.errorf("Connection %s not found", id);
            ctx.fail(400);
            return;
        }

        JsonObject message = ctx.body().asJsonObject();
        String jsonrpc = message.getString("jsonrpc");
        if (!"2.0".equals(jsonrpc)) {
            LOG.errorf("Invalid jsonrpc version %s [connectionId: %s]", message, id);
            ctx.fail(400);
            return;
        }

        switch (connection.status()) {
            case NEW -> initializeNew(message, ctx, connection);
            case INITIALIZING -> initializing(message, ctx, connection);
            case IN_OPERATION -> operation(message, ctx, connection);
            case SHUTDOWN -> ctx.fail(400);
        }
    }

    private void initializeNew(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        // The first message must be "initialize"
        String method = message.getString("method");
        if (!INITIALIZE.equals(method)) {
            LOG.errorf("The first message from the client must be \"initialize\": %s", method);
            ctx.fail(400);
            return;
        }
        Object id = message.getValue("id");

        JsonObject params = message.getJsonObject("params");
        if (params == null) {
            LOG.error("Required params not found");
            ctx.fail(400);
            return;
        }
        // TODO schema validation
        if (connection.initialize(decodeInitializeRequest(params))) {
            // The server MUST respond with its own capabilities and information
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.end(newResult(id, serverInfo()).encode());
        } else {
            ctx.fail(400);
        }
    }

    private void initializing(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        String method = message.getString("method");
        if (NOTIFICATIONS_INITIALIZED.equals(method)) {
            if (connection.initialized()) {
                LOG.infof("Client successfully initialized [%s]", connection.id());
                ctx.end();
            }
        } else if (PING.equals(method)) {
            ping(message, ctx);
        } else {
            LOG.infof("Client not initialized yet [%s]", connection.id());
            ctx.fail(400);
        }
    }

    private static final String INITIALIZE = "initialize";
    private static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    private static final String PROMPTS_LIST = "prompts/list";
    private static final String PROMPTS_GET = "prompts/get";
    private static final String TOOLS_LIST = "tools/list";
    private static final String TOOLS_CALL = "tools/call";
    private static final String PING = "ping";

    private void operation(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        String method = message.getString("method");
        switch (method) {
            case PROMPTS_LIST -> promptHandler.promptsList(message, ctx);
            case PROMPTS_GET -> promptHandler.promptsGet(message, ctx, connection);
            case TOOLS_LIST -> toolHandler.toolsList(message, ctx);
            case TOOLS_CALL -> toolHandler.toolsCall(message, ctx, connection);
            case PING -> ping(message, ctx);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
    }

    private void ping(JsonObject message, RoutingContext ctx) {
        // https://spec.modelcontextprotocol.io/specification/basic/utilities/ping/
        Object id = message.getValue("id");
        LOG.infof("Ping [id: %s]", id);
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        ctx.end(newResult(id, new JsonObject()).encode());
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

    static JsonObject newResult(Object id, Object result) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> serverInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("protocolVersion", "2024-11-05");

        String serverName = config.serverInfo().name()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.name", String.class).orElse("N/A"));
        String serverVersion = config.serverInfo().version()
                .orElse(ConfigProvider.getConfig().getOptionalValue("quarkus.application.version", String.class).orElse("N/A"));
        info.put("serverInfo", Map.of("name", serverName, "version", serverVersion));

        info.put("capabilities", Map.of("prompts", Map.of(), "tools", Map.of()));
        return info;
    }
}
