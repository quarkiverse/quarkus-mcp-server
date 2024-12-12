package io.quarkiverse.mcp.server.deployment;

import static io.quarkiverse.mcp.server.deployment.FeatureMethodBuildItem.Feature.PROMPT;
import static io.quarkiverse.mcp.server.deployment.FeatureMethodBuildItem.Feature.TOOL;
import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

import jakarta.enterprise.invoke.Invoker;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;

import io.quarkiverse.mcp.server.runtime.ExecutionModel;
import io.quarkiverse.mcp.server.runtime.FeatureArgument;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata;
import io.quarkiverse.mcp.server.runtime.FeatureMethodInfo;
import io.quarkiverse.mcp.server.runtime.McpBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpServerRecorder;
import io.quarkiverse.mcp.server.runtime.PromptManager;
import io.quarkiverse.mcp.server.runtime.ResultMappers;
import io.quarkiverse.mcp.server.runtime.ToolManager;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.InvokerFactoryBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.InvokerBuilder;
import io.quarkus.arc.processor.KotlinUtils;
import io.quarkus.arc.processor.Types;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HandlerType;

class McpServerProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server");
    }

    @BuildStep
    void addBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf("io.quarkiverse.mcp.server.runtime.ConnectionManager"));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(PromptManager.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(ToolManager.class));
    }

    @BuildStep
    AutoAddScopeBuildItem autoAddScope() {
        return AutoAddScopeBuildItem.builder().containsAnnotations(DotNames.PROMPT, DotNames.TOOL)
                .defaultScope(BuiltinScope.SINGLETON)
                .build();
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void registerEndpoints(McpBuildTimeConfig config, HttpRootPathBuildItem rootPath, McpServerRecorder recorder,
            BodyHandlerBuildItem bodyHandler,
            BuildProducer<RouteBuildItem> routes) {
        String mcpPath = rootPath.relativePath(config.rootPath());

        routes.produce(RouteBuildItem.builder()
                .route(mcpPath + "/" + "sse")
                .handlerType(HandlerType.NORMAL)
                .handler(recorder.createSseEndpointHandler(mcpPath))
                .build());

        routes.produce(RouteBuildItem.builder()
                .routeFunction(mcpPath + "/" + "messages", recorder.addBodyHandler(bodyHandler.getHandler()))
                .handlerType(HandlerType.NORMAL)
                .handler(recorder.createMessagesEndpointHandler())
                .build());
    }

    @BuildStep
    void collectFeatureMethods(BeanDiscoveryFinishedBuildItem beanDiscovery, InvokerFactoryBuildItem invokerFactory,
            BuildProducer<FeatureMethodBuildItem> features) {
        for (BeanInfo bean : beanDiscovery.beanStream().classBeans().filter(this::hasFeatureMethod)) {
            ClassInfo beanClass = bean.getTarget().get().asClass();
            for (MethodInfo method : beanClass.methods()) {
                AnnotationInstance featureAnnotation = method.declaredAnnotation(DotNames.PROMPT);
                if (featureAnnotation == null) {
                    featureAnnotation = method.declaredAnnotation(DotNames.TOOL);
                }
                if (featureAnnotation != null) {
                    // TODO validate method
                    AnnotationValue nameValue = featureAnnotation.value("name");
                    String name = nameValue != null ? nameValue.asString() : method.name();
                    AnnotationValue descValue = featureAnnotation.value("description");
                    String description = descValue != null ? descValue.asString() : "";
                    InvokerBuilder invokerBuilder = invokerFactory.createInvoker(bean, method)
                            .withInstanceLookup();
                    features.produce(
                            new FeatureMethodBuildItem(bean, method, invokerBuilder.build(), name, description,
                                    featureAnnotation.name().equals(DotNames.PROMPT) ? PROMPT : TOOL));
                }
            }
        }
    }

    private boolean hasFeatureMethod(BeanInfo bean) {
        ClassInfo beanClass = bean.getTarget().get().asClass();
        return beanClass.hasAnnotation(DotNames.PROMPT) || beanClass.hasAnnotation(DotNames.TOOL);
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void generateMetadata(McpServerRecorder recorder, RecorderContext recorderContext,
            List<FeatureMethodBuildItem> featureMethods,
            TransformedAnnotationsBuildItem transformedAnnotations,
            BuildProducer<GeneratedClassBuildItem> generatedClasses, BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        // Note that the generated McpMetadata impl must be considered an application class
        // so that it can see the generated invokers
        ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClasses, true);

        String metadataClassName = "io.quarkiverse.mcp.server.runtime.McpMetadata_Impl";
        ClassCreator metadataCreator = ClassCreator.builder().classOutput(classOutput)
                .className(metadataClassName)
                .interfaces(McpMetadata.class)
                .build();

        // io.quarkiverse.mcp.server.runtime.McpMetadata.prompts()
        MethodCreator promptsMethod = metadataCreator.getMethodCreator("prompts", List.class);
        ResultHandle retPrompts = Gizmo.newArrayList(promptsMethod);
        for (FeatureMethodBuildItem prompt : featureMethods.stream().filter(FeatureMethodBuildItem::isPrompt).toList()) {
            processFeatureMethod(promptsMethod, prompt, retPrompts, transformedAnnotations);
        }
        promptsMethod.returnValue(retPrompts);

        // io.quarkiverse.mcp.server.runtime.McpMetadata.tools()
        MethodCreator toolsMethod = metadataCreator.getMethodCreator("tools", List.class);
        ResultHandle retTools = Gizmo.newArrayList(toolsMethod);
        for (FeatureMethodBuildItem tool : featureMethods.stream().filter(FeatureMethodBuildItem::isTool).toList()) {
            processFeatureMethod(toolsMethod, tool, retTools, transformedAnnotations);
        }
        toolsMethod.returnValue(retTools);

        metadataCreator.close();

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(McpMetadata.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .runtimeValue(recorderContext.newInstance(metadataClassName))
                .done());
    }

    private void processFeatureMethod(MethodCreator method, FeatureMethodBuildItem featureMethod, ResultHandle retList,
            TransformedAnnotationsBuildItem transformedAnnotations) {
        ResultHandle args = Gizmo.newArrayList(method);
        for (MethodParameterInfo pi : featureMethod.getMethod().parameters()) {
            String name = pi.name();
            String description = "";
            boolean required = true;
            AnnotationInstance promptArgAnnotation = pi.declaredAnnotation(DotNames.PROMPT_ARG);
            if (promptArgAnnotation != null) {
                AnnotationValue nameValue = promptArgAnnotation.value("name");
                if (nameValue != null) {
                    name = nameValue.asString();
                }
                AnnotationValue descriptionValue = promptArgAnnotation.value("name");
                if (descriptionValue != null) {
                    description = descriptionValue.asString();
                }
                AnnotationValue requiredValue = promptArgAnnotation.value("name");
                if (requiredValue != null) {
                    required = requiredValue.asBoolean();
                }
            }
            // TODO validate types
            ResultHandle type = Types.getTypeHandle(method, pi.type());
            ResultHandle provider;
            if (pi.type().name().equals(DotNames.MCP_CONNECTION)) {
                provider = method.load(FeatureArgument.Provider.MCP_CONNECTION);
            } else if (pi.type().name().equals(DotNames.REQUEST_ID)) {
                provider = method.load(FeatureArgument.Provider.REQUEST_ID);
            } else {
                provider = method.load(FeatureArgument.Provider.PARAMS);
            }
            ResultHandle arg = method.newInstance(
                    MethodDescriptor.ofConstructor(FeatureArgument.class, String.class, String.class, boolean.class,
                            Type.class, FeatureArgument.Provider.class),
                    method.load(name), method.load(description), method.load(required), type,
                    provider);
            Gizmo.listOperations(method).on(args).add(arg);
        }
        ResultHandle info = method.newInstance(
                MethodDescriptor.ofConstructor(FeatureMethodInfo.class, String.class, String.class, List.class),
                method.load(featureMethod.getName()), method.load(featureMethod.getDescription()), args);
        ResultHandle invoker = method
                .newInstance(MethodDescriptor.ofConstructor(featureMethod.getInvoker().getClassName()));
        ResultHandle executionModel = method.load(executionModel(featureMethod.getMethod(), transformedAnnotations));
        ResultHandle resultMapper = getMapper(method, featureMethod.getMethod().returnType(), featureMethod.getFeature());
        ResultHandle metadata = method.newInstance(
                MethodDescriptor.ofConstructor(FeatureMetadata.class, FeatureMethodInfo.class, Invoker.class,
                        ExecutionModel.class, Function.class),
                info, invoker, executionModel, resultMapper);
        Gizmo.listOperations(method).on(retList).add(metadata);
    }

    private ResultHandle getMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType,
            FeatureMethodBuildItem.Feature feature) {
        // At this point the method return type is already validated
        return switch (feature) {
            case PROMPT -> promptMapper(bytecode, returnType);
            case TOOL -> toolMapper(bytecode, returnType);
            default -> throw new IllegalArgumentException("Unsupported feature: " + feature);
        };
    }

    private ResultHandle promptMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType) {
        if (returnType.name().equals(DotNames.PROMPT_RESPONSE)) {
            return resultMapper(bytecode, "TO_UNI");
        } else if (returnType.name().equals(DotNames.LIST)) {
            return resultMapper(bytecode, "PROMPT_LIST_MESSAGE");
        } else if (returnType.name().equals(DotNames.UNI)) {
            org.jboss.jandex.Type typeArg = returnType.asParameterizedType().arguments().get(0);
            if (typeArg.name().equals(DotNames.PROMPT_RESPONSE)) {
                return resultMapper(bytecode, "IDENTITY");
            } else if (typeArg.name().equals(DotNames.LIST)) {
                return resultMapper(bytecode, "PROMPT_UNI_LIST_MESSAGE");
            } else {
                return resultMapper(bytecode, "PROMPT_UNI_SINGLE_MESSAGE");
            }
        } else {
            return resultMapper(bytecode, "PROMPT_SINGLE_MESSAGE");
        }
    }

    private ResultHandle toolMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType) {
        if (returnType.name().equals(DotNames.TOOL_RESPONSE)) {
            // ToolResponse
            return resultMapper(bytecode, "TO_UNI");
        } else if (returnType.name().equals(DotNames.UNI)) {
            // Uni<ToolResponse>
            return resultMapper(bytecode, "IDENTITY");
        } else {
            throw new IllegalArgumentException("Unsupported return type");
        }
    }

    private ResultHandle resultMapper(BytecodeCreator bytecode, String contantName) {
        return bytecode.readStaticField(FieldDescriptor.of(ResultMappers.class, contantName, Function.class));
    }

    private static ExecutionModel executionModel(MethodInfo method, TransformedAnnotationsBuildItem transformedAnnotations) {
        if (KotlinUtils.isKotlinSuspendMethod(method)
                && (transformedAnnotations.hasAnnotation(method, DotNames.RUN_ON_VIRTUAL_THREAD)
                        || transformedAnnotations.hasAnnotation(method.declaringClass(),
                                DotNames.RUN_ON_VIRTUAL_THREAD)
                        || transformedAnnotations.hasAnnotation(method, DotNames.BLOCKING)
                        || transformedAnnotations.hasAnnotation(method, DotNames.NON_BLOCKING))) {
            throw new IllegalStateException("Kotlin `suspend` functions in MCP components may not be "
                    + "annotated @Blocking, @NonBlocking or @RunOnVirtualThread: " + method);
        }
        if (transformedAnnotations.hasAnnotation(method, DotNames.RUN_ON_VIRTUAL_THREAD)
                || transformedAnnotations.hasAnnotation(method.declaringClass(), DotNames.RUN_ON_VIRTUAL_THREAD)) {
            return ExecutionModel.VIRTUAL_THREAD;
        } else if (transformedAnnotations.hasAnnotation(method, DotNames.BLOCKING)) {
            return ExecutionModel.WORKER_THREAD;
        } else if (transformedAnnotations.hasAnnotation(method, DotNames.NON_BLOCKING)) {
            return ExecutionModel.EVENT_LOOP;
        } else if (transformedAnnotations.hasAnnotation(method, DotNames.TRANSACTIONAL)
                || transformedAnnotations.hasAnnotation(method.declaringClass(), DotNames.TRANSACTIONAL)) {
            // Method annotated with @Transactional or declared on a class annotated @Transactional is also treated as a blocking method
            return ExecutionModel.WORKER_THREAD;
        } else {
            return hasBlockingSignature(method) ? ExecutionModel.WORKER_THREAD : ExecutionModel.EVENT_LOOP;
        }
    }

    static boolean hasBlockingSignature(MethodInfo method) {
        if (KotlinUtils.isKotlinSuspendMethod(method)) {
            return false;
        }
        switch (method.returnType().kind()) {
            case VOID:
            case CLASS:
            case ARRAY:
                return true;
            case PARAMETERIZED_TYPE:
                // Uni, Multi -> non-blocking
                DotName name = method.returnType().asParameterizedType().name();
                return !name.equals(DotNames.UNI) && !name.equals(DotNames.MULTI);
            default:
                throw new IllegalStateException(
                        "Unsupported return type:" + method);
        }
    }

}
