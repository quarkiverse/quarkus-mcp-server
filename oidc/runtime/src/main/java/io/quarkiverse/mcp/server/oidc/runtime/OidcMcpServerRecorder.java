package io.quarkiverse.mcp.server.oidc.runtime;

import java.util.List;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class OidcMcpServerRecorder {

    public Handler<RoutingContext> createInsufficientScopeHandler(List<String> mcpPaths, String rootPath) {
        return new InsufficientScopeFailureHandler(mcpPaths, rootPath);
    }
}
