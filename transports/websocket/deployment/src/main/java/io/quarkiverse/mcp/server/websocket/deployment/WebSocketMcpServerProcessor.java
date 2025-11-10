package io.quarkiverse.mcp.server.websocket.deployment;

import java.util.List;
import java.util.Map.Entry;

import jakarta.enterprise.inject.Instance;

import org.jboss.jandex.AnnotationInstance;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
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
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.SignatureBuilder;
import io.quarkus.gizmo.Type;
import io.quarkus.runtime.util.HashUtil;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.websockets.next.InboundProcessingMode;
import io.quarkus.websockets.next.WebSocket;
import io.vertx.core.Vertx;

public class WebSocketMcpServerProcessor {

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
    void generateEndpoints(McpWebSocketServersBuildTimeConfig config, BuildProducer<GeneratedBeanBuildItem> generatedBeans) {
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeans, name -> false);

        // For each WebSocket path we generate a new class that extends WebSocketMcpMessageHandler
        for (Entry<String, McpWebSocketServerBuildTimeConfig> e : config.servers().entrySet()) {
            String endpointClassName = "io.quarkiverse.mcp.server.websocket.runtime.Endpoint" + HashUtil.sha1(e.getKey());
            ClassCreator endpointCreator = ClassCreator.builder().classOutput(classOutput)
                    .className(endpointClassName)
                    .superClass(WebSocketMcpMessageHandler.class)
                    .build();
            // @WebSocket(path = "/foo/bar")
            endpointCreator.addAnnotation(
                    AnnotationInstance.builder(WebSocket.class)
                            .add("path", e.getValue().websocket().endpointPath())
                            .add("inboundProcessingMode", InboundProcessingMode.CONCURRENT)
                            .build());

            Class<?>[] params = new Class<?>[] {
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
                    McpMetadata.class,
                    Vertx.class,
                    List.class,
                    Instance.class
            };
            MethodCreator constructor = endpointCreator.getConstructorCreator(params);
            constructor.setSignature(SignatureBuilder.forMethod()
                    .addParameterType(Type.classType(McpServersRuntimeConfig.class))
                    .addParameterType(Type.classType(ConnectionManager.class))
                    .addParameterType(Type.classType(PromptManagerImpl.class))
                    .addParameterType(Type.classType(ToolManagerImpl.class))
                    .addParameterType(Type.classType(ResourceManagerImpl.class))
                    .addParameterType(Type.classType(PromptCompletionManagerImpl.class))
                    .addParameterType(Type.classType(ResourceTemplateManagerImpl.class))
                    .addParameterType(Type.classType(ResourceTemplateCompletionManagerImpl.class))
                    .addParameterType(Type.classType(NotificationManagerImpl.class))
                    .addParameterType(Type.classType(ResponseHandlers.class))
                    .addParameterType(Type.classType(McpMetadata.class))
                    .addParameterType(Type.classType(Vertx.class))
                    // List<InitialCheck>
                    .addParameterType(Type.parameterizedType(Type.classType(List.class), Type.classType(InitialCheck.class)))
                    // Instance<CurrentIdentityAssociation>
                    .addParameterType(Type.parameterizedType(Type.classType(Instance.class),
                            Type.classType(CurrentIdentityAssociation.class)))
                    .build());
            // @All List<InitialCheck>
            constructor.getParameterAnnotations(12).addAnnotation(All.class);
            // super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
            // resourceTemplateManager, resourceTemplateCompleteManager, notificationManager, responseHandlers, metadata, vertx,
            // initialChecks);
            constructor.invokeSpecialMethod(MethodDescriptor.ofConstructor(WebSocketMcpMessageHandler.class, params),
                    constructor.getThis(),
                    constructor.getMethodParam(0),
                    constructor.getMethodParam(1),
                    constructor.getMethodParam(2),
                    constructor.getMethodParam(3),
                    constructor.getMethodParam(4),
                    constructor.getMethodParam(5),
                    constructor.getMethodParam(6),
                    constructor.getMethodParam(7),
                    constructor.getMethodParam(8),
                    constructor.getMethodParam(9),
                    constructor.getMethodParam(10),
                    constructor.getMethodParam(11),
                    constructor.getMethodParam(12),
                    constructor.getMethodParam(13));
            constructor.returnVoid();

            // WebSocketMcpMessageHandler.serverName()
            MethodCreator serverName = endpointCreator.getMethodCreator("serverName", String.class);
            serverName.returnValue(serverName.load(e.getKey()));

            endpointCreator.close();
        }
    }

}
