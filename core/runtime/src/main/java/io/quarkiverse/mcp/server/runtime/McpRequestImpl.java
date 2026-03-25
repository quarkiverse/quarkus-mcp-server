package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection.Status;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.runtime.tracing.McpRequestInfo;
import io.quarkiverse.mcp.server.runtime.tracing.McpResponseInfo;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.vertx.core.json.JsonObject;

public abstract class McpRequestImpl<CONNECTION extends McpConnectionBase> implements McpRequest {

    private final String serverName;
    private final Object json;
    private final CONNECTION connection;
    private final Sender sender;
    private final SecuritySupport securitySupport;
    private final ContextSupport requestContextSupport;

    private final ManagedContext requestContext;
    private final CurrentIdentityAssociation currentIdentityAssociation;

    // Tracing span - started by prepareTracing(), ended by contextEnd()
    private volatile McpTracingSpan tracingSpan;

    public McpRequestImpl(String serverName, Object json, CONNECTION connection, Sender sender,
            SecuritySupport securitySupport,
            ContextSupport requestContextSupport, CurrentIdentityAssociation currentIdentityAssociation) {
        this.serverName = serverName;
        this.json = json;
        this.connection = connection;
        this.sender = sender;
        this.securitySupport = securitySupport;
        this.requestContextSupport = requestContextSupport;
        this.requestContext = Arc.container().requestContext();
        this.currentIdentityAssociation = currentIdentityAssociation;
    }

    @Override
    public String serverName() {
        return serverName;
    }

    @Override
    public Object json() {
        return json;
    }

    @Override
    public CONNECTION connection() {
        return connection;
    }

    @Override
    public Sender sender() {
        return sender;
    }

    @Override
    public SecuritySupport securitySupport() {
        return securitySupport;
    }

    @Override
    public ContextSupport contextSupport() {
        return requestContextSupport;
    }

    @Override
    public void prepareTracing(McpTracing mcpTracing, McpMethod method, JsonObject message,
            InitialRequest.Transport transport) {
        if (mcpTracing != null) {
            tracingSpan = mcpTracing.startSpan(method, message, this, transport);
        }
    }

    @Override
    public void setTracingErrorResponse(boolean toolError, Integer jsonRpcErrorCode, String errorMessage) {
        if (tracingSpan != null) {
            McpRequestInfo ri = tracingSpan.requestInfo();
            if (ri != null) {
                ri.setResponseInfo(new McpResponseInfo(toolError, jsonRpcErrorCode, errorMessage));
            }
        }
    }

    @Override
    public void contextStart() {
        final SecuritySupport securitySupport = securitySupport();
        final ContextSupport contextSupport = contextSupport();
        if (requestContext.isActive()) {
            if (securitySupport != null && currentIdentityAssociation != null) {
                securitySupport.setCurrentIdentity(currentIdentityAssociation);
            }
        } else {
            requestContext.activate();
            if (contextSupport != null) {
                contextSupport.requestContextActivated();
            }
            if (securitySupport != null && currentIdentityAssociation != null) {
                securitySupport.setCurrentIdentity(currentIdentityAssociation);
            }
        }
    }

    @Override
    public void endTracing(Throwable error) {
        if (tracingSpan != null) {
            tracingSpan.end(error);
            tracingSpan = null;
        }
    }

    @Override
    public void contextEnd(Throwable error) {
        endTracing(error);
        requestContext.terminate();
    }

    @Override
    public String protocolVersion() {
        if ((connection.status() == Status.IN_OPERATION || connection.status() == Status.INITIALIZING)
                && connection.initialRequest() != null) {
            return connection.initialRequest().protocolVersion();
        }
        return null;
    }

}
