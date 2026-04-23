package io.quarkiverse.mcp.server.authorization.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.Authenticated;

@Path("/")
@Authenticated
public class AccessTokenResource {

    @Inject
    AccessTokenCredential accessToken;

    @GET
    @Path("/access-token")
    @Produces(MediaType.TEXT_PLAIN)
    public String getAccessToken() {
        return accessToken.getToken();
    }

    @GET
    @Path("/access-token-no-audience")
    @Produces(MediaType.TEXT_PLAIN)
    public String getAccessTokenNoAudience() {
        return accessToken.getToken();
    }
}
