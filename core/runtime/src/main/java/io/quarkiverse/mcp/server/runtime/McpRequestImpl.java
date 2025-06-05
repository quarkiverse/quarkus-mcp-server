package io.quarkiverse.mcp.server.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.identity.CurrentIdentityAssociation;

public class McpRequestImpl implements McpRequest {

    private final String serverName;
    private final Object json;
    private final McpConnectionBase connection;
    private final Sender sender;
    private final SecuritySupport securitySupport;
    private final ContextSupport requestContextSupport;

    private final ManagedContext requestContext;
    private final CurrentIdentityAssociation currentIdentityAssociation;

    public McpRequestImpl(String serverName, Object json, McpConnectionBase connection, Sender sender,
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
    public McpConnectionBase connection() {
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
    public void contextEnd() {
        requestContext.terminate();
    }

}
