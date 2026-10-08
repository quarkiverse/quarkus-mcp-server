package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.runtime.tracing.McpRequestInfo;
import io.quarkiverse.mcp.server.runtime.tracing.McpResponseInfo;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.vertx.core.json.JsonObject;

public abstract class McpRequestImpl<CONNECTION extends McpConnectionBase> implements McpRequest {

    private final String serverName;
    private final JsonObject message;
    private final CONNECTION connection;
    private final Sender sender;
    private final SecuritySupport securitySupport;
    private final ContextSupport requestContextSupport;

    private final ManagedContext requestContext;
    private final CurrentIdentityAssociation currentIdentityAssociation;

    // Ordinary requests own one context; initialized notifications keep their contexts locally
    private volatile McpRequestContext ownedRequestContext;

    // Tracing span - started by prepareTracing(), ended by contextEnd()
    private volatile McpTracingSpan tracingSpan;

    public McpRequestImpl(String serverName, JsonObject message, CONNECTION connection, Sender sender,
            SecuritySupport securitySupport,
            ContextSupport requestContextSupport, CurrentIdentityAssociation currentIdentityAssociation) {
        this.serverName = serverName;
        this.message = message;
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
    public JsonObject message() {
        return message;
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
        ownedRequestContext = activateRequestContext();
    }

    @Override
    public McpRequestContext activateRequestContext() {
        boolean activate = !requestContext.isActive();
        McpRequestContext context = new McpRequestContext(requestContext,
                activate ? requestContext.activate() : null);
        try {
            if (activate && contextSupport() != null) {
                contextSupport().requestContextActivated();
            }
            if (securitySupport() != null && currentIdentityAssociation != null) {
                securitySupport().setCurrentIdentity(currentIdentityAssociation);
            }
            return context;
        } catch (RuntimeException e) {
            try {
                context.close();
            } catch (RuntimeException e2) {
                e.addSuppressed(e2);
            }
            throw e;
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
        McpRequestContext context = ownedRequestContext;
        ownedRequestContext = null;
        if (context != null) {
            context.close();
        }
    }

    @Override
    public McpProtocolVersion protocolVersion() {
        if (connection.initialRequest() != null) {
            return connection.initialRequest().protocolVersion();
        }
        return null;
    }

}
