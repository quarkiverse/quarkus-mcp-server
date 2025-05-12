package io.quarkiverse.mcp.server.sse.runtime;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpRequestImpl;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompleteManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.runtime.SecuritySupport;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class SseMcpMessageHandler extends McpMessageHandler<McpRequestImpl> implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(SseMcpMessageHandler.class);

    private final CurrentVertxRequest currentVertxRequest;

    protected SseMcpMessageHandler(McpRuntimeConfig config, ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager, ResourceManagerImpl resourceManager, PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompleteManagerImpl resourceTemplateCompleteManager, NotificationManagerImpl initManager,
            ResponseHandlers serverRequests, CurrentVertxRequest currentVertxRequest,
            McpMetadata metadata) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, initManager, serverRequests, metadata);
        this.currentVertxRequest = currentVertxRequest;
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

        Object json;
        try {
            json = Json.decodeValue(ctx.body().buffer());
        } catch (Exception e) {
            String msg = "Unable to parse the JSON message";
            LOG.errorf(e, msg);
            connection.sendError(null, JsonRPC.PARSE_ERROR, msg);
            ctx.end();
            return;
        }

        QuarkusHttpUser user = (QuarkusHttpUser) ctx.user();
        SecuritySupport securitySupport = new SecuritySupport() {

            @Override
            public void setCurrentIdentity(CurrentIdentityAssociation currentIdentityAssociation) {
                if (user != null) {
                    SecurityIdentity identity = user.getSecurityIdentity();
                    currentIdentityAssociation.setIdentity(identity);
                } else {
                    currentIdentityAssociation.setIdentity(QuarkusHttpUser.getSecurityIdentity(ctx, null));
                }
            }
        };
        ContextSupport contextSupport = new ContextSupport() {
            @Override
            public void requestContextActivated() {
                currentVertxRequest.setCurrent(ctx);
            }
        };

        McpRequestImpl mcpRequest = new McpRequestImpl(json, connection, connection, securitySupport, contextSupport);
        handle(mcpRequest).onComplete(ar -> {
            if (ar.succeeded()) {
                ctx.end();
            } else {
                ctx.response().setStatusCode(500).end();
            }
        });

    }

}
