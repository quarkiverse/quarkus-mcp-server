package io.quarkiverse.mcp.server.deployment;

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
import io.quarkiverse.mcp.server.runtime.Mappers;
import io.quarkiverse.mcp.server.runtime.McpBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpServerRecorder;
import io.quarkiverse.mcp.server.runtime.PromptArgument;
import io.quarkiverse.mcp.server.runtime.PromptManager;
import io.quarkiverse.mcp.server.runtime.PromptMetadata;
import io.quarkiverse.mcp.server.runtime.PromptMethodInfo;
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
    }

    @BuildStep
    AutoAddScopeBuildItem autoAddScope() {
        return AutoAddScopeBuildItem.builder().containsAnnotations(DotNames.PROMPT)
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
    void collectServerComponents(BeanDiscoveryFinishedBuildItem beanDiscovery, InvokerFactoryBuildItem invokerFactory,
            BuildProducer<PromptBuildItem> prompts) {
        for (BeanInfo bean : beanDiscovery.beanStream().classBeans()
                .filter(b -> b.getTarget().get().asClass().hasAnnotation(DotNames.PROMPT))) {
            ClassInfo beanClass = bean.getTarget().get().asClass();
            for (MethodInfo method : beanClass.methods()) {
                if (method.hasDeclaredAnnotation(DotNames.PROMPT)) {
                    AnnotationInstance promptAnnotation = method.declaredAnnotation(DotNames.PROMPT);
                    AnnotationValue nameValue = promptAnnotation.value("name");
                    String name = nameValue != null ? nameValue.asString() : method.name();
                    AnnotationValue descValue = promptAnnotation.value("description");
                    String description = descValue != null ? descValue.asString() : "";
                    InvokerBuilder invokerBuilder = invokerFactory.createInvoker(bean, method)
                            .withInstanceLookup();
                    prompts.produce(new PromptBuildItem(bean, method, invokerBuilder.build(), name, description));
                }
            }
        }
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void generateMetadata(McpServerRecorder recorder, RecorderContext recorderContext, List<PromptBuildItem> prompts,
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

        MethodCreator promptsMethod = metadataCreator.getMethodCreator("prompts", List.class);
        ResultHandle ret = Gizmo.newArrayList(promptsMethod);

        for (PromptBuildItem prompt : prompts) {
            ResultHandle args = Gizmo.newArrayList(promptsMethod);
            for (MethodParameterInfo pi : prompt.getMethod().parameters()) {
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
                ResultHandle type = Types.getTypeHandle(promptsMethod, pi.type());
                ResultHandle arg = promptsMethod.newInstance(
                        MethodDescriptor.ofConstructor(PromptArgument.class, String.class, String.class, boolean.class,
                                Type.class),
                        promptsMethod.load(name), promptsMethod.load(description), promptsMethod.load(required), type);
                Gizmo.listOperations(promptsMethod).on(args).add(arg);
            }
            ResultHandle info = promptsMethod.newInstance(
                    MethodDescriptor.ofConstructor(PromptMethodInfo.class, String.class, String.class, List.class),
                    promptsMethod.load(prompt.getName()), promptsMethod.load(prompt.getDescription()), args);
            ResultHandle invoker = promptsMethod
                    .newInstance(MethodDescriptor.ofConstructor(prompt.getInvoker().getClassName()));
            ResultHandle executionModel = promptsMethod.load(executionModel(prompt.getMethod(), transformedAnnotations));
            ResultHandle resultMapper = getMapper(promptsMethod, prompt.getMethod().returnType());
            ResultHandle metadata = promptsMethod.newInstance(
                    MethodDescriptor.ofConstructor(PromptMetadata.class, PromptMethodInfo.class, Invoker.class,
                            ExecutionModel.class, Function.class),
                    info, invoker, executionModel, resultMapper);
            Gizmo.listOperations(promptsMethod).on(ret).add(metadata);
        }
        promptsMethod.returnValue(ret);
        metadataCreator.close();

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(McpMetadata.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .runtimeValue(recorderContext.newInstance(metadataClassName))
                .done());
    }

    private ResultHandle getMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType) {
        if (returnType.name().equals(DotNames.LIST)) {
            return bytecode.readStaticField(FieldDescriptor.of(Mappers.class, "LIST_MESSAGE", Function.class));
        } else if (returnType.name().equals(DotNames.UNI)) {
            org.jboss.jandex.Type typeArg = returnType.asParameterizedType().arguments().get(0);
            if (typeArg.name().equals(DotNames.LIST)) {
                return bytecode.readStaticField(FieldDescriptor.of(Mappers.class, "IDENTITY", Function.class));
            } else {
                return bytecode.readStaticField(FieldDescriptor.of(Mappers.class, "UNI_SINGLE_MESSAGE", Function.class));
            }
        }
        return bytecode.readStaticField(FieldDescriptor.of(Mappers.class, "SINGLE_MESSAGE", Function.class));
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
