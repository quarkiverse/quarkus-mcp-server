package io.quarkiverse.mcp.server.oidc.runtime;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.common.runtime.OidcCommonUtils;
import io.quarkus.oidc.common.runtime.OidcConstants;
import io.quarkus.oidc.runtime.OidcUtils;
import io.quarkus.security.ForbiddenException;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class InsufficientScopeFailureHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(InsufficientScopeFailureHandler.class);

    private final List<String> mcpPaths;
    private final String rootPath;

    public InsufficientScopeFailureHandler(List<String> mcpPaths, String rootPath) {
        this.mcpPaths = mcpPaths;
        this.rootPath = rootPath;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (!(ctx.failure() instanceof ForbiddenException) || !isMcpPath(ctx.normalizedPath())) {
            ctx.next();
            return;
        }

        OidcTenantConfig oidcConfig = ctx.get(OidcTenantConfig.class.getName());
        if (oidcConfig == null
                || !oidcConfig.resourceMetadata().enabled()
                || oidcConfig.resourceMetadata().scopes().isEmpty()) {
            ctx.next();
            return;
        }

        Set<String> scopes = oidcConfig.resourceMetadata().scopes().get();
        StringBuilder wwwAuth = new StringBuilder(oidcConfig.token().authorizationScheme());
        wwwAuth.append(" error=\"insufficient_scope\"");
        wwwAuth.append(", scope=\"").append(String.join(" ", scopes)).append("\"");

        String resourceMetadataUrl = buildResourceMetadataUrl(ctx, oidcConfig);
        wwwAuth.append(", resource_metadata=\"").append(resourceMetadataUrl).append("\"");

        LOG.debugf("Returning 403 with insufficient_scope challenge for tenant %s",
                oidcConfig.tenantId().orElse(OidcUtils.DEFAULT_TENANT_ID));

        ctx.response()
                .putHeader(HttpHeaderNames.WWW_AUTHENTICATE.toString(), wwwAuth.toString())
                .setStatusCode(403)
                .end();
    }

    private boolean isMcpPath(String path) {
        for (String mcpPath : mcpPaths) {
            if (path.equals(mcpPath) || path.startsWith(mcpPath + "/")) {
                return true;
            }
        }
        return false;
    }

    private String buildResourceMetadataUrl(RoutingContext ctx, OidcTenantConfig oidcConfig) {
        String metadataPath = getResourceMetadataPath(oidcConfig);
        String authority = URI.create(ctx.request().absoluteURI()).getAuthority();
        String scheme = oidcConfig.resourceMetadata().forceHttpsScheme() ? "https" : ctx.request().scheme();
        return scheme + "://" + authority + metadataPath;
    }

    private String getResourceMetadataPath(OidcTenantConfig oidcConfig) {
        String configuredResource = oidcConfig.resourceMetadata().resource().orElse("");
        String relativePath;

        if (configuredResource.startsWith("http")) {
            relativePath = URI.create(configuredResource).getRawPath();
        } else {
            relativePath = configuredResource;
        }

        String path = OidcUtils.getRootPath(rootPath) + OidcConstants.RESOURCE_METADATA_WELL_KNOWN_PATH;
        if (!relativePath.isEmpty()) {
            if (!"/".equals(relativePath)) {
                path += OidcCommonUtils.prependSlash(relativePath);
            }
        } else if (!OidcUtils.DEFAULT_TENANT_ID.equals(oidcConfig.tenantId().orElse(OidcUtils.DEFAULT_TENANT_ID))) {
            path += OidcCommonUtils.prependSlash(oidcConfig.tenantId().get().toLowerCase());
        }

        return path;
    }
}
