package io.quarkiverse.mcp.server.authorization.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/auth-server-url")
public class AuthServerUrlResource {

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getAuthServerUrl() {
        return authServerUrl;
    }
}
