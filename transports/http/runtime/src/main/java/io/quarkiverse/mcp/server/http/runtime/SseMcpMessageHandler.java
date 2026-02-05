package io.quarkiverse.mcp.server.http.runtime;

import java.util.List;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.http.runtime.SseMcpMessageHandler.SseMcpRequest;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpMetrics;
import io.quarkiverse.mcp.server.runtime.McpRequestImpl;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.runtime.SecuritySupport;
import io.quarkiverse.mcp.server.runtime.Sender;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.arc.All;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class SseMcpMessageHandler extends McpMessageHandler<SseMcpRequest> implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(SseMcpMessageHandler.class);

    private final CurrentVertxRequest currentVertxRequest;

    private final CurrentIdentityAssociation currentIdentityAssociation;

    protected SseMcpMessageHandler(McpServersRuntimeConfig config,
            ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager,
            ResourceManagerImpl resourceManager,
            PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompleteManager,
            NotificationManagerImpl initManager,
            ResponseHandlers serverRequests,
            @All List<InitialCheck> initialChecks,
            CurrentVertxRequest currentVertxRequest,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            McpMetadata metadata,
            Vertx vertx,
            Instance<McpMetrics> metrics) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, initManager, serverRequests, metadata, vertx,
                initialChecks, metrics.isResolvable() ? metrics.get() : null);
        this.currentVertxRequest = currentVertxRequest;
        this.currentIdentityAssociation = currentIdentityAssociation.isResolvable() ? currentIdentityAssociation.get() : null;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String serverName = ctx.get(HttpMcpServerRecorder.CONTEXT_KEY);
        if (serverName == null) {
            throw new IllegalStateException("Server name not defined");
        }

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
        McpConnectionBase conn = connectionManager.get(connectionId);
        if (conn == null) {
            LOG.errorf("Connection not found: %s", connectionId);
            ctx.fail(404);
            return;
        }
        SseMcpConnection connection;
        if (conn instanceof SseMcpConnection sse) {
            connection = sse;
        } else {
            throw new IllegalStateException("Invalid connection type: " + conn.getClass().getName());
        }

        Object json;
        try {
            json = Json.decodeValue(ctx.body().buffer());
        } catch (Exception e) {
            String msg = "Unable to parse the JSON message";
            LOG.errorf(e, msg);
            connection.sendError(null, JsonRpcErrorCodes.PARSE_ERROR, msg);
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

        SseMcpRequest mcpRequest = new SseMcpRequest(serverName, json, connection, connection, securitySupport,
                contextSupport,
                currentIdentityAssociation);
        handle(mcpRequest).onComplete(ar -> {
            if (ar.succeeded()) {
                ctx.end();
            } else {
                ctx.response().setStatusCode(500).end();
            }
        });
    }

    @Override
    protected Transport transport() {
        return Transport.SSE;
    }

    static class SseMcpRequest extends McpRequestImpl<SseMcpConnection> {

        SseMcpRequest(String serverName, Object json, SseMcpConnection connection, Sender sender,
                SecuritySupport securitySupport, ContextSupport requestContextSupport,
                CurrentIdentityAssociation currentIdentityAssociation) {
            super(serverName, json, connection, sender, securitySupport, requestContextSupport, currentIdentityAssociation);
        }

    }

}
