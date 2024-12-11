package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitializeRequest;
import io.quarkiverse.mcp.server.PromptMessage;
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
            LOG.error("Connection id is required");
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
            LOG.errorf("Invalid jsonrpc [%s]", message);
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
            ctx.end(result(id, serverInfo()).encode());
        } else {
            ctx.fail(400);
        }
    }

    private void initializing(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        String method = message.getString("method");
        if ("notifications/initialized".equals(method)) {
            if (connection.initialized()) {
                LOG.infof("Client initialized [id: %s]", connection.id());
                ctx.end();
            }
        } else {
            LOG.infof("Client not initialized yet [id: %s]", connection.id());
            ctx.fail(400);
        }
        // TODO ping
    }

    private void operation(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        String method = message.getString("method");
        switch (method) {
            case "prompts/list" -> promptsList(message, ctx);
            case "prompts/get" -> promptsGet(message, ctx);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        }
    }

    private void promptsList(JsonObject message, RoutingContext ctx) {
        LOG.infof("List prompts");
        PromptManager promptManager = Arc.container().instance(PromptManager.class).get();
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        ctx.end(result(message.getValue("id"), new JsonObject()
                .put("prompts", new JsonArray(promptManager.list()))).encode());
    }

    private void promptsGet(JsonObject message, RoutingContext ctx) {
        LOG.infof("Get prompts");
        PromptManager promptManager = Arc.container().instance(PromptManager.class).get();
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        JsonObject params = message.getJsonObject("params");
        Future<List<PromptMessage>> fu = promptManager.get(params.getString("name"),
                params.getJsonObject("arguments").getMap());
        fu.onComplete(new Handler<AsyncResult<List<PromptMessage>>>() {
            @Override
            public void handle(AsyncResult<List<PromptMessage>> ar) {
                if (ar.succeeded()) {
                    ctx.end(result(message.getValue("id"), new JsonObject()
                            .put("messages", new JsonArray(ar.result())))
                            .encode());
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

    private JsonObject result(Object id, Object result) {
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
