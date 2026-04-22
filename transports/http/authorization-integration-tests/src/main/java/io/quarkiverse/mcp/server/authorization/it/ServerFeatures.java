package io.quarkiverse.mcp.server.authorization.it;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.identity.SecurityIdentity;

public class ServerFeatures {

    @Inject
    SecurityIdentity identity;

    @Tool(name = "alpha-user-name-provider", description = "Provides a name of the current user in the Alpha realm")
    TextContent provideUserName() {
        return new TextContent(identity.getPrincipal().getName());
    }
}
