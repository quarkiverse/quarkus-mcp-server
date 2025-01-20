package io.quarkiverse.mcp.server.sse.runtime;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.PromptCompleteManager;
import io.quarkiverse.mcp.server.runtime.PromptManager;
import io.quarkiverse.mcp.server.runtime.ResourceManager;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompleteManager;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManager;
import io.quarkiverse.mcp.server.runtime.ToolManager;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class SseMcpMessageHandler extends McpMessageHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(SseMcpMessageHandler.class);

    protected SseMcpMessageHandler(McpRuntimeConfig config, ConnectionManager connectionManager, PromptManager promptManager,
            ToolManager toolManager, ResourceManager resourceManager, PromptCompleteManager promptCompleteManager,
            ResourceTemplateManager resourceTemplateManager, ResourceTemplateCompleteManager resourceTemplateCompleteManager) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager);
    }

    @Override
    public void handle(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        String connectionId = ctx.pathParam("id");
        if (connectionId == null) {
            LOG.errorf("Connection id is missing: %s", ctx.normalizedPath());
            ctx.fail(400);
            return;
        }
        if (request.method() != HttpMethod.POST) {
            ctx.response().putHeader(HttpHeaders.ALLOW, "POST");
            LOG.errorf("Invalid HTTP method %s [connectionId: %s]", ctx.request().method(), connectionId);
            ctx.fail(405);
            return;
        }
        McpConnectionBase connection = connectionManager.get(connectionId);
        if (connection == null) {
            LOG.errorf("Connection not found: %s", connectionId);
            ctx.fail(400);
            return;
        }
        JsonObject message;
        try {
            message = ctx.body().asJsonObject();
        } catch (Exception e) {
            String msg = "Unable to parse the JSON message";
            LOG.errorf(e, msg);
            connection.sendError(null, JsonRPC.PARSE_ERROR, msg);
            ctx.end();
            return;
        }
        if (connection.trafficLogger() != null) {
            connection.trafficLogger().messageReceived(message);
        }
        if (JsonRPC.validate(message, connection)) {
            handle(message, connection, connection);
        }
        ctx.end();
    }

}
