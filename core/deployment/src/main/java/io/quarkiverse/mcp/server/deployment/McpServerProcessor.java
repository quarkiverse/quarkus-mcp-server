package io.quarkiverse.mcp.server.deployment;

import static io.quarkiverse.mcp.server.runtime.Feature.NOTIFICATION;
import static io.quarkiverse.mcp.server.runtime.Feature.PROMPT;
import static io.quarkiverse.mcp.server.runtime.Feature.PROMPT_COMPLETE;
import static io.quarkiverse.mcp.server.runtime.Feature.RESOURCE;
import static io.quarkiverse.mcp.server.runtime.Feature.RESOURCE_TEMPLATE;
import static io.quarkiverse.mcp.server.runtime.Feature.RESOURCE_TEMPLATE_COMPLETE;
import static io.quarkiverse.mcp.server.runtime.Feature.TOOL;
import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.invoke.Invoker;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type.Kind;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.ModelHint;
import io.quarkiverse.mcp.server.ModelPreferences;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest.IncludeContext;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.quarkiverse.mcp.server.runtime.BuiltinDefaultValueConverters;
import io.quarkiverse.mcp.server.runtime.EncoderMapper;
import io.quarkiverse.mcp.server.runtime.ExecutionModel;
import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkiverse.mcp.server.runtime.FeatureArgument;
import io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata;
import io.quarkiverse.mcp.server.runtime.FeatureMethodInfo;
import io.quarkiverse.mcp.server.runtime.JsonTextContentEncoder;
import io.quarkiverse.mcp.server.runtime.JsonTextResourceContentsEncoder;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpServerRecorder;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptEncoderResultMapper;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceContentsEncoderResultMapper;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompleteManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl.VariableMatcher;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.runtime.ResultMappers;
import io.quarkiverse.mcp.server.runtime.SamplingRequestImpl;
import io.quarkiverse.mcp.server.runtime.ToolEncoderResultMapper;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.WrapBusinessErrorInterceptor;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.InvokerFactoryBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.Annotations;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.arc.processor.InvokerBuilder;
import io.quarkus.arc.processor.KotlinUtils;
import io.quarkus.arc.processor.Types;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.execannotations.ExecutionModelAnnotationsAllowedBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

class McpServerProcessor {

    private static final Logger LOG = Logger.getLogger(McpServerProcessor.class);

    private static final Map<DotName, Feature> ANNOTATION_TO_FEATURE = Map.of(
            DotNames.PROMPT, PROMPT,
            DotNames.COMPLETE_PROMPT, PROMPT_COMPLETE,
            DotNames.RESOURCE, RESOURCE,
            DotNames.RESOURCE_TEMPLATE, RESOURCE_TEMPLATE,
            DotNames.COMPLETE_RESOURCE_TEMPLATE, RESOURCE_TEMPLATE_COMPLETE,
            DotNames.TOOL, TOOL,
            DotNames.LANGCHAIN4J_TOOL, TOOL,
            DotNames.NOTIFICATION, NOTIFICATION);

    private static final String DEFAULT_VALUE = "defaultValue";

