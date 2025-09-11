package org.acme;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import io.quarkiverse.mcp.server.McpServer;

public class ServerFeatures {

    @Inject
    SecurityIdentity identity;
    
    @Tool(name = "alpha-user-name-provider", description = "Provides a name of the current user in the Alpha realm")
    TextContent provideUserName() {
        return new TextContent(identity.getPrincipal().getName());
    }
    
    @Tool(name = "bravo-user-name-provider", description = "Provides a name of the current user in the Bravo realm")
    @McpServer("bravo")
    TextContent provideUserName2() {
        return new TextContent(identity.getPrincipal().getName());
    }
}
