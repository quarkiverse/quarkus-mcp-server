package io.quarkiverse.mcp.server.websocket.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.inject.Instance;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.InitialResponseInfo;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.runtime.CancellationRequests;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpMetrics;
import io.quarkiverse.mcp.server.runtime.McpRequestImpl;
import io.quarkiverse.mcp.server.runtime.McpRequestValidator;
import io.quarkiverse.mcp.server.runtime.McpTracing;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.SecuritySupport;
import io.quarkiverse.mcp.server.runtime.ServerRequests;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.TrafficListeners;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkiverse.mcp.server.websocket.runtime.WebSocketMcpMessageHandler.WebSocketMcpRequest;
import io.quarkus.arc.All;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * A subclass is generated for each MCP server endpoint.
 */
public abstract class WebSocketMcpMessageHandler extends McpMessageHandler<WebSocketMcpRequest> {

    private static final Logger LOG = Logger.getLogger(WebSocketMcpMessageHandler.class);

    CurrentIdentityAssociation currentIdentityAssociation;

    TrafficListeners trafficListeners;

    protected WebSocketMcpMessageHandler(McpServersRuntimeConfig config,
            ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager,
            ResourceManagerImpl resourceManager,
            PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompleteManager,
            NotificationManagerImpl initManager,
            ServerRequests serverRequests,
            CancellationRequests cancellationRequests,
            McpMetadata metadata,
            Vertx vertx,
            @All List<InitialCheck> initialChecks,
            @All List<InitialResponseInfo> initialResponseInfos,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            Instance<McpMetrics> metrics,
            Instance<McpTracing> tracing,
            Instance<McpRequestValidator> mcpRequestValidator,
            TrafficListeners trafficListeners) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, initManager, serverRequests, metadata, vertx,
                initialChecks, initialResponseInfos, metrics.isResolvable() ? metrics.get() : null,
                tracing.isResolvable() ? tracing.get() : null,
                mcpRequestValidator.isResolvable() ? mcpRequestValidator.get() : null, cancellationRequests);
        this.currentIdentityAssociation = currentIdentityAssociation.isResolvable() ? currentIdentityAssociation.get() : null;
        this.trafficListeners = trafficListeners;
    }

    protected abstract String serverName();

    private final ConcurrentMap<String, WebSocketMcpConnection> connections = new ConcurrentHashMap<>();

    @OnOpen
    void openConnection(WebSocketConnection connection) {
        LOG.debugf("MCP WebSocket connection open [id: %s]", connection.id());
    }

    @SuppressWarnings("unchecked")
    @OnTextMessage
    Uni<Void> consumeMessage(WebSocketConnection connection, String message) throws InterruptedException {
        JsonObject jsonMessage = (JsonObject) Json.decodeValue(message);

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

        WebSocketMcpConnection mcpConnection;
        JsonObject meta = findMeta(jsonMessage);
        if (isStatelessMessage(jsonMessage, meta)) {
            mcpConnection = new WebSocketMcpConnection(ConnectionManager.transientConnectionId(),
                    config.servers().get(serverName()), serverName(), trafficListeners, connection, true);
            String metaVersion = meta != null ? meta.getString(MetaKey.PROTOCOL_VERSION.toString()) : null;
            InitialRequest initialRequest;
            try {
                initialRequest = buildStatelessInitialRequest(meta, metaVersion, Transport.WEBSOCKET);
                applyMetaLogLevel(meta, mcpConnection);
            } catch (McpException e) {
                return (Uni<Void>) UniHelper
                        .toUni(mcpConnection.sendError(Messages.getId(jsonMessage), e.getJsonRpcErrorCode(), e.getMessage()));
            }
            mcpConnection.initialize(initialRequest);
            mcpConnection.setInitialized();
        } else {
            mcpConnection = connections.computeIfAbsent(connection.id(), k -> newConnection(connection));
        }

        WebSocketMcpRequest mcpRequest = new WebSocketMcpRequest(serverName(), jsonMessage, mcpConnection, securitySupport,
                null,
                currentIdentityAssociation);
        return (Uni<Void>) UniHelper.toUni(handle(mcpRequest));
    }

    @OnClose
    void closeConnection(WebSocketConnection connection) {
        WebSocketMcpConnection mcpConnection = connections.remove(connection.id());
        if (mcpConnection != null) {
            connectionManager.remove(mcpConnection.id());
            LOG.debugf("MCP WebSocket connection closed [mcpId: %s, id: %s]", mcpConnection.id(), connection.id());
        }
        // Clean up any transient subscription connections registered for this WebSocket
        List<String> toRemove = new ArrayList<>();
        for (McpConnectionBase c : connectionManager) {
            if (c instanceof WebSocketMcpConnection wsc
                    && wsc.webSocketConnection().id().equals(connection.id())
                    && wsc.isTransient()) {
                toRemove.add(c.id());
            }
        }
        for (String id : toRemove) {
            connectionManager.remove(id);
            LOG.debugf("Transient subscription connection removed [mcpId: %s, wsId: %s]", id, connection.id());
        }
    }

    @Override
    protected Transport transport() {
        return Transport.WEBSOCKET;
    }

    private WebSocketMcpConnection newConnection(WebSocketConnection connection) {
        String id = ConnectionManager.connectionId();
        WebSocketMcpConnection mcpConnection = new WebSocketMcpConnection(id, config.servers().get(serverName()), serverName(),
                trafficListeners, connection);
        connectionManager.add(mcpConnection);
        LOG.debugf("MCP WebSocket connection initialized [mcpId: %s, id: %s]", mcpConnection.id(), connection.id());
        return mcpConnection;
    }

    static class WebSocketMcpRequest extends McpRequestImpl<WebSocketMcpConnection> {

        WebSocketMcpRequest(String serverName, JsonObject message, WebSocketMcpConnection connection,
                SecuritySupport securitySupport, ContextSupport requestContextSupport,
                CurrentIdentityAssociation currentIdentityAssociation) {
            super(serverName, message, connection, connection, securitySupport, requestContextSupport,
                    currentIdentityAssociation);
        }

    }

}
