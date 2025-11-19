package io.quarkiverse.mcp.server.http.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.deployment.ServerNameBuildItem;
import io.quarkiverse.mcp.server.http.runtime.HttpMcpServerRecorder;
import io.quarkiverse.mcp.server.http.runtime.McpServerEndpoints;
import io.quarkiverse.mcp.server.http.runtime.McpServerEndpoints.McpServerEndpoint;
import io.quarkiverse.mcp.server.http.runtime.McpServerEndpointsLogger;
import io.quarkiverse.mcp.server.http.runtime.SseMcpMessageHandler;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServerBuildTimeConfig;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServersBuildTimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;

public class HttpMcpServerProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-http");
    }

    @BuildStep
    void addBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(
                AdditionalBeanBuildItem.builder()
                        .setUnremovable()
                        .addBeanClasses(SseMcpMessageHandler.class, StreamableHttpMcpMessageHandler.class,
                                McpServerEndpointsLogger.class)
                        .build());
    }

    @BuildStep
    void serverNames(McpHttpServersBuildTimeConfig config, BuildProducer<ServerNameBuildItem> serverNames) {
        for (String serverName : config.servers().keySet()) {
            serverNames.produce(new ServerNameBuildItem(serverName));
        }
    }

    @Record(RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @BuildStep
    void registerEndpoints(McpServerEndpointsBuildItem mcpServerEndpoints,
            HttpMcpServerRecorder recorder,
            BodyHandlerBuildItem bodyHandler,
            BuildProducer<RouteBuildItem> routes) {
        for (McpServerEndpoint endpoint : mcpServerEndpoints.getEndpoints()) {
            // Streamable HTTP transport
            routes.produce(RouteBuildItem.newFrameworkRoute(endpoint.mcpPath)
                    .withRouteCustomizer(recorder.addBodyHandler(bodyHandler.getHandler()))
                    .withRequestHandler(recorder.createMcpEndpointHandler(endpoint.serverName))
                    .build());
            // SSE/HTTP transport
            routes.produce(RouteBuildItem.newFrameworkRoute(endpoint.ssePath)
                    .withRequestHandler(recorder.createSseEndpointHandler(endpoint.mcpPath, endpoint.serverName))
                    .build());
            routes.produce(RouteBuildItem.newFrameworkRoute(endpoint.mcpPath + "/" + "messages/:id")
                    .withRouteCustomizer(recorder.addBodyHandler(bodyHandler.getHandler()))
                    .withRequestHandler(recorder.createMessagesEndpointHandler(endpoint.serverName))
                    .build());
        }
    }

    @Record(STATIC_INIT)
    @BuildStep
    void registerMcpEndpointsBean(McpServerEndpointsBuildItem mcpServerEndpoints, HttpMcpServerRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(McpServerEndpoints.class)
                .scope(Singleton.class)
                .createWith(recorder.createMcpServerEndpoints(mcpServerEndpoints.getEndpoints()))
                .done());
    }

    @BuildStep
    McpServerEndpointsBuildItem collectMcpServerEndpoints(McpHttpServersBuildTimeConfig config,
            HttpRootPathBuildItem httpRootPath) {
        List<McpServerEndpoint> endpoints = new ArrayList<>();
        Set<String> rootPaths = new HashSet<>();
        for (Map.Entry<String, McpHttpServerBuildTimeConfig> e : config.servers().entrySet()) {
            String serverName = e.getKey();
            String rootPath = e.getValue().http().rootPath();
            if (!rootPaths.add(rootPath)) {
                throw new IllegalStateException("Multiple server configurations define the same root path: " + rootPath);
            }
            // Streamable HTTP transport, by default /mcp
            String mcpPath = httpRootPath.relativePath(rootPath);
            // SSE/HTTP transport, by default /mcp/sse
            String ssePath = mcpPath.endsWith("/") ? mcpPath + "sse" : mcpPath + "/sse";
            endpoints.add(new McpServerEndpoint(serverName, mcpPath, ssePath));
        }
        return new McpServerEndpointsBuildItem(endpoints);
    }

}
