package io.quarkiverse.mcp.server.websocket.deployment;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialResponseInfo;
import io.quarkiverse.mcp.server.deployment.ServerNameBuildItem;
import io.quarkiverse.mcp.server.runtime.CancellationRequests;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpMetrics;
import io.quarkiverse.mcp.server.runtime.McpRequestValidator;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkiverse.mcp.server.websocket.runtime.WebSocketMcpMessageHandler;
import io.quarkiverse.mcp.server.websocket.runtime.config.McpWebSocketServerBuildTimeConfig;
import io.quarkiverse.mcp.server.websocket.runtime.config.McpWebSocketServersBuildTimeConfig;
import io.quarkus.arc.All;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmo2Adaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.GenericType;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.TypeArgument;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.util.HashUtil;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.websockets.next.InboundProcessingMode;
import io.quarkus.websockets.next.WebSocket;
import io.vertx.core.Vertx;

public class WebSocketMcpServerProcessor {

    private static final Logger LOG = Logger.getLogger(WebSocketMcpServerProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-websocket");
    }

    @BuildStep
    AdditionalIndexedClassesBuildItem dummyEndpoint() {
        // We need to register a dummy @WebSocket endpoint,
        // to workaround a websockets-next optimization where some internal beans
        // are not registered unless @WebSocket is used in the CombinedIndexBuildItem
        // The dummy endpoint is vetoed, i.e. ignored at runtime
        return new AdditionalIndexedClassesBuildItem("io.quarkiverse.mcp.server.websocket.runtime.DummyEndpoint");
    }

    @BuildStep
    void serverNames(McpWebSocketServersBuildTimeConfig config, BuildProducer<ServerNameBuildItem> serverNames) {
        for (Map.Entry<String, McpWebSocketServerBuildTimeConfig> e : config.servers().entrySet()) {
            if (e.getValue().websocket().enabled()) {
                serverNames.produce(new ServerNameBuildItem(e.getKey()));
            }
        }
    }

    @BuildStep
    void generateEndpoints(McpWebSocketServersBuildTimeConfig config,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans) {

        // The generated endpoints are not considered application classes
        ClassOutput classOutput = new GeneratedBeanGizmo2Adaptor(generatedBeans);
        Gizmo gizmo = Gizmo.create(classOutput)
                .withDebugInfo(false)
                .withParameters(false);

        // For each WebSocket path we generate a new class that extends WebSocketMcpMessageHandler
        Set<String> endpointPaths = new HashSet<>();
        for (Entry<String, McpWebSocketServerBuildTimeConfig> e : config.servers().entrySet()) {
            if (!e.getValue().websocket().enabled()) {
                LOG.debugf("WebSocket transport disabled for server [%s]", e.getKey());
                continue;
            }
            String endpointPath = e.getValue().websocket().endpointPath();
            if (!endpointPaths.add(endpointPath)) {
                throw new IllegalStateException(
                        "Multiple server configurations define the same endpoint path: " + endpointPath);
            }
            String endpointClassName = "io.quarkiverse.mcp.server.websocket.runtime.Endpoint" + HashUtil.sha1(e.getKey());

            gizmo.class_(endpointClassName, cc -> {
                cc.extends_(WebSocketMcpMessageHandler.class);

                // @WebSocket(path = "/foo/bar", inboundProcessingMode = InboundProcessingMode.CONCURRENT)
                cc.addAnnotation(WebSocket.class, ac -> {
                    ac.add("path", e.getValue().websocket().endpointPath());
                    ac.add("inboundProcessingMode", InboundProcessingMode.CONCURRENT);
                });
                // @Startup - force eager initialization to trigger server name validation
                cc.addAnnotation(Startup.class);
                cc.addAnnotation(Singleton.class);

                // Constructor
                cc.constructor(conc -> {
                    ParamVar p0 = conc.parameter("config", McpServersRuntimeConfig.class);
                    ParamVar p1 = conc.parameter("connectionManager", ConnectionManager.class);
                    ParamVar p2 = conc.parameter("promptManager", PromptManagerImpl.class);
                    ParamVar p3 = conc.parameter("toolManager", ToolManagerImpl.class);
                    ParamVar p4 = conc.parameter("resourceManager", ResourceManagerImpl.class);
                    ParamVar p5 = conc.parameter("promptCompletionManager", PromptCompletionManagerImpl.class);
                    ParamVar p6 = conc.parameter("resourceTemplateManager", ResourceTemplateManagerImpl.class);
                    ParamVar p7 = conc.parameter("resourceTemplateCompletionManager",
                            ResourceTemplateCompletionManagerImpl.class);
                    ParamVar p8 = conc.parameter("notificationManager", NotificationManagerImpl.class);
                    ParamVar p9 = conc.parameter("responseHandlers", ResponseHandlers.class);
                    ParamVar p10 = conc.parameter("cancellationRequests", CancellationRequests.class);
                    ParamVar p11 = conc.parameter("mcpMetadata", McpMetadata.class);
                    ParamVar p12 = conc.parameter("vertx", Vertx.class);
                    // @All List<InitialCheck>
                    ParamVar p13 = conc.parameter("initialChecks", pp -> {
                        pp.setType(GenericType.of(List.class, List.of(TypeArgument.of(InitialCheck.class))));
                        pp.addAnnotation(All.class);
                    });
                    // @All List<InitialResponseInfo>
                    ParamVar p14 = conc.parameter("initialResponseInfos", pp -> {
                        pp.setType(GenericType.of(List.class, List.of(TypeArgument.of(InitialResponseInfo.class))));
                        pp.addAnnotation(All.class);
                    });
                    // Instance<CurrentIdentityAssociation>
                    ParamVar p15 = conc.parameter("currentIdentityAssociation",
                            GenericType.of(Instance.class, List.of(TypeArgument.of(CurrentIdentityAssociation.class))));
                    // Instance<McpMetrics>
                    ParamVar p16 = conc.parameter("metrics",
                            GenericType.of(Instance.class, List.of(TypeArgument.of(McpMetrics.class))));
                    // Instance<McpRequestValidator>
                    ParamVar p17 = conc.parameter("mcpRequestValidator",
                            GenericType.of(Instance.class, List.of(TypeArgument.of(McpRequestValidator.class))));

                    ConstructorDesc superConstructor = ConstructorDesc.of(WebSocketMcpMessageHandler.class,
                            McpServersRuntimeConfig.class,
                            ConnectionManager.class,
                            PromptManagerImpl.class,
                            ToolManagerImpl.class,
                            ResourceManagerImpl.class,
                            PromptCompletionManagerImpl.class,
                            ResourceTemplateManagerImpl.class,
                            ResourceTemplateCompletionManagerImpl.class,
                            NotificationManagerImpl.class,
                            ResponseHandlers.class,
                            CancellationRequests.class,
                            McpMetadata.class,
                            Vertx.class,
                            List.class,
                            List.class,
                            Instance.class,
                            Instance.class,
                            Instance.class);
                    conc.body(bc -> {
                        bc.invokeSpecial(superConstructor, cc.this_(), p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12,
                                p13, p14, p15, p16, p17);
                        bc.return_();
                    });
                });

                cc.method("serverName", mc -> {
                    mc.returning(String.class);
                    mc.body(bc -> {
                        bc.return_(e.getKey());
                    });
                });

            });
        }
    }

}
