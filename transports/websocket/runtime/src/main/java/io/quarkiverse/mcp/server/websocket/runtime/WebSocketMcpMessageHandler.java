package io.quarkiverse.mcp.server.websocket.runtime;

import java.util.List;

import jakarta.enterprise.inject.Instance;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpRequestImpl;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.runtime.SecuritySupport;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkiverse.mcp.server.websocket.runtime.WebSocketMcpMessageHandler.WebSocketMcpRequest;
import io.quarkus.arc.All;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.UserData.TypedKey;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;

/**
 * A subclass is generated for each MCP server endpoint.
 */
public abstract class WebSocketMcpMessageHandler extends McpMessageHandler<WebSocketMcpRequest> {

    private static final Logger LOG = Logger.getLogger(WebSocketMcpMessageHandler.class);

    private static final String MCP_CONNECTION_ID = "mcpConnectionId";

    CurrentIdentityAssociation currentIdentityAssociation;

    protected WebSocketMcpMessageHandler(McpServersRuntimeConfig config,
            ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager,
            ResourceManagerImpl resourceManager,
            PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompleteManager,
            NotificationManagerImpl initManager,
            ResponseHandlers responseHandlers,
            McpMetadata metadata,
            Vertx vertx,
            @All List<InitialCheck> initialChecks,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, initManager, responseHandlers, metadata, vertx,
                initialChecks);
        this.currentIdentityAssociation = currentIdentityAssociation.isResolvable() ? currentIdentityAssociation.get() : null;
    }

    protected abstract String serverName();

    @OnOpen
    void openConnection(WebSocketConnection connection) {
        String id = ConnectionManager.connectionId();
        WebSocketMcpConnection mcpConnection = new WebSocketMcpConnection(id, config.servers().get(serverName()), connection);
        connectionManager.add(mcpConnection);
        LOG.debugf("WebSocket connection initialized [%s]", id);
        connection.userData().put(TypedKey.forString(MCP_CONNECTION_ID), id);
    }

    @SuppressWarnings("unchecked")
    @OnTextMessage
    Uni<Void> consumeMessage(WebSocketConnection connection, String message) {
        String connectionId = connection.userData().get(TypedKey.forString(MCP_CONNECTION_ID));
        WebSocketMcpConnection mcpConnection = (WebSocketMcpConnection) connectionManager.get(connectionId);
        Object json = Json.decodeValue(message);

        SecuritySupport securitySupport;
        if (currentIdentityAssociation != null) {
            SecurityIdentity securityIdentity = currentIdentityAssociation.getIdentity();
            securitySupport = new SecuritySupport() {
                @Override
                public void setCurrentIdentity(CurrentIdentityAssociation currentIdentityAssociation) {
                    currentIdentityAssociation.setIdentity(securityIdentity);
                }
            };
        } else {
            securitySupport = null;
        }

        WebSocketMcpRequest mcpRequest = new WebSocketMcpRequest(serverName(), json, mcpConnection, securitySupport, null,
                currentIdentityAssociation);
        return (Uni<Void>) UniHelper.toUni(handle(mcpRequest));
    }

    @OnClose
    void closeConnection(WebSocketConnection connection) {
        String id = connection.userData().get(TypedKey.forString(MCP_CONNECTION_ID));
        connectionManager.remove(id);
        LOG.debugf("WebSocket connection closed [%s]", id);
    }

    @Override
    protected Transport transport() {
        return Transport.WEBSOCKET;
    }

    static class WebSocketMcpRequest extends McpRequestImpl<WebSocketMcpConnection> {

        WebSocketMcpRequest(String serverName, Object json, WebSocketMcpConnection connection,
                SecuritySupport securitySupport, ContextSupport requestContextSupport,
                CurrentIdentityAssociation currentIdentityAssociation) {
            super(serverName, json, connection, connection, securitySupport, requestContextSupport, currentIdentityAssociation);
        }

    }

}