    @BuildStep
    void addBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        AdditionalBeanBuildItem.Builder unremovable = AdditionalBeanBuildItem.builder().setUnremovable();
        unremovable.addBeanClass("io.quarkiverse.mcp.server.runtime.ConnectionManager");
        unremovable.addBeanClass(ResponseHandlers.class);
        // Managers
        unremovable.addBeanClasses(PromptManagerImpl.class, ToolManagerImpl.class, ResourceManagerImpl.class,
                PromptCompletionManagerImpl.class, ResourceTemplateManagerImpl.class,
                ResourceTemplateCompleteManagerImpl.class, NotificationManagerImpl.class);
        // Encoders
        unremovable.addBeanClasses(JsonTextContentEncoder.class, JsonTextResourceContentsEncoder.class);
        // Result mappers
        unremovable.addBeanClasses(ToolEncoderResultMapper.class, ResourceContentsEncoderResultMapper.class,
                PromptEncoderResultMapper.class);
        additionalBeans.produce(unremovable.build());
        additionalBeans
                .produce(new AdditionalBeanBuildItem(WrapBusinessError.class, WrapBusinessErrorInterceptor.class));
    }

    @BuildStep
    AutoAddScopeBuildItem autoAddScope() {
        return AutoAddScopeBuildItem.builder()
                .containsAnnotations(ANNOTATION_TO_FEATURE.keySet().toArray(DotName[]::new))
                .defaultScope(BuiltinScope.SINGLETON)
                .build();
    }

    @BuildStep
    ExecutionModelAnnotationsAllowedBuildItem executionModelAnnotations(
            TransformedAnnotationsBuildItem transformedAnnotations) {
        Set<DotName> featureAnnotations = ANNOTATION_TO_FEATURE.keySet();
        return new ExecutionModelAnnotationsAllowedBuildItem(new Predicate<MethodInfo>() {
            @Override
            public boolean test(MethodInfo method) {
                return Annotations.containsAny(transformedAnnotations.getAnnotations(method), featureAnnotations);
            }
        });
    }

    @BuildStep
    void collectFeatureMethods(BeanDiscoveryFinishedBuildItem beanDiscovery, InvokerFactoryBuildItem invokerFactory,
            List<DefaultValueConverterBuildItem> defaultValueConverters, CombinedIndexBuildItem combinedIndex,
            BuildProducer<FeatureMethodBuildItem> features, BuildProducer<ValidationErrorBuildItem> errors) {
        Map<Feature, List<FeatureMethodBuildItem>> found = new HashMap<>();

        for (BeanInfo bean : beanDiscovery.beanStream().classBeans().filter(this::hasFeatureMethod)) {
            ClassInfo beanClass = bean.getTarget().get().asClass();
            for (MethodInfo method : beanClass.methods()) {
                AnnotationInstance featureAnnotation = getFeatureAnnotation(method);
                if (featureAnnotation != null) {
                    Feature feature = getFeature(featureAnnotation);
                    validateFeatureMethod(method, feature, featureAnnotation, defaultValueConverters,
                            combinedIndex.getComputingIndex());
                    String name;
                    if (feature == PROMPT_COMPLETE
                            || feature == RESOURCE_TEMPLATE_COMPLETE) {
                        name = featureAnnotation.value().asString();
                    } else {
                        AnnotationValue nameValue = featureAnnotation.value("name");
                        name = nameValue != null ? nameValue.asString() : method.name();
                    }

                    String description;
                    if (feature == TOOL && method.hasDeclaredAnnotation(DotNames.LANGCHAIN4J_TOOL)) {
                        AnnotationValue value = featureAnnotation.value();
                        description = value != null ? Arrays.stream(value.asStringArray()).collect(Collectors.joining()) : "";
                    } else if (feature == NOTIFICATION) {
                        // Description holds the notification type
                        description = featureAnnotation.value().asEnum();
                    } else {
                        AnnotationValue descValue = featureAnnotation.value("description");
                        description = descValue != null ? descValue.asString() : "";
                    }

                    InvokerBuilder invokerBuilder = invokerFactory.createInvoker(bean, method)
                            .withInstanceLookup();

                    String uri = null;
                    String mimeType = null;
                    if (feature == RESOURCE) {
                        AnnotationValue uriValue = featureAnnotation.value("uri");
                        uri = uriValue != null ? uriValue.asString() : null;
                        AnnotationValue mimeTypeValue = featureAnnotation.value("mimeType");
                        mimeType = mimeTypeValue != null ? mimeTypeValue.asString() : null;
                    } else if (feature == RESOURCE_TEMPLATE) {
                        AnnotationValue uriValue = featureAnnotation.value("uriTemplate");
                        uri = uriValue != null ? uriValue.asString() : null;
                        AnnotationValue mimeTypeValue = featureAnnotation.value("mimeType");
                        mimeType = mimeTypeValue != null ? mimeTypeValue.asString() : null;
                    }
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
                    .collect(Collectors.toMap(this::getDuplicateValidationName, List::of, (v1, v2) -> {
                        List<FeatureMethodBuildItem> list = new ArrayList<>();
                        list.addAll(v1);
                        list.addAll(v2);
                        return list;
                    }));
            for (Entry<String, List<FeatureMethodBuildItem>> e : byName.entrySet()) {
                if (e.getValue().size() > 1) {
                    String message = "Duplicate feature method found for %s:\n\t%s"
                            .formatted(e.getKey(),
                                    e.getValue().stream().map(Object::toString).collect(Collectors.joining("\n\t")));
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

        // Check existing prompts for completions
        List<FeatureMethodBuildItem> prompts = found.get(PROMPT);
        List<FeatureMethodBuildItem> promptCompletions = found.get(PROMPT_COMPLETE);
        if (promptCompletions != null) {
            for (FeatureMethodBuildItem completion : promptCompletions) {
                if (prompts == null || prompts.stream().noneMatch(p -> p.getName().equals(completion.getName()))) {
                    String message = "Prompt %s does not exist for completion: %s"
                            .formatted(completion.getName(), completion);
                    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(message)));
                }
            }
        }

        // Check existing resource templates for completions
        List<FeatureMethodBuildItem> resourceTemplates = found.get(RESOURCE_TEMPLATE);
        List<FeatureMethodBuildItem> resourceTemplateCompletions = found.get(RESOURCE_TEMPLATE_COMPLETE);
        if (resourceTemplateCompletions != null) {
            for (FeatureMethodBuildItem completion : resourceTemplateCompletions) {
                if (resourceTemplates == null
                        || resourceTemplates.stream().noneMatch(p -> p.getName().equals(completion.getName()))) {
                    String message = "Resource template %s does not exist for completion: %s"
                            .formatted(completion.getName(), completion);
                    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(message)));
                }
            }
        }
    }

    private String getDuplicateValidationName(FeatureMethodBuildItem featureMethod) {
        if (featureMethod.getFeature() == PROMPT_COMPLETE || featureMethod.getFeature() == RESOURCE_TEMPLATE_COMPLETE) {
            MethodParameterInfo argument = featureMethod.getMethod().parameters().stream()
                    .filter(p -> providerFrom(p.type()) == Provider.PARAMS).findFirst().orElseThrow();
            String argumentName = argument.name();
            AnnotationInstance completeArg = argument.declaredAnnotation(DotNames.COMPLETE_ARG);
            if (completeArg != null) {
                AnnotationValue value = completeArg.value();
                if (value != null) {
                    argumentName = value.asString();
                }
            }
            return featureMethod.getName() + argumentName;
        }
        return featureMethod.getName();
    }

    private AnnotationInstance getFeatureAnnotation(MethodInfo method) {
        for (AnnotationInstance annotation : method.declaredAnnotations()) {
            if (ANNOTATION_TO_FEATURE.containsKey(annotation.name())) {
                return annotation;
            }
        }
        return null;
    }

    private Feature getFeature(AnnotationInstance annotation) {
        Feature ret = ANNOTATION_TO_FEATURE.get(annotation.name());
        if (ret != null) {
            return ret;
        }
        throw new IllegalStateException();
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void generateMetadata(McpServerRecorder recorder, RecorderContext recorderContext,
            BeanDiscoveryFinishedBuildItem beanDiscovery,
            List<FeatureMethodBuildItem> featureMethods, TransformedAnnotationsBuildItem transformedAnnotations,
            List<DefaultValueConverterBuildItem> defaultValueConverters,
            BuildProducer<GeneratedClassBuildItem> generatedClasses, BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        // Note that the generated McpMetadata impl must be considered an application class
        // so that it can see the generated invokers
        ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClasses, true);

        String metadataClassName = "io.quarkiverse.mcp.server.runtime.McpMetadata_Impl";
        ClassCreator metadataCreator = ClassCreator.builder().classOutput(classOutput)
                .className(metadataClassName)
                .interfaces(McpMetadata.class)
                .build();

        boolean isResourceManagerUsed = false, isResourceTemplateManagerUsed = false, isPromptManagerUsed = false,
                isToolManagerUsed = false;
        for (InjectionPointInfo ip : beanDiscovery.getInjectionPoints()) {
            if (ip.getRequiredType().name().equals(DotNames.RESOURCE_MANAGER)) {
                isResourceManagerUsed = true;
            } else if (ip.getRequiredType().name().equals(DotNames.RESOURCE_TEMPLATE_MANAGER)) {
                isResourceTemplateManagerUsed = true;
            } else if (ip.getRequiredType().name().equals(DotNames.PROMPT_MANAGER)) {
                isPromptManagerUsed = true;
            } else if (ip.getRequiredType().name().equals(DotNames.TOOL_MANAGER)) {
                isToolManagerUsed = true;
            }
        }

        // io.quarkiverse.mcp.server.runtime.McpMetadata.isResourceManagerUsed()
        MethodCreator isResourceManagerUsedMethod = metadataCreator.getMethodCreator("isResourceManagerUsed", boolean.class);
        isResourceManagerUsedMethod.returnValue(isResourceManagerUsedMethod.load(isResourceManagerUsed));

        // io.quarkiverse.mcp.server.runtime.McpMetadata.isResourceManagerUsed()
        MethodCreator isResourceTemplateManagerUsedMethod = metadataCreator.getMethodCreator("isResourceTemplateManagerUsed",
                boolean.class);
        isResourceTemplateManagerUsedMethod
                .returnValue(isResourceTemplateManagerUsedMethod.load(isResourceTemplateManagerUsed));

        // io.quarkiverse.mcp.server.runtime.McpMetadata.isPromptManagerUsed()
        MethodCreator isPromptManagerUsedMethod = metadataCreator.getMethodCreator("isPromptManagerUsed", boolean.class);
        isPromptManagerUsedMethod.returnValue(isPromptManagerUsedMethod.load(isPromptManagerUsed));

        // io.quarkiverse.mcp.server.runtime.McpMetadata.isToolManagerUsed()
        MethodCreator isToolManagerUsedMethod = metadataCreator.getMethodCreator("isToolManagerUsed", boolean.class);
        isToolManagerUsedMethod.returnValue(isToolManagerUsedMethod.load(isToolManagerUsed));

        AtomicInteger counter = new AtomicInteger();

        // io.quarkiverse.mcp.server.runtime.McpMetadata.prompts()
        MethodCreator promptsMethod = metadataCreator.getMethodCreator("prompts", List.class);
        ResultHandle retPrompts = Gizmo.newArrayList(promptsMethod);
        for (FeatureMethodBuildItem prompt : featureMethods.stream().filter(FeatureMethodBuildItem::isPrompt).toList()) {
            processFeatureMethod(counter, metadataCreator, promptsMethod, prompt, retPrompts, transformedAnnotations,
                    DotNames.PROMPT_ARG);
        }
        promptsMethod.returnValue(retPrompts);

        // io.quarkiverse.mcp.server.runtime.McpMetadata.promptCompletions()
        MethodCreator promptCompletionsMethod = metadataCreator.getMethodCreator("promptCompletions", List.class);
        ResultHandle retPromptCompletions = Gizmo.newArrayList(promptCompletionsMethod);
        for (FeatureMethodBuildItem promptCompletion : featureMethods.stream().filter(FeatureMethodBuildItem::isPromptComplete)
                .toList()) {
            processFeatureMethod(counter, metadataCreator, promptCompletionsMethod, promptCompletion, retPromptCompletions,
                    transformedAnnotations,
                    DotNames.COMPLETE_ARG);
        }
        promptCompletionsMethod.returnValue(retPromptCompletions);

        // io.quarkiverse.mcp.server.runtime.McpMetadata.tools()
        MethodCreator toolsMethod = metadataCreator.getMethodCreator("tools", List.class);
        ResultHandle retTools = Gizmo.newArrayList(toolsMethod);
        for (FeatureMethodBuildItem tool : featureMethods.stream().filter(FeatureMethodBuildItem::isTool).toList()) {
            processFeatureMethod(counter, metadataCreator, toolsMethod, tool, retTools, transformedAnnotations,
                    tool.getMethod().hasDeclaredAnnotation(DotNames.LANGCHAIN4J_TOOL) ? DotNames.LANGCHAIN4J_P
                            : DotNames.TOOL_ARG);
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

        // io.quarkiverse.mcp.server.runtime.McpMetadata.resourceTemplates()
        MethodCreator resourceTemplatesMethod = metadataCreator.getMethodCreator("resourceTemplates", List.class);
        ResultHandle retResourceTemplates = Gizmo.newArrayList(resourceTemplatesMethod);
        for (FeatureMethodBuildItem resourceTemplate : featureMethods.stream()
                .filter(FeatureMethodBuildItem::isResourceTemplate).toList()) {
            processFeatureMethod(counter, metadataCreator, resourceTemplatesMethod, resourceTemplate, retResourceTemplates,
                    transformedAnnotations,
                    DotNames.RESOURCE_TEMPLATE_ARG);
        }
        resourceTemplatesMethod.returnValue(retResourceTemplates);

        // io.quarkiverse.mcp.server.runtime.McpMetadata.resourceTemplateCompletions()
        MethodCreator resourceTemplateCompletionsMethod = metadataCreator.getMethodCreator("resourceTemplateCompletions",
                List.class);
        ResultHandle retResourceTemplateCompletions = Gizmo.newArrayList(resourceTemplateCompletionsMethod);
        for (FeatureMethodBuildItem resourceTemplateCompletion : featureMethods.stream()
                .filter(FeatureMethodBuildItem::isResourceTemplateComplete)
                .toList()) {
            processFeatureMethod(counter, metadataCreator, resourceTemplateCompletionsMethod, resourceTemplateCompletion,
                    retResourceTemplateCompletions,
                    transformedAnnotations,
                    DotNames.COMPLETE_ARG);
        }
        resourceTemplateCompletionsMethod.returnValue(retResourceTemplateCompletions);

        // io.quarkiverse.mcp.server.runtime.McpMetadata.notifications()
        MethodCreator notificationsMethod = metadataCreator.getMethodCreator("notifications", List.class);
        ResultHandle retNotifications = Gizmo.newArrayList(notificationsMethod);
        for (FeatureMethodBuildItem notification : featureMethods.stream().filter(FeatureMethodBuildItem::isNotification)
                .toList()) {
            processFeatureMethod(counter, metadataCreator, notificationsMethod, notification, retNotifications,
                    transformedAnnotations,
                    DotNames.NOTIFICATION);
        }
        notificationsMethod.returnValue(retNotifications);

        //  io.quarkiverse.mcp.server.runtime.McpMetadata.defaultValueConverters()
        MethodCreator convertersMethod = metadataCreator.getMethodCreator("defaultValueConverters", Map.class);
        ResultHandle retConverters = Gizmo.newHashMap(convertersMethod);
        for (DefaultValueConverterBuildItem converter : defaultValueConverters) {
            ResultHandle converterType = Types.getTypeHandle(convertersMethod, converter.getArgumentType());
            ResultHandle converterInstance = convertersMethod
                    .newInstance(MethodDescriptor.ofConstructor(converter.getClassName()));
            Gizmo.mapOperations(convertersMethod).on(retConverters).put(converterType, converterInstance);
        }
        convertersMethod.returnValue(retConverters);

        metadataCreator.close();

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(McpMetadata.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .runtimeValue(recorderContext.newInstance(metadataClassName))
                .done());
    }

    @BuildStep
    void registerForReflection(List<FeatureMethodBuildItem> featureMethods,
            List<DefaultValueConverterBuildItem> defaultValueConverters,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchies) {
        // FIXME this is not ideal, JsonObject.encode() may use Jackson under the hood which requires reflection
        for (FeatureMethodBuildItem m : featureMethods) {
            for (org.jboss.jandex.Type paramType : m.getMethod().parameterTypes()) {
                if (paramType.kind() == Kind.PRIMITIVE
                        || paramType.name().equals(DotNames.STRING)
                        || paramType.name().equals(DotNames.MCP_CONNECTION)
                        || paramType.name().equals(DotNames.MCP_LOG)
                        || paramType.name().equals(DotNames.REQUEST_ID)
                        || paramType.name().equals(DotNames.REQUEST_URI)
                        || paramType.name().equals(DotNames.PROGRESS)
                        || paramType.name().equals(DotNames.ROOTS)
                        || paramType.name().equals(DotNames.SAMPLING)) {
                    continue;
                }
                reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(paramType).build());
            }
        }
        // Register all default value converters
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(defaultValueConverters.stream()
                .map(DefaultValueConverterBuildItem::getClassName).toList().toArray(String[]::new))
                .constructors()
                .build());

        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(Content.class, TextContent.class, ImageContent.class,
                EmbeddedResource.class, PromptResponse.class, PromptMessage.class, ToolResponse.class, FeatureMethodInfo.class,
                FeatureArgument.class, ResourceResponse.class, ResourceContents.class, TextResourceContents.class,
                BlobResourceContents.class, Role.class, SamplingMessage.class, ModelPreferences.class, ModelHint.class,
                IncludeContext.class,
                SamplingRequestImpl.class)
                .methods().build());
        reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(List.class).build());
        reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(Map.class).build());
    }

    @BuildStep
    AdditionalIndexedClassesBuildItem indexBuiltinDefaultValueConverters() {
        return new AdditionalIndexedClassesBuildItem(BuiltinDefaultValueConverters.class.getName(),
                BuiltinDefaultValueConverters.BooleanConverter.class.getName(),
                BuiltinDefaultValueConverters.ByteConverter.class.getName(),
                BuiltinDefaultValueConverters.ShortConverter.class.getName(),
                BuiltinDefaultValueConverters.IntegerConverter.class.getName(),
                BuiltinDefaultValueConverters.LongConverter.class.getName(),
                BuiltinDefaultValueConverters.FloatConverter.class.getName(),
                BuiltinDefaultValueConverters.DoubleConverter.class.getName(),
                BuiltinDefaultValueConverters.CharacterConverter.class.getName());
    }

    @BuildStep
    public void collectDefaultValueConverters(CombinedIndexBuildItem combinedIndex,
            BuildProducer<DefaultValueConverterBuildItem> converters) {

        Map<org.jboss.jandex.Type, List<DefaultValueConverterBuildItem>> found = new HashMap<>();
        for (ClassInfo converter : combinedIndex.getIndex().getAllKnownImplementors(DefaultValueConverter.class)) {
            if (converter.isAbstract()) {
                continue;
            }
            if (!Modifier.isPublic(converter.flags())) {
                LOG.warnf("Non-public default value converter ignored: %s", converter);
                continue;
            }
            if (!converter.hasNoArgsConstructor()) {
                LOG.warnf("Default value converter that does not declare a no-args constructor ignored: %s", converter);
                continue;
            }
            int priority;
            AnnotationInstance priorityAnnotation = converter.annotation(Priority.class);
            if (priorityAnnotation != null) {
                priority = priorityAnnotation.value().asInt();
            } else {
                priority = 0;
            }
            List<org.jboss.jandex.Type> typeParams = JandexUtil.resolveTypeParameters(converter.name(),
                    DotNames.DEFAULT_VALUE_CONVERTER, combinedIndex.getComputingIndex());
            org.jboss.jandex.Type argumentType = typeParams.get(0);
            found.compute(argumentType, (k, v) -> {
                if (v == null) {
                    v = new ArrayList<>();
                }
                v.add(new DefaultValueConverterBuildItem(priority, converter.name().toString(), argumentType));
                return v;
            });
        }

        for (List<DefaultValueConverterBuildItem> list : found.values()) {
            list.sort(Comparator.comparingInt(DefaultValueConverterBuildItem::getPriority).reversed());
            // Only the converter with highest priority is used
            converters.produce(list.get(0));
        }
    }

    private void validateFeatureMethod(MethodInfo method, Feature feature, AnnotationInstance featureAnnotation,
            List<DefaultValueConverterBuildItem> defaultValueConverters, IndexView index) {
        if (Modifier.isStatic(method.flags())) {
            throw new IllegalStateException(feature + " method must not be static: " + method);
        }
        if (Modifier.isPrivate(method.flags())) {
            throw new IllegalStateException(feature + " method must not be private: " + method);
        }
        if (method.returnType().kind() == Kind.VOID && feature != NOTIFICATION) {
            throw new IllegalStateException(feature + " method may not return void: " + method);
        }
        switch (feature) {
            case PROMPT -> validatePromptMethod(method);
            case PROMPT_COMPLETE -> validatePromptCompleteMethod(method);
            case TOOL -> validateToolMethod(method, defaultValueConverters, index);
            case RESOURCE -> validateResourceMethod(method);
            case RESOURCE_TEMPLATE -> validateResourceTemplateMethod(method, featureAnnotation);
            case RESOURCE_TEMPLATE_COMPLETE -> validateResourceTemplateCompleteMethod(method);
            case NOTIFICATION -> validateNotificationMethod(method);
            default -> throw new IllegalArgumentException("Unsupported feature: " + feature);
        }
    }

    private static final Set<org.jboss.jandex.Type> PROMPT_TYPES = Set.of(ClassType.create(DotNames.PROMPT_RESPONSE),
            ClassType.create(DotNames.PROMPT_MESSAGE));

    private void validatePromptMethod(MethodInfo method) {
        // No need to validate return type

        List<MethodParameterInfo> parameters = parameters(method);
        for (MethodParameterInfo param : parameters) {
            if (!param.type().name().equals(DotNames.STRING)) {
                throw new IllegalStateException("Prompt method must only consume String parameters: " + method);
            }
        }
    }

    private static final Set<org.jboss.jandex.Type> COMPLETE_TYPES = Set.of(ClassType.create(DotNames.COMPLETE_RESPONSE),
            ClassType.create(DotNames.STRING));

    private void validatePromptCompleteMethod(MethodInfo method) {
        org.jboss.jandex.Type type = method.returnType();
        if (DotNames.UNI.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (DotNames.LIST.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (!COMPLETE_TYPES.contains(type)) {
            throw new IllegalStateException("Unsupported Prompt complete method return type: " + method);
        }

        List<MethodParameterInfo> parameters = parameters(method);
        if (parameters.size() != 1 || !parameters.get(0).type().name().equals(DotNames.STRING)) {
            throw new IllegalStateException("Prompt complete must consume exactly one String argument: " + method);
        }
    }

    private void validateResourceTemplateCompleteMethod(MethodInfo method) {
        org.jboss.jandex.Type type = method.returnType();
        if (DotNames.UNI.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (DotNames.LIST.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (!COMPLETE_TYPES.contains(type)) {
            throw new IllegalStateException("Unsupported Resource template complete method return type: " + method);
        }

        List<MethodParameterInfo> parameters = parameters(method);
        if (parameters.size() != 1 || !parameters.get(0).type().name().equals(DotNames.STRING)) {
            throw new IllegalStateException("Resource template complete must consume exactly one String argument: " + method);
        }
    }

    private static final Set<org.jboss.jandex.Type> TOOL_TYPES = Set.of(ClassType.create(DotNames.TOOL_RESPONSE),
            ClassType.create(DotNames.CONTENT), ClassType.create(DotNames.TEXT_CONTENT),
            ClassType.create(DotNames.IMAGE_CONTENT), ClassType.create(DotNames.EMBEDDED_RESOURCE),
            ClassType.create(DotNames.STRING));

    private void validateToolMethod(MethodInfo method, List<DefaultValueConverterBuildItem> defaultValueConverters,
            IndexView index) {
        for (MethodParameterInfo p : method.parameters()) {
            AnnotationInstance toolArg = p.annotation(DotNames.TOOL_ARG);
            if (toolArg != null) {
                AnnotationValue defaultValueValue = toolArg.value(DEFAULT_VALUE);
                if (defaultValueValue != null) {
                    // Strings and enums are handled specifically
                    if (p.type().name().equals(DotNames.STRING)) {
                        continue;
                    }
                    if (p.type().kind() == Kind.CLASS) {
                        ClassInfo pclazz = index.getClassByName(p.type().name());
                        if (pclazz != null && pclazz.isEnum()) {
                            continue;
                        }
                    }
                    // Make sure primitives are boxed
                    org.jboss.jandex.Type argType;
                    if (p.type().kind() == Kind.PRIMITIVE) {
                        argType = PrimitiveType.box(p.type().asPrimitiveType());
                    } else {
                        argType = p.type();
                    }
                    if (defaultValueConverters.stream().noneMatch(c -> c.getArgumentType().equals(argType))) {
                        throw new IllegalStateException(
                                "No matching default value converter found for argument type [" + p.type() + "] declared on: "
                                        + p);
                    }
                }
            }
        }
    }

    private boolean useEncoder(org.jboss.jandex.Type type, Set<org.jboss.jandex.Type> types) {
        if (DotNames.UNI.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        if (DotNames.LIST.equals(type.name()) && type.kind() == Kind.PARAMETERIZED_TYPE) {
            type = type.asParameterizedType().arguments().get(0);
        }
        return !types.contains(type);
    }

    static final Set<org.jboss.jandex.Type> RESOURCE_TYPES = Set.of(ClassType.create(DotNames.RESOURCE_RESPONSE),
            ClassType.create(DotNames.RESOURCE_CONTENTS), ClassType.create(DotNames.TEXT_RESOURCE_CONTENTS),
            ClassType.create(DotNames.BLOB_RESOURCE_CONTENTS));

    private void validateResourceMethod(MethodInfo method) {
        // No need to validate return type

        List<MethodParameterInfo> parameters = parameters(method);
        if (!parameters.isEmpty()) {
            throw new IllegalStateException(
                    "Resource method may only accept built-in parameter types" + method);
        }
    }

    private void validateResourceTemplateMethod(MethodInfo method, AnnotationInstance featureAnnotation) {
        // No need to validate return type

        AnnotationValue uriTemplateValue = featureAnnotation.value("uriTemplate");
        if (uriTemplateValue == null) {
            throw new IllegalStateException("URI template not found");
        }
        VariableMatcher variableMatcher = ResourceTemplateManagerImpl.createMatcherFromUriTemplate(uriTemplateValue.asString());

        List<MethodParameterInfo> parameters = parameters(method);
        for (MethodParameterInfo param : parameters) {
            if (!param.type().name().equals(DotNames.STRING)) {
                throw new IllegalStateException("Resource template method must only consume String parameters: " + method);
            }
            if (!variableMatcher.variables().contains(param.name())) {
                throw new IllegalStateException(
                        "Parameter [" + param.name() + "] does not match an URI template variable: " + method);
            }
        }
    }

    private void validateNotificationMethod(MethodInfo method) {
        if (method.returnType().kind() != Kind.VOID
                && (!method.returnType().name().equals(DotNames.UNI)
                        || !method.returnType().asParameterizedType().arguments().get(0).name()
                                .equals(DotName.createSimple(Void.class)))) {
            throw new IllegalStateException("Notification method must return void or Uni<Void>");
        }
        List<MethodParameterInfo> parameters = method.parameters();
        for (MethodParameterInfo param : parameters) {
            if (!param.type().name().equals(DotNames.MCP_CONNECTION)
                    && !param.type().name().equals(DotNames.MCP_LOG)
                    && !param.type().name().equals(DotNames.ROOTS)
                    && !param.type().name().equals(DotNames.SAMPLING)) {
                throw new IllegalStateException(
                        "Notification methods must only consume built-in parameter types [McpConnection, McpLog, Roots, Sampling]: "
                                + method);
            }
        }
    }

    private List<MethodParameterInfo> parameters(MethodInfo method) {
        return method.parameters().stream()
                .filter(p -> providerFrom(p.type()) == Provider.PARAMS).toList();
    }

    private boolean hasFeatureMethod(BeanInfo bean) {
        ClassInfo beanClass = bean.getTarget().get().asClass();
        for (DotName annotationName : ANNOTATION_TO_FEATURE.keySet()) {
            if (beanClass.hasAnnotation(annotationName)) {
                return true;
            }
        }
        return false;
    }

    private void processFeatureMethod(AtomicInteger counter, ClassCreator clazz, MethodCreator method,
            FeatureMethodBuildItem featureMethod, ResultHandle retList,
            TransformedAnnotationsBuildItem transformedAnnotations, DotName argAnnotationName) {
        String methodName = "meta$" + counter.incrementAndGet();
        MethodCreator metaMethod = clazz.getMethodCreator(methodName, FeatureMetadata.class);

        ResultHandle args = Gizmo.newArrayList(metaMethod);
        for (MethodParameterInfo param : featureMethod.getMethod().parameters()) {
            String name = param.name();
            String description = "";
            // Argument is required by default
            boolean required = true;
            String defaultValue = null;
            if (argAnnotationName != null) {
                AnnotationInstance argAnnotation = param.declaredAnnotation(argAnnotationName);
                if (argAnnotation != null) {
                    AnnotationValue nameValue;
                    if (DotNames.LANGCHAIN4J_P.equals(argAnnotationName)) {
                        nameValue = argAnnotation.value();
                    } else {
                        nameValue = argAnnotation.value("name");
                    }
                    if (nameValue != null) {
                        name = nameValue.asString();
                    }
                    AnnotationValue descriptionValue = argAnnotation.value("description");
                    if (descriptionValue != null) {
                        description = descriptionValue.asString();
                    }
                    AnnotationValue requiredValue = argAnnotation.value("required");
                    if (requiredValue != null) {
                        // Annotation value always takes precedence
                        required = requiredValue.asBoolean();
                    } else if (DotNames.OPTIONAL.equals(param.type().name())
                            || hasDefaultValue(param)) {
                        // No annotation value is defined and Optional type is used or default value set
                        required = false;
                    }
                    AnnotationValue defaultValueValue = argAnnotation.value(DEFAULT_VALUE);
                    if (defaultValueValue != null) {
                        defaultValue = defaultValueValue.asString();
                    }
                } else if (DotNames.OPTIONAL.equals(param.type().name())) {
                    // No annotation is defined and Optional type is used
                    required = false;
                }
            }
            ResultHandle type = Types.getTypeHandle(metaMethod, param.type());
            ResultHandle provider = metaMethod.load(providerFrom(param.type()));
            ResultHandle arg = metaMethod.newInstance(
                    MethodDescriptor.ofConstructor(FeatureArgument.class, String.class, String.class, boolean.class,
                            Type.class, String.class, FeatureArgument.Provider.class),
                    metaMethod.load(name), metaMethod.load(description), metaMethod.load(required), type,
                    defaultValue != null ? metaMethod.load(defaultValue) : metaMethod.loadNull(),
                    provider);
            Gizmo.listOperations(metaMethod).on(args).add(arg);
        }
        ResultHandle info = metaMethod.newInstance(
                MethodDescriptor.ofConstructor(FeatureMethodInfo.class, String.class, String.class, String.class, String.class,
                        List.class, String.class),
                metaMethod.load(featureMethod.getName()), metaMethod.load(featureMethod.getDescription()),
                featureMethod.getUri() == null ? metaMethod.loadNull() : metaMethod.load(featureMethod.getUri()),
                featureMethod.getMimeType() == null ? metaMethod.loadNull() : metaMethod.load(featureMethod.getMimeType()),
                args, metaMethod.load(featureMethod.getMethod().declaringClass().name().toString()));
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

    private boolean hasDefaultValue(MethodParameterInfo param) {
        AnnotationInstance anno = param.annotation(DotNames.TOOL_ARG);
        if (anno == null) {
            anno = param.annotation(DotNames.PROMPT_ARG);
        }
        return anno != null && anno.value(DEFAULT_VALUE) != null;
    }

    private FeatureArgument.Provider providerFrom(org.jboss.jandex.Type type) {
        if (type.name().equals(DotNames.MCP_CONNECTION)) {
            return FeatureArgument.Provider.MCP_CONNECTION;
        } else if (type.name().equals(DotNames.REQUEST_ID)) {
            return FeatureArgument.Provider.REQUEST_ID;
        } else if (type.name().equals(DotNames.MCP_LOG)) {
            return FeatureArgument.Provider.MCP_LOG;
        } else if (type.name().equals(DotNames.REQUEST_URI)) {
            return FeatureArgument.Provider.REQUEST_URI;
        } else if (type.name().equals(DotNames.PROGRESS)) {
            return FeatureArgument.Provider.PROGRESS;
        } else if (type.name().equals(DotNames.ROOTS)) {
            return FeatureArgument.Provider.ROOTS;
        } else if (type.name().equals(DotNames.SAMPLING)) {
            return FeatureArgument.Provider.SAMPLING;
        } else {
            return FeatureArgument.Provider.PARAMS;
        }
    }

    private ResultHandle getMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType,
            Feature feature) {
        // Returns a function that converts the returned object to Uni<RESPONSE>
        // where the RESPONSE is one of ToolResponse, PromptResponse, ResourceResponse, CompleteResponse
        // IMPL NOTE: at this point the method return type is already validated
        return switch (feature) {
            case PROMPT -> promptResultMapper(bytecode, returnType);
            case PROMPT_COMPLETE -> readResultMapper(bytecode,
                    createMapperClassSimpleName(PROMPT_COMPLETE, returnType, DotNames.COMPLETE_RESPONSE, c -> "String"));
            case TOOL -> toolResultMapper(bytecode, returnType);
            case RESOURCE, RESOURCE_TEMPLATE -> resourceResultMapper(bytecode, returnType);
            case RESOURCE_TEMPLATE_COMPLETE -> readResultMapper(bytecode,
                    createMapperClassSimpleName(RESOURCE_TEMPLATE_COMPLETE, returnType, DotNames.COMPLETE_RESPONSE,
                            c -> "String"));
            case NOTIFICATION -> readResultMapper(bytecode, returnType.kind() == Kind.VOID ? "ToUni" : "Identity");
            default -> throw new IllegalArgumentException("Unsupported feature: " + feature);
        };
    }

    ResultHandle resourceResultMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType) {
        if (useEncoder(returnType, RESOURCE_TYPES)) {
            return encoderResultMapper(RESOURCE, bytecode, returnType, ResourceContentsEncoderResultMapper.class);
        } else {
            return readResultMapper(bytecode,
                    createMapperClassSimpleName(RESOURCE, returnType, DotNames.RESOURCE_RESPONSE, c -> "Content"));
        }
    }

    ResultHandle encoderResultMapper(Feature feature, BytecodeCreator bytecode, org.jboss.jandex.Type returnType,
            Class<?> mapperClazz) {
        // Arc.container().instance(mapperClazz).get();
        ResultHandle container = bytecode
                .invokeStaticMethod(MethodDescriptor.ofMethod(Arc.class, "container", ArcContainer.class));
        ResultHandle instance = bytecode.invokeInterfaceMethod(MethodDescriptor.ofMethod(ArcContainer.class, "instance",
                InstanceHandle.class, Class.class, Annotation[].class), container,
                bytecode.loadClass(mapperClazz), bytecode.newArray(Annotation.class, 0));
        ResultHandle mapper = bytecode.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(InstanceHandle.class, "get", Object.class),
                instance);
        if (DotNames.UNI.equals(returnType.name())) {
            if (feature != PROMPT && DotNames.LIST.equals(returnType.asParameterizedType().arguments().get(0).name())) {
                mapper = bytecode.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(mapperClazz, "uniList", EncoderMapper.class), mapper);
            } else {
                mapper = bytecode.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(mapperClazz, "uni", EncoderMapper.class), mapper);
            }
        } else if (feature != PROMPT && DotNames.LIST.equals(returnType.name())) {
            mapper = bytecode.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(mapperClazz, "list", EncoderMapper.class), mapper);
        }
        return mapper;
    }

    ResultHandle toolResultMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType) {
        if (useEncoder(returnType, TOOL_TYPES)) {
            return encoderResultMapper(TOOL, bytecode, returnType, ToolEncoderResultMapper.class);
        } else {
            return readResultMapper(bytecode, createMapperClassSimpleName(TOOL, returnType, DotNames.TOOL_RESPONSE, c -> {
                return isContent(c) ? "Content" : "String";
            }));
        }
    }

    ResultHandle promptResultMapper(BytecodeCreator bytecode, org.jboss.jandex.Type returnType) {
        if (useEncoder(returnType, PROMPT_TYPES)) {
            return encoderResultMapper(PROMPT, bytecode, returnType, PromptEncoderResultMapper.class);
        } else {
            return readResultMapper(bytecode,
                    createMapperClassSimpleName(PROMPT, returnType, DotNames.PROMPT_RESPONSE, c -> "OfMessage"));
        }
    }

    static String createMapperClassSimpleName(Feature feature, org.jboss.jandex.Type returnType,
            DotName baseType, Function<DotName, String> componentMapper) {
        if (returnType.name().equals(baseType)) {
            return "ToUni";
        }
        org.jboss.jandex.Type type = returnType;
        StringBuilder ret;
        if (feature == PROMPT_COMPLETE || feature == RESOURCE_TEMPLATE_COMPLETE) {
            ret = new StringBuilder("Complete");
        } else {
            String f = feature.toString();
            // TOOL -> Tool
            ret = new StringBuilder().append(f.charAt(0)).append(f.substring(1).toLowerCase());
        }
        if (DotNames.UNI.equals(type.name())) {
            type = type.asParameterizedType().arguments().get(0);
            if (type.name().equals(baseType)) {
                return "Identity";
            }
            ret.append("Uni");
        }
        if (DotNames.LIST.equals(type.name())) {
            type = type.asParameterizedType().arguments().get(0);
            ret.append("List");
        }
        ret.append(componentMapper.apply(type.name()));

        return ret.toString();
    }

    private boolean isContent(DotName typeName) {
        return DotNames.CONTENT.equals(typeName) || DotNames.TEXT_CONTENT.equals(typeName)
                || DotNames.IMAGE_CONTENT.equals(typeName) || DotNames.EMBEDDED_RESOURCE.equals(typeName);
    }

    private ResultHandle readResultMapper(BytecodeCreator bytecode, String mapperClassSimpleName) {
        String mapperClassName = ResultMappers.class.getName() + "$" + mapperClassSimpleName;
        return bytecode.readStaticField(FieldDescriptor.of(mapperClassName, "INSTANCE", mapperClassName));
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
