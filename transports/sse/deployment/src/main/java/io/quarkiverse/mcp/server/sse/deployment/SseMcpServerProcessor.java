package io.quarkiverse.mcp.server.sse.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.inject.spi.EventContext;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.deployment.ServerNameBuildItem;
import io.quarkiverse.mcp.server.sse.runtime.SseMcpMessageHandler;
import io.quarkiverse.mcp.server.sse.runtime.SseMcpServerRecorder;
import io.quarkiverse.mcp.server.sse.runtime.SseMcpServerRecorder.McpServerEndpoints;
import io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.sse.runtime.config.McpSseServerBuildTimeConfig;
import io.quarkiverse.mcp.server.sse.runtime.config.McpSseServersBuildTimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem.ObserverConfiguratorBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.processor.ObserverConfigurator;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.vertx.http.HttpServerStart;
import io.quarkus.vertx.http.HttpsServerStart;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;
import io.vertx.core.http.HttpServerOptions;

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
            BuildProducer<RouteBuildItem> routes,
            ObserverRegistrationPhaseBuildItem observerRegistrationPhase,
            BuildProducer<ObserverConfiguratorBuildItem> observers) {

        Set<String> rootPaths = new HashSet<>();
        List<McpServerEndpoints> endpoints = new ArrayList<>();
        for (Map.Entry<String, McpSseServerBuildTimeConfig> e : config.servers().entrySet()) {
            String serverName = e.getKey();
            String rootPath = e.getValue().sse().rootPath();
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
            String ssePath = mcpPath.endsWith("/") ? mcpPath + "sse" : mcpPath + "/sse";
            routes.produce(RouteBuildItem.newFrameworkRoute(ssePath)
                    .withRequestHandler(recorder.createSseEndpointHandler(mcpPath, serverName))
                    .build());
            routes.produce(RouteBuildItem.newFrameworkRoute(mcpPath + "/" + "messages/:id")
                    .withRouteCustomizer(recorder.addBodyHandler(bodyHandler.getHandler()))
                    .withRequestHandler(recorder.createMessagesEndpointHandler(serverName))
                    .build());

            endpoints.add(new McpServerEndpoints(serverName, mcpPath, ssePath));
        }

        // Create synthetic observers for HttpServerStart and HttpsServerStart
        // so that the info is logged after the server is started
        ObserverConfigurator httpStartConfigurator = observerRegistrationPhase.getContext()
                .configure()
                .async(true)
                .beanClass(DotName.createSimple(SseMcpServerRecorder.class))
                .observedType(HttpServerStart.class)
                .notify(mc -> logMcpServerEndpoints(HttpServerStart.class, mc, endpoints));
        ObserverConfigurator httpsStartConfigurator = observerRegistrationPhase.getContext()
                .configure()
                .async(true)
                .beanClass(DotName.createSimple(SseMcpServerRecorder.class))
                .observedType(HttpsServerStart.class)
                .notify(mc -> logMcpServerEndpoints(HttpsServerStart.class, mc, endpoints));
        observers.produce(new ObserverConfiguratorBuildItem(httpStartConfigurator, httpsStartConfigurator));
    }

    private void logMcpServerEndpoints(Class<?> eventType, MethodCreator mc, List<McpServerEndpoints> endpoints) {
        ResultHandle event = mc.invokeInterfaceMethod(MethodDescriptor.ofMethod(EventContext.class, "getEvent", Object.class),
                mc.getMethodParam(0));
        ResultHandle httpServerOptions = mc.invokeVirtualMethod(
                MethodDescriptor.ofMethod(eventType, "options", HttpServerOptions.class),
                event);
        ResultHandle list = Gizmo.newArrayList(mc);
        for (McpServerEndpoints e : endpoints) {
            ResultHandle mcpe = mc.newInstance(MethodDescriptor.ofConstructor(McpServerEndpoints.class,
                    String.class, String.class, String.class), mc.load(e.serverName), mc.load(e.mcpPath),
                    mc.load(e.ssePath));
            Gizmo.listOperations(mc).on(list).add(mcpe);
        }
        mc.invokeStaticMethod(
                MethodDescriptor.ofMethod(SseMcpServerRecorder.class, "logEndpoints", void.class, List.class,
                        HttpServerOptions.class),
                list, httpServerOptions);
        mc.returnVoid();
    }

}
