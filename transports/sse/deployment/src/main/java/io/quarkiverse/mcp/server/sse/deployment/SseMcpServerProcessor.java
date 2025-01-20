package io.quarkiverse.mcp.server.sse.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import io.quarkiverse.mcp.server.sse.runtime.SseMcpMessageHandler;
import io.quarkiverse.mcp.server.sse.runtime.SseMcpServerRecorder;
import io.quarkiverse.mcp.server.sse.runtime.config.McpSseBuildTimeConfig;
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
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(SseMcpMessageHandler.class));
    }

    @Record(RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @BuildStep
    void registerEndpoints(McpSseBuildTimeConfig config, HttpRootPathBuildItem rootPath, SseMcpServerRecorder recorder,
            BodyHandlerBuildItem bodyHandler,
            BuildProducer<RouteBuildItem> routes) {
        String mcpPath = rootPath.relativePath(config.rootPath());

        routes.produce(RouteBuildItem.newFrameworkRoute(mcpPath + "/" + "sse")
                .withRequestHandler(recorder.createSseEndpointHandler(mcpPath))
                .build());

        routes.produce(RouteBuildItem.newFrameworkRoute(mcpPath + "/" + "messages/:id")
                .withRouteCustomizer(recorder.addBodyHandler(bodyHandler.getHandler()))
                .withRequestHandler(recorder.createMessagesEndpointHandler())
                .build());
    }
}
