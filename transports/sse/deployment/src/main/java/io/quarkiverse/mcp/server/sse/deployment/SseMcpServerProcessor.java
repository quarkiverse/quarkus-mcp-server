package io.quarkiverse.mcp.server.sse.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.mcp.server.deployment.ServerNameBuildItem;
import io.quarkiverse.mcp.server.sse.runtime.SseMcpMessageHandler;
import io.quarkiverse.mcp.server.sse.runtime.SseMcpServerRecorder;
import io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.sse.runtime.config.McpSseServerBuildTimeConfig;
import io.quarkiverse.mcp.server.sse.runtime.config.McpSseServersBuildTimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;

public class SseMcpServerProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-sse");
    }

    @BuildStep
    void addBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(
                AdditionalBeanBuildItem.builder().setUnremovable()
                        .addBeanClasses(SseMcpMessageHandler.class, StreamableHttpMcpMessageHandler.class).build());
    }

    @BuildStep
    void serverNames(McpSseServersBuildTimeConfig config, BuildProducer<ServerNameBuildItem> serverNames) {
        for (String serverName : config.servers().keySet()) {
            serverNames.produce(new ServerNameBuildItem(serverName));
        }
    }

    @Record(RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @BuildStep
    void registerEndpoints(McpSseServersBuildTimeConfig config, HttpRootPathBuildItem httpRootPath,
            SseMcpServerRecorder recorder,
            BodyHandlerBuildItem bodyHandler,
            BuildProducer<RouteBuildItem> routes) {

        Set<String> rootPaths = new HashSet<>();
        for (Map.Entry<String, McpSseServerBuildTimeConfig> e : config.servers().entrySet()) {
            String serverName = e.getKey();
            String rootPath = e.getValue().rootPath();
            if (!rootPaths.add(rootPath)) {
                throw new IllegalStateException("Multiple server configurations define the same root path: " + rootPath);
            }

            // By default /mcp
            String mcpPath = httpRootPath.relativePath(rootPath);

            // Streamable HTTP transport
            routes.produce(RouteBuildItem.newFrameworkRoute(mcpPath)
                    .withRouteCustomizer(recorder.addBodyHandler(bodyHandler.getHandler()))
                    .withRequestHandler(recorder.createMcpEndpointHandler(serverName))
                    .build());

            // SSE/HTTP transport
            routes.produce(RouteBuildItem.newFrameworkRoute(mcpPath.endsWith("/") ? mcpPath + "sse" : mcpPath + "/sse")
                    .withRequestHandler(recorder.createSseEndpointHandler(mcpPath, serverName))
                    .build());
            routes.produce(RouteBuildItem.newFrameworkRoute(mcpPath + "/" + "messages/:id")
                    .withRouteCustomizer(recorder.addBodyHandler(bodyHandler.getHandler()))
                    .withRequestHandler(recorder.createMessagesEndpointHandler(serverName))
                    .build());
        }

    }
}
