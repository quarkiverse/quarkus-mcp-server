package org.acme;

import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.UserInfo;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/login")
@Authenticated
public class LoginEndpoint {

    @Inject
    Template accessToken;

    @Inject
    UserInfo userInfo;

    @Inject
    AccessTokenCredential accessTokenCredential;

    @GET
    @Produces("text/html")
    public TemplateInstance getAccessToken() {
        return accessToken.data("name", userInfo.getName()).data("access_token", accessTokenCredential.getToken());
    }
}
