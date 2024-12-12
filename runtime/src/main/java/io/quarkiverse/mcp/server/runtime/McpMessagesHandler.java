package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitializeRequest;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.arc.Arc;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

class McpMessagesHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(McpMessagesHandler.class);

    private final ConnectionManager connectionManager;

    McpMessagesHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void handle(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        if (request.method() != HttpMethod.POST) {
            LOG.errorf("Invalid HTTP method: %s", ctx.request().method());
            ctx.fail(400);
            return;
        }
        String id = ctx.request().getParam("id");
        if (id == null) {
            LOG.error("Connection id is missing");
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
            LOG.errorf("Invalid jsonrpc version [%s]", message);
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
        if (!"initialize".equals(method)) {
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
        if ("notifications/initialized".equals(method)) {
            if (connection.initialized()) {
                LOG.infof("Client successfully initialized [%s]", connection.id());
                ctx.end();
            }
        } else {
            LOG.infof("Client not initialized yet [%s]", connection.id());
            ctx.fail(400);
        }
        // TODO ping
    }

    private void operation(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        String method = message.getString("method");
        switch (method) {
            case "prompts/list" -> promptsList(message, ctx);
            case "prompts/get" -> promptsGet(message, ctx, connection);
            case "tools/list" -> toolsList(message, ctx);
            case "tools/call" -> toolsCall(message, ctx, connection);
            case "ping" -> ping(message, ctx);
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

    private void promptsList(JsonObject message, RoutingContext ctx) {
        Object id = message.getValue("id");
        LOG.infof("List prompts [id: %s]", id);
        PromptManager promptManager = Arc.container().instance(PromptManager.class).get();
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        ctx.end(newResult(id, new JsonObject()
                .put("prompts", new JsonArray(promptManager.list()))).encode());
    }

    private void promptsGet(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        Object id = message.getValue("id");
        LOG.infof("Get prompts [id: %s]", id);

        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        JsonObject params = message.getJsonObject("params");
        ArgumentProviders argProviders = new ArgumentProviders(params.getJsonObject("arguments").getMap(), connection, id);

        PromptManager promptManager = Arc.container().instance(PromptManager.class).get();
        Future<PromptResponse> fu = promptManager.get(params.getString("name"), argProviders);
        fu.onComplete(new Handler<AsyncResult<PromptResponse>>() {
            @Override
            public void handle(AsyncResult<PromptResponse> ar) {
                if (ar.succeeded()) {
                    PromptResponse promptResponse = ar.result();
                    JsonObject result = new JsonObject();
                    if (promptResponse.description() != null) {
                        result.put("description", promptResponse.description());
                    }
                    result.put("messages", promptResponse.messages());
                    ctx.end(newResult(id, result).encode());
                } else {
                    LOG.error("Unable to obtain prompt", ar.cause());
                    ctx.fail(500);
                }
            }
        });
    }

    private void toolsList(JsonObject message, RoutingContext ctx) {
        Object id = message.getValue("id");
        LOG.infof("List tools [id: %s]", id);
        ToolManager toolManager = Arc.container().instance(ToolManager.class).get();
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        ctx.end(newResult(id, new JsonObject()
                .put("tools", new JsonArray(toolManager.list()))).encode());
    }

    private void toolsCall(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        Object id = message.getValue("id");
        LOG.infof("Call tool [id: %s]", id);

        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        JsonObject params = message.getJsonObject("params");
        ArgumentProviders argProviders = new ArgumentProviders(params.getJsonObject("arguments").getMap(), connection, id);

        ToolManager toolManager = Arc.container().instance(ToolManager.class).get();
        Future<ToolResponse> fu = toolManager.get(params.getString("name"), argProviders);
        fu.onComplete(new Handler<AsyncResult<ToolResponse>>() {
            @Override
            public void handle(AsyncResult<ToolResponse> ar) {
                if (ar.succeeded()) {
                    ToolResponse toolResponse = ar.result();
                    ctx.end(newResult(id, toolResponse).encode());
                } else {
                    LOG.error("Unable to obtain prompt", ar.cause());
                    ctx.fail(500);
                }
            }
        });
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

    private JsonObject newResult(Object id, Object result) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> serverInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("protocolVersion", "2024-11-05");
        info.put("serverInfo", Map.of("name", "Foo", "version", "999-SNAPSHOT"));
        info.put("capabilities", Map.of("prompts", Map.of()));
        return info;
    }
}
