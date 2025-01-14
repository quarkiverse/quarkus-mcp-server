package io.quarkiverse.mcp.server.deployment;

import static io.quarkiverse.mcp.server.runtime.FeatureMetadata.Feature.PROMPT;
import static io.quarkiverse.mcp.server.runtime.FeatureMetadata.Feature.RESOURCE;
import static io.quarkiverse.mcp.server.runtime.FeatureMetadata.Feature.TOOL;
import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.invoke.Invoker;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type.Kind;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceContent;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.ExecutionModel;
import io.quarkiverse.mcp.server.runtime.FeatureArgument;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata.Feature;
import io.quarkiverse.mcp.server.runtime.FeatureMethodInfo;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpServerRecorder;
import io.quarkiverse.mcp.server.runtime.PromptManager;
import io.quarkiverse.mcp.server.runtime.ResourceManager;
import io.quarkiverse.mcp.server.runtime.ResultMappers;
import io.quarkiverse.mcp.server.runtime.ToolManager;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.InvokerFactoryBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.InvokerBuilder;
import io.quarkus.arc.processor.KotlinUtils;
import io.quarkus.arc.processor.Types;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

class McpServerProcessor {

    @BuildStep
    SelectedTransportBuildItem selectTransport(McpBuildTimeConfig buildTimeConfig,
            List<TransportCandidateBuildItem> transportCandidates) {
        if (transportCandidates.isEmpty()) {
            throw new IllegalStateException(
                    "At least one MCP transport must be used. Consider adding the one of the 'quarkus-mcp-server-stdio', 'quarkus-mcp-server-sse' extensions");
        }
        if (transportCandidates.size() == 1) {
            return new SelectedTransportBuildItem(transportCandidates.get(0).getName());
        }
        if (buildTimeConfig.transport().isPresent()) {
            String selectedTransportName = buildTimeConfig.transport().get();
            for (TransportCandidateBuildItem transportCandidate : transportCandidates) {
                if (transportCandidate.getName().equals(selectedTransportName)) {
                    return new SelectedTransportBuildItem(transportCandidate.getName());
                }
            }
            throw new IllegalStateException(
                    "Selected transport '" + selectedTransportName + "' does not correspond to any of the added extensions");
        } else {
            throw new IllegalStateException(
                    "Multiple transport candidates were found, please select one by setting 'quarkus.mcp.server.transport' to one of the following values: "
                            + transportCandidates.stream().map(TransportCandidateBuildItem::getName)
                                    .collect(Collectors.joining(", ")));
        }
    }

