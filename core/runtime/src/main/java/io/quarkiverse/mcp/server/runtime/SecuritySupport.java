package io.quarkiverse.mcp.server.runtime;

import io.quarkus.security.identity.CurrentIdentityAssociation;

/**
 *
 */
public interface SecuritySupport {

    void setCurrentIdentity(CurrentIdentityAssociation currentIdentityAssociation);
}
