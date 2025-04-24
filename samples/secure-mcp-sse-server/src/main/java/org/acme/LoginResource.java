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
public class LoginResource {

    @Inject
    UserInfo userInfo;
    
    @Inject
    AccessTokenCredential accessToken;

    @Inject
    Template accessTokenPage;

    @GET
    @Produces("text/html")
    public TemplateInstance poem() {
        return accessTokenPage.data("name", userInfo.getName()).data("accessToken", accessToken.getToken());
    }
}