    @BuildStep
    void addBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf("io.quarkiverse.mcp.server.runtime.ConnectionManager"));
        additionalBeans.produce(AdditionalBeanBuildItem.builder().setUnremovable()
                .addBeanClasses(PromptManager.class, ToolManager.class, ResourceManager.class).build());
    }

    @BuildStep
    AutoAddScopeBuildItem autoAddScope() {
        return AutoAddScopeBuildItem.builder().containsAnnotations(DotNames.PROMPT, DotNames.TOOL, DotNames.RESOURCE)
                .defaultScope(BuiltinScope.SINGLETON)
                .build();
    }

    @BuildStep
    void collectFeatureMethods(BeanDiscoveryFinishedBuildItem beanDiscovery, InvokerFactoryBuildItem invokerFactory,
            BuildProducer<FeatureMethodBuildItem> features, BuildProducer<ValidationErrorBuildItem> errors) {
        Map<Feature, List<FeatureMethodBuildItem>> found = new HashMap<>();

        for (BeanInfo bean : beanDiscovery.beanStream().classBeans().filter(this::hasFeatureMethod)) {
            ClassInfo beanClass = bean.getTarget().get().asClass();
            for (MethodInfo method : beanClass.methods()) {
                Feature feature = null;
                AnnotationInstance featureAnnotation = method.declaredAnnotation(DotNames.PROMPT);
                if (featureAnnotation != null) {
                    feature = PROMPT;
                } else {
                    featureAnnotation = method.declaredAnnotation(DotNames.TOOL);
                    if (featureAnnotation != null) {
                        feature = TOOL;
                    } else {
                        featureAnnotation = method.declaredAnnotation(DotNames.RESOURCE);
                        if (featureAnnotation != null) {
                            feature = RESOURCE;
                        }
                    }
                }
                if (featureAnnotation != null) {
                    validateFeatureMethod(method, feature);
                    AnnotationValue nameValue = featureAnnotation.value("name");
                    String name = nameValue != null ? nameValue.asString() : method.name();
                    AnnotationValue descValue = featureAnnotation.value("description");
                    String description = descValue != null ? descValue.asString() : "";
                    InvokerBuilder invokerBuilder = invokerFactory.createInvoker(bean, method)
                            .withInstanceLookup();
                    AnnotationValue uriValue = featureAnnotation.value("uri");
                    String uri = uriValue != null ? uriValue.asString() : null;
                    AnnotationValue mimeTypeValue = featureAnnotation.value("mimeType");
                    String mimeType = mimeTypeValue != null ? mimeTypeValue.asString() : null;
                    FeatureMethodBuildItem fm = new FeatureMethodBuildItem(bean, method, invokerBuilder.build(), name,
                            description, uri, mimeType, feature);
                    features.produce(fm);
                    found.compute(feature, (f, list) -> {
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        list.add(fm);
                        return list;
                    });
                }
            }
        }

        // Check duplicate names
        for (List<FeatureMethodBuildItem> featureMethods : found.values()) {
            Map<String, List<FeatureMethodBuildItem>> byName = featureMethods.stream()
                    .collect(Collectors.toMap(FeatureMethodBuildItem::getName, List::of, (v1, v2) -> {
                        List<FeatureMethodBuildItem> list = new ArrayList<>();
                        list.addAll(v1);
                        list.addAll(v2);
                        return list;
                    }));
            for (List<FeatureMethodBuildItem> list : byName.values()) {
                if (list.size() > 1) {
                    String message = "Duplicate feature name found:\n\t%s"
                            .formatted(list.stream().map(Object::toString).collect(Collectors.joining("\n\t")));
                    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(message)));
                }
            }
        }

        // Check duplicate uris for resources
        List<FeatureMethodBuildItem> resources = found.get(RESOURCE);
        if (resources != null) {
            Map<String, List<FeatureMethodBuildItem>> byUri = resources.stream()
                    .collect(Collectors.toMap(FeatureMethodBuildItem::getUri, List::of, (v1, v2) -> {
                        List<FeatureMethodBuildItem> list = new ArrayList<>();
                        list.addAll(v1);
                        list.addAll(v2);
                        return list;
                    }));
            for (List<FeatureMethodBuildItem> list : byUri.values()) {
                if (list.size() > 1) {
                    String message = "Duplicate resource uri found:\n\t%s"
                            .formatted(list.stream().map(Object::toString).collect(Collectors.joining("\n\t")));
                    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(message)));
                }
            }
        }
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void generateMetadata(McpServerRecorder recorder, RecorderContext recorderContext,
            List<FeatureMethodBuildItem> featureMethods, TransformedAnnotationsBuildItem transformedAnnotations,
            BuildProducer<GeneratedClassBuildItem> generatedClasses, BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        // Note that the generated McpMetadata impl must be considered an application class
        // so that it can see the generated invokers
        ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClasses, true);

        String metadataClassName = "io.quarkiverse.mcp.server.runtime.McpMetadata_Impl";
        ClassCreator metadataCreator = ClassCreator.builder().classOutput(classOutput)
                .className(metadataClassName)
                .interfaces(McpMetadata.class)
                .build();

        AtomicInteger counter = new AtomicInteger();

        // io.quarkiverse.mcp.server.runtime.McpMetadata.prompts()
        MethodCreator promptsMethod = metadataCreator.getMethodCreator("prompts", List.class);
        ResultHandle retPrompts = Gizmo.newArrayList(promptsMethod);
        for (FeatureMethodBuildItem prompt : featureMethods.stream().filter(FeatureMethodBuildItem::isPrompt).toList()) {
            processFeatureMethod(counter, metadataCreator, promptsMethod, prompt, retPrompts, transformedAnnotations,
                    DotNames.PROMPT_ARG);
        }
        promptsMethod.returnValue(retPrompts);

        // io.quarkiverse.mcp.server.runtime.McpMetadata.tools()
        MethodCreator toolsMethod = metadataCreator.getMethodCreator("tools", List.class);
        ResultHandle retTools = Gizmo.newArrayList(toolsMethod);
        for (FeatureMethodBuildItem tool : featureMethods.stream().filter(FeatureMethodBuildItem::isTool).toList()) {
            processFeatureMethod(counter, metadataCreator, toolsMethod, tool, retTools, transformedAnnotations,
                    DotNames.TOOL_ARG);
        }
        toolsMethod.returnValue(retTools);

        // io.quarkiverse.mcp.server.runtime.McpMetadata.resources()
        MethodCreator resourcesMethod = metadataCreator.getMethodCreator("resources", List.class);
        ResultHandle retResources = Gizmo.newArrayList(resourcesMethod);
        for (FeatureMethodBuildItem resource : featureMethods.stream().filter(FeatureMethodBuildItem::isResource).toList()) {
            processFeatureMethod(counter, metadataCreator, resourcesMethod, resource, retResources, transformedAnnotations,
                    null);
        }
        resourcesMethod.returnValue(retResources);

        metadataCreator.close();

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(McpMetadata.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .runtimeValue(recorderContext.newInstance(metadataClassName))
                .done());
    }

    @BuildStep
    void registerForReflection(List<FeatureMethodBuildItem> featureMethods,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchies) {
        // FIXME this is not ideal, JsonObject.encode() may use Jackson under the hood which requires reflection
        for (FeatureMethodBuildItem m : featureMethods) {
            for (org.jboss.jandex.Type paramType : m.getMethod().parameterTypes()) {
                if (paramType.kind() == Kind.PRIMITIVE
                        || paramType.name().equals(DotNames.MCP_CONNECTION)
                        || paramType.name().equals(DotNames.REQUEST_ID)) {
                    continue;
                }
                reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(paramType).build());
            }
        }
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(Content.class, TextContent.class, ImageContent.class,
                ResourceContent.class, PromptResponse.class, PromptMessage.class, ToolResponse.class, FeatureMethodInfo.class,
                FeatureArgument.class, ResourceResponse.class, ResourceContents.class, TextResourceContents.class,
                BlobResourceContents.class).methods().build());
        reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(List.class).build());
        reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(Map.class).build());
    }

    private void validateFeatureMethod(MethodInfo method, Feature feature) {
        if (Modifier.isStatic(method.flags())) {
            throw new IllegalStateException("MCP feature method must not be static: " + method);
        }
        if (Modifier.isPrivate(method.flags())) {
            throw new IllegalStateException("MCP feature method must not be private: " + method);
        }
        switch (feature) {
            case PROMPT -> validatePromptMethod(method);
            case TOOL -> validateToolMethod(method);
            case RESOURCE -> validateResourceMethod(method);
            default -> throw new IllegalArgumentException("Unsupported feature: " + feature);
        }
    }

    private static final Set<org.jboss.jandex.Type> PROMPT_TYPES = Set.of(ClassType.create(DotNames.PROMPT_RESPONSE),
            ClassType.create(DotNames.PROMPT_MESSAGE));

    private void validatePromptMethod(MethodInfo method) {
        org.jboss.jandex.Type type = method.returnType();
        if (DotNames.UNI.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (DotNames.LIST.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (!PROMPT_TYPES.contains(type)) {
            throw new IllegalStateException("Unsupported prompt method return type: " + method);
        }
    }

    private static final Set<org.jboss.jandex.Type> TOOL_TYPES = Set.of(ClassType.create(DotNames.TOOL_RESPONSE),
            ClassType.create(DotNames.CONTENT), ClassType.create(DotNames.TEXT_CONTENT),
            ClassType.create(DotNames.IMAGE_CONTENT), ClassType.create(DotNames.RESOURCE_CONTENT),
            ClassType.create(DotNames.STRING));

    private void validateToolMethod(MethodInfo method) {
        org.jboss.jandex.Type type = method.returnType();
        if (DotNames.UNI.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (DotNames.LIST.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (!TOOL_TYPES.contains(type)) {
            throw new IllegalStateException("Unsupported Tool method return type: " + method);
        }
    }

    private static final Set<org.jboss.jandex.Type> RESOURCE_TYPES = Set.of(ClassType.create(DotNames.RESOURCE_RESPONSE),
            ClassType.create(DotNames.RESOURCE_CONTENS), ClassType.create(DotNames.TEXT_RESOURCE_CONTENS),
            ClassType.create(DotNames.BLOB_RESOURCE_CONTENS));

    private void validateResourceMethod(MethodInfo method) {
        org.jboss.jandex.Type type = method.returnType();
        if (DotNames.UNI.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (DotNames.LIST.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (!RESOURCE_TYPES.contains(type)) {
            throw new IllegalStateException("Unsupported Resource method return type: " + method);
        }
        if (method.parametersCount() > 1
                || (method.parametersCount() == 1
                        && !method.parameterName(0).equals("uri")
                        && !method.parameterType(0).name().equals(DotNames.STRING))) {
            throw new IllegalStateException(
                    "Resource method may accept zero paramateres or a single parameter of name 'uri' and type String: "
                            + method);
        }
    }

    private boolean hasFeatureMethod(BeanInfo bean) {
        ClassInfo beanClass = bean.getTarget().get().asClass();
        return beanClass.hasAnnotation(DotNames.PROMPT)
                || beanClass.hasAnnotation(DotNames.TOOL)
                || beanClass.hasAnnotation(DotNames.RESOURCE);
    }

    private void processFeatureMethod(AtomicInteger counter, ClassCreator clazz, MethodCreator method,
            FeatureMethodBuildItem featureMethod, ResultHandle retList,
            TransformedAnnotationsBuildItem transformedAnnotations, DotName argAnnotationName) {
        String methodName = "meta$" + counter.incrementAndGet();
        MethodCreator metaMethod = clazz.getMethodCreator(methodName, FeatureMetadata.class);

        ResultHandle args = Gizmo.newArrayList(metaMethod);
        for (MethodParameterInfo pi : featureMethod.getMethod().parameters()) {
            String name = pi.name();
            String description = "";
            boolean required = true;
            if (argAnnotationName != null) {
                AnnotationInstance argAnnotation = pi.declaredAnnotation(argAnnotationName);
                if (argAnnotation != null) {
                    AnnotationValue nameValue = argAnnotation.value("name");
                    if (nameValue != null) {
                        name = nameValue.asString();
                    }
                    AnnotationValue descriptionValue = argAnnotation.value("description");
                    if (descriptionValue != null) {
                        description = descriptionValue.asString();
                    }
                    AnnotationValue requiredValue = argAnnotation.value("required");
                    if (requiredValue != null) {
                        required = requiredValue.asBoolean();
                    }
                }
            }
            ResultHandle type = Types.getTypeHandle(metaMethod, pi.type());
            ResultHandle provider;
            if (pi.type().name().equals(DotNames.MCP_CONNECTION)) {
                provider = metaMethod.load(FeatureArgument.Provider.MCP_CONNECTION);
            } else if (pi.type().name().equals(DotNames.REQUEST_ID)) {
                provider = metaMethod.load(FeatureArgument.Provider.REQUEST_ID);
            } else {
                provider = metaMethod.load(FeatureArgument.Provider.PARAMS);
            }
            ResultHandle arg = metaMethod.newInstance(
                    MethodDescriptor.ofConstructor(FeatureArgument.class, String.class, String.class, boolean.class,
                            Type.class, FeatureArgument.Provider.class),
                    metaMethod.load(name), metaMethod.load(description), metaMethod.load(required), type,
                    provider);
            Gizmo.listOperations(metaMethod).on(args).add(arg);
        }
        ResultHandle info = metaMethod.newInstance(
                MethodDescriptor.ofConstructor(FeatureMethodInfo.class, String.class, String.class, String.class, String.class,
                        List.class),
                metaMethod.load(featureMethod.getName()), metaMethod.load(featureMethod.getDescription()),
                featureMethod.getUri() == null ? metaMethod.loadNull() : metaMethod.load(featureMethod.getUri()),
                featureMethod.getMimeType() == null ? metaMethod.loadNull() : metaMethod.load(featureMethod.getMimeType()),
                args);
        ResultHandle invoker = metaMethod
                .newInstance(MethodDescriptor.ofConstructor(featureMethod.getInvoker().getClassName()));
        ResultHandle executionModel = metaMethod.load(executionModel(featureMethod.getMethod(), transformedAnnotations));
        ResultHandle resultMapper = getMapper(metaMethod, featureMethod.getMethod().returnType(), featureMethod.getFeature());
        ResultHandle metadata = metaMethod.newInstance(
                MethodDescriptor.ofConstructor(FeatureMetadata.class, Feature.class, FeatureMethodInfo.class, Invoker.class,
                        ExecutionModel.class, Function.class),
                metaMethod.load(featureMethod.getFeature()), info, invoker, executionModel, resultMapper);
        metaMethod.returnValue(metadata);

        Gizmo.listOperations(method).on(retList)
                .add(method.invokeVirtualMethod(metaMethod.getMethodDescriptor(), method.getThis()));
    }

    private ResultHandle getMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType,
            Feature feature) {
        // At this point the method return type is already validated
        return switch (feature) {
            case PROMPT -> promptMapper(bytecode, returnType);
            case TOOL -> toolMapper(bytecode, returnType);
            case RESOURCE -> resourceMapper(bytecode, returnType);
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
            return resultMapper(bytecode, "TO_UNI");
        } else if (isContent(returnType.name())) {
            return resultMapper(bytecode, "TOOL_CONTENT");
        } else if (returnType.name().equals(DotNames.STRING)) {
            return resultMapper(bytecode, "TOOL_STRING");
        } else if (returnType.name().equals(DotNames.LIST)) {
            if (returnType.asParameterizedType().arguments().get(0).name().equals(DotNames.STRING)) {
                return resultMapper(bytecode, "TOOL_LIST_STRING");
            }
            return resultMapper(bytecode, "TOOL_LIST_CONTENT");
        } else if (returnType.name().equals(DotNames.UNI)) {
            org.jboss.jandex.Type typeArg = returnType.asParameterizedType().arguments().get(0);
            if (typeArg.name().equals(DotNames.TOOL_RESPONSE)) {
                return resultMapper(bytecode, "IDENTITY");
            } else if (isContent(typeArg.name())) {
                return resultMapper(bytecode, "TOOL_UNI_CONTENT");
            } else if (typeArg.name().equals(DotNames.STRING)) {
                return resultMapper(bytecode, "TOOL_UNI_STRING");
            } else if (typeArg.name().equals(DotNames.LIST)) {
                if (typeArg.asParameterizedType().arguments().get(0).name().equals(DotNames.STRING)) {
                    return resultMapper(bytecode, "TOOL_UNI_LIST_STRING");
                }
                return resultMapper(bytecode, "TOOL_UNI_LIST_CONTENT");
            }
        }
        throw new IllegalArgumentException("Unsupported return type");
    }

    private ResultHandle resourceMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType) {
        if (returnType.name().equals(DotNames.RESOURCE_RESPONSE)) {
            return resultMapper(bytecode, "TO_UNI");
        } else if (isResourceContents(returnType.name())) {
            return resultMapper(bytecode, "RESOURCE_CONTENT");
        } else if (returnType.name().equals(DotNames.LIST)) {
            return resultMapper(bytecode, "RESOURCE_LIST_CONTENT");
        } else if (returnType.name().equals(DotNames.UNI)) {
            org.jboss.jandex.Type typeArg = returnType.asParameterizedType().arguments().get(0);
            if (typeArg.name().equals(DotNames.RESOURCE_RESPONSE)) {
                return resultMapper(bytecode, "IDENTITY");
            } else if (isResourceContents(typeArg.name())) {
                return resultMapper(bytecode, "RESOURCE_UNI_CONTENT");
            } else if (typeArg.name().equals(DotNames.LIST)) {
                return resultMapper(bytecode, "RESOURCE_UNI_LIST_CONTENT");
            }
        }
        throw new IllegalArgumentException("Unsupported return type");
    }

    private boolean isContent(DotName typeName) {
        return DotNames.CONTENT.equals(typeName) || DotNames.TEXT_CONTENT.equals(typeName)
                || DotNames.IMAGE_CONTENT.equals(typeName) || DotNames.RESOURCE_CONTENT.equals(typeName);
    }

    private boolean isResourceContents(DotName typeName) {
        return DotNames.RESOURCE_CONTENS.equals(typeName) || DotNames.TEXT_RESOURCE_CONTENS.equals(typeName)
                || DotNames.BLOB_RESOURCE_CONTENS.equals(typeName);
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
