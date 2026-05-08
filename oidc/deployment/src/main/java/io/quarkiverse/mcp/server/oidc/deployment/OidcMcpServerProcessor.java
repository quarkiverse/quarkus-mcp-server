package io.quarkiverse.mcp.server.oidc.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServerBuildTimeConfig;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServersBuildTimeConfig;
import io.quarkiverse.mcp.server.oidc.runtime.OidcMcpServerRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;

public class OidcMcpServerProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-oidc");
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void registerInsufficientScopeHandler(
            McpHttpServersBuildTimeConfig config,
            HttpRootPathBuildItem httpRootPath,
            OidcMcpServerRecorder recorder,
            BuildProducer<RouteBuildItem> routes) {

        List<String> mcpPaths = new ArrayList<>();
        for (Map.Entry<String, McpHttpServerBuildTimeConfig> e : config.servers().entrySet()) {
            if (e.getValue().http().enabled()) {
                mcpPaths.add(httpRootPath.relativePath(e.getValue().http().rootPath()));
            }
        }

        if (!mcpPaths.isEmpty()) {
            routes.produce(RouteBuildItem.newAbsoluteRoute("/*")
                    .asFailureRoute()
                    .withRequestHandler(
                            recorder.createInsufficientScopeHandler(mcpPaths, httpRootPath.getRootPath()))
                    .build());
        }
    }
}
