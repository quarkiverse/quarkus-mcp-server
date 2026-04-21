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
import java.lang.constant.ClassDesc;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.invoke.Invoker;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassInfo.NestingType;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.EquivalenceKey.TypeEquivalenceKey;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type.Kind;
import org.jboss.jandex.gizmo2.Jandex2Gizmo;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.GlobalInputSchemaGenerator;
import io.quarkiverse.mcp.server.GlobalOutputSchemaGenerator;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.ModelHint;
import io.quarkiverse.mcp.server.ModelPreferences;
import io.quarkiverse.mcp.server.PromptFilter;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceFilter;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateFilter;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest.IncludeContext;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolAnnotations;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.quarkiverse.mcp.server.runtime.BuiltinDefaultValueConverters;
import io.quarkiverse.mcp.server.runtime.CancellationRequests;
import io.quarkiverse.mcp.server.runtime.DefaultResourceContentsEncoder;
import io.quarkiverse.mcp.server.runtime.DefaultSchemaGenerator;
import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkiverse.mcp.server.runtime.FeatureArgument;
import io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata;
import io.quarkiverse.mcp.server.runtime.FeatureMethodInfo;
import io.quarkiverse.mcp.server.runtime.JsonTextContentEncoder;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpObjectMapperCustomizer;
import io.quarkiverse.mcp.server.runtime.McpServerRecorder;
import io.quarkiverse.mcp.server.runtime.MicrometerMcpMetrics;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptEncoderResultMapper;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceContentsEncoderResultMapper;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl.VariableMatcher;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.runtime.ResultMappers;
import io.quarkiverse.mcp.server.runtime.SamplingRequestImpl;
import io.quarkiverse.mcp.server.runtime.ToolEncoderResultMapper;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.ToolStructuredContentResultMapper;
import io.quarkiverse.mcp.server.runtime.WrapBusinessErrorInterceptor;
import io.quarkiverse.mcp.server.runtime.config.McpServerBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.tracing.McpTracingInstrumenter;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.InvokerFactoryBuildItem;
import io.quarkus.arc.deployment.OpenTelemetrySdkBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.Annotations;
import io.quarkus.arc.processor.BeanDeploymentValidator.ValidationContext;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.arc.processor.InvokerBuilder;
import io.quarkus.arc.processor.KotlinUtils;
import io.quarkus.arc.processor.RuntimeTypeCreator;
import io.quarkus.deployment.GeneratedClassGizmo2Adaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.execannotations.ExecutionModelAnnotationsAllowedBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.Var;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.creator.ClassCreator;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.Descs.MD_Collection;
import io.quarkus.gizmo2.desc.Descs.MD_Map;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;
import io.quarkus.runtime.metrics.MetricsFactory;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;

class McpServerProcessor {

    private static final Logger LOG = Logger.getLogger(McpServerProcessor.class);

    private static final String DEFAULT_VALUE = "defaultValue";

    @BuildStep
    FeatureAnnotationsBuildItem featureAnnotations(McpServersBuildTimeConfig config) {
        Map<DotName, Feature> annotationToFeature;
        if (config.supportLangchain4jAnnotations()) {
            annotationToFeature = Map.of(
                    DotNames.PROMPT, PROMPT,
                    DotNames.COMPLETE_PROMPT, PROMPT_COMPLETE,
                    DotNames.RESOURCE, RESOURCE,
                    DotNames.RESOURCE_TEMPLATE, RESOURCE_TEMPLATE,
                    DotNames.COMPLETE_RESOURCE_TEMPLATE, RESOURCE_TEMPLATE_COMPLETE,
                    DotNames.TOOL, TOOL,
                    DotNames.LANGCHAIN4J_TOOL, TOOL,
                    DotNames.NOTIFICATION, NOTIFICATION);
        } else {
            annotationToFeature = Map.of(
                    DotNames.PROMPT, PROMPT,
                    DotNames.COMPLETE_PROMPT, PROMPT_COMPLETE,
                    DotNames.RESOURCE, RESOURCE,
                    DotNames.RESOURCE_TEMPLATE, RESOURCE_TEMPLATE,
                    DotNames.COMPLETE_RESOURCE_TEMPLATE, RESOURCE_TEMPLATE_COMPLETE,
                    DotNames.TOOL, TOOL,
                    DotNames.NOTIFICATION, NOTIFICATION);
        }
        return new FeatureAnnotationsBuildItem(annotationToFeature);
    }

    @BuildStep
    void serverNames(BuildProducer<ServerNameBuildItem> serverNames) {
        serverNames.produce(new ServerNameBuildItem(McpServer.DEFAULT));
    }

    @BuildStep
    void addBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        AdditionalBeanBuildItem.Builder unremovable = AdditionalBeanBuildItem.builder().setUnremovable();
        unremovable.addBeanClass("io.quarkiverse.mcp.server.runtime.ConnectionManager");
        unremovable.addBeanClass(ResponseHandlers.class);
        unremovable.addBeanClass(CancellationRequests.class);
        unremovable.addBeanClass(DefaultSchemaGenerator.class);
        unremovable.addBeanClass(McpObjectMapperCustomizer.class);
        // Managers
        unremovable.addBeanClasses(PromptManagerImpl.class, ToolManagerImpl.class, ResourceManagerImpl.class,
                PromptCompletionManagerImpl.class, ResourceTemplateManagerImpl.class,
                ResourceTemplateCompletionManagerImpl.class, NotificationManagerImpl.class);
        // Encoders
        unremovable.addBeanClasses(JsonTextContentEncoder.class, DefaultResourceContentsEncoder.class);
        // Result mappers
        unremovable.addBeanClasses(ToolEncoderResultMapper.class, ResourceContentsEncoderResultMapper.class,
                PromptEncoderResultMapper.class, ToolStructuredContentResultMapper.class);
        additionalBeans.produce(unremovable.build());
        additionalBeans
                .produce(new AdditionalBeanBuildItem(WrapBusinessError.class, WrapBusinessErrorInterceptor.class));
    }

    @BuildStep
    void autoScopes(BuildProducer<AutoAddScopeBuildItem> autoScopes, FeatureAnnotationsBuildItem featureAnnotations) {
        // Add @Singleton to a class that contains a feature annotation
        // and at least one non-static method annotated with a feature annotation
        autoScopes.produce(AutoAddScopeBuildItem.builder()
                .containsAnnotations(featureAnnotations.annotationToFeature().keySet().toArray(DotName[]::new))
                .anyMethodMatches(featureAnnotations::isFeatureMethod)
                .defaultScope(BuiltinScope.SINGLETON)
                .build());
        // Add @Singleton to filters
        autoScopes.produce(AutoAddScopeBuildItem.builder()
                .implementsInterface(DotName.createSimple(ToolFilter.class))
                .defaultScope(BuiltinScope.SINGLETON)
                .build());
        autoScopes.produce(AutoAddScopeBuildItem.builder()
                .implementsInterface(DotName.createSimple(PromptFilter.class))
                .defaultScope(BuiltinScope.SINGLETON)
                .build());
        autoScopes.produce(AutoAddScopeBuildItem.builder()
                .implementsInterface(DotName.createSimple(ResourceFilter.class))
                .defaultScope(BuiltinScope.SINGLETON)
                .build());
        autoScopes.produce(AutoAddScopeBuildItem.builder()
                .implementsInterface(DotName.createSimple(ResourceTemplateFilter.class))
                .defaultScope(BuiltinScope.SINGLETON)
                .build());
        autoScopes.produce(AutoAddScopeBuildItem.builder()
                .implementsInterface(DotName.createSimple(InitialCheck.class))
                .defaultScope(BuiltinScope.SINGLETON)
                .build());
    }

    @BuildStep
    ExecutionModelAnnotationsAllowedBuildItem executionModelAnnotations(FeatureAnnotationsBuildItem featureAnnotations,
            TransformedAnnotationsBuildItem transformedAnnotations) {
        Set<DotName> annotations = featureAnnotations.annotationToFeature().keySet();
        return new ExecutionModelAnnotationsAllowedBuildItem(new Predicate<MethodInfo>() {
            @Override
            public boolean test(MethodInfo method) {
                return Annotations.containsAny(transformedAnnotations.getAnnotations(method), annotations);
            }
        });
    }

    @BuildStep
    void collectFeatureMethods(McpServersBuildTimeConfig config,
            BeanDiscoveryFinishedBuildItem beanDiscovery,
            InvokerFactoryBuildItem invokerFactory,
            List<DefaultValueConverterBuildItem> defaultValueConverters,
            BeanArchiveIndexBuildItem beanArchiveIndex,
            TransformedAnnotationsBuildItem transformedAnnotations,
            FeatureAnnotationsBuildItem featureAnnotations,
            BuildProducer<FeatureMethodBuildItem> features,
            BuildProducer<ValidationErrorBuildItem> errors) {

        List<Throwable> wrongUsages = findWrongAnnotationUsage(beanArchiveIndex.getIndex(), featureAnnotations, errors);
        if (!wrongUsages.isEmpty()) {
            errors.produce(new ValidationErrorBuildItem(wrongUsages));
        }

        Map<Feature, List<FeatureMethodBuildItem>> found = new HashMap<>();

        for (BeanInfo bean : beanDiscovery.beanStream().classBeans().filter(featureAnnotations::hasFeatureMethod)) {
            ClassInfo beanClass = bean.getTarget().get().asClass();
            feature: for (MethodInfo method : beanClass.methods()) {
                AnnotationInstance featureAnnotation = featureAnnotations.getFeatureAnnotation(method);
                if (featureAnnotation != null) {
                    Feature feature = featureAnnotations.getFeature(featureAnnotation);
                    validateFeatureMethod(method, feature, featureAnnotation, defaultValueConverters,
                            beanArchiveIndex.getIndex());
                    String name;
                    if (feature == PROMPT_COMPLETE
                            || feature == RESOURCE_TEMPLATE_COMPLETE) {
                        name = featureAnnotation.value().asString();
                    } else {
                        AnnotationValue nameValue = featureAnnotation.value("name");
                        name = nameValue != null ? nameValue.asString() : method.name();
                    }
                    String title = null;
                    AnnotationValue titleValue = featureAnnotation.value("title");
                    if (titleValue != null) {
                        title = titleValue.asString();
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
                    int size = -1;
                    boolean structuredContent = false;
                    org.jboss.jandex.Type outputSchemaFrom = null;
                    org.jboss.jandex.Type outputSchemaGenerator = null;
                    org.jboss.jandex.Type inputSchemaGenerator = null;
                    ToolManager.ToolAnnotations toolAnnotations = null;
                    Content.Annotations resourceAnnotations = null;
                    Map<String, String> metadata = new HashMap<>();

                    if (feature == RESOURCE) {
                        AnnotationValue uriValue = featureAnnotation.value("uri");
                        if (uriValue != null)
                            uri = uriValue.asString();
                        AnnotationValue mimeTypeValue = featureAnnotation.value("mimeType");
                        if (mimeTypeValue != null)
                            mimeType = mimeTypeValue.asString();
                        AnnotationValue sizeValue = featureAnnotation.value("size");
                        if (sizeValue != null)
                            size = sizeValue.asInt();
                        resourceAnnotations = parseResourceAnnotations(featureAnnotation);
                    } else if (feature == RESOURCE_TEMPLATE) {
                        AnnotationValue uriValue = featureAnnotation.value("uriTemplate");
                        if (uriValue != null)
                            uri = uriValue.asString();
                        AnnotationValue mimeTypeValue = featureAnnotation.value("mimeType");
                        if (mimeTypeValue != null)
                            mimeType = mimeTypeValue.asString();
                        resourceAnnotations = parseResourceAnnotations(featureAnnotation);
                    } else if (feature == TOOL) {
                        // Tool annotations
                        AnnotationValue annotations = featureAnnotation.value("annotations");
                        if (annotations != null) {
                            AnnotationInstance annotationsAnnotation = annotations.asNested();
                            AnnotationValue annoTitleValue = annotationsAnnotation.value("title");
                            AnnotationValue readOnlyHintValue = annotationsAnnotation.value("readOnlyHint");
                            AnnotationValue destructiveHintValue = annotationsAnnotation.value("destructiveHint");
                            AnnotationValue idempotentHintValue = annotationsAnnotation.value("idempotentHint");
                            AnnotationValue openWorldHintValue = annotationsAnnotation.value("openWorldHint");
                            toolAnnotations = new ToolAnnotations(annoTitleValue != null ? annoTitleValue.asString() : null,
                                    readOnlyHintValue != null ? readOnlyHintValue.asBoolean() : false,
                                    destructiveHintValue != null ? destructiveHintValue.asBoolean() : true,
                                    idempotentHintValue != null ? idempotentHintValue.asBoolean() : false,
                                    openWorldHintValue != null ? openWorldHintValue.asBoolean() : true);
                        }
                        AnnotationValue structuredContentValue = featureAnnotation.value("structuredContent");
                        if (structuredContentValue != null)
                            structuredContent = structuredContentValue.asBoolean();

                        AnnotationValue outputSchemaValue = featureAnnotation.value("outputSchema");
                        if (outputSchemaValue != null) {
                            AnnotationValue outputSchemaFromValue = outputSchemaValue.asNested().value("from");
                            if (outputSchemaFromValue != null) {
                                outputSchemaFrom = outputSchemaFromValue.asClass();
                            } else {
                                outputSchemaFrom = outputSchemaFromReturnType(method.returnType());
                            }
                            AnnotationValue outputSchemaGeneratorValue = outputSchemaValue.asNested().value("generator");
                            if (outputSchemaGeneratorValue != null) {
                                outputSchemaGenerator = outputSchemaGeneratorValue.asClass();
                            } else {
                                outputSchemaGenerator = ClassType.create(GlobalOutputSchemaGenerator.class);
                            }
                        } else if (structuredContent) {
                            outputSchemaFrom = outputSchemaFromReturnType(method.returnType());
                            outputSchemaGenerator = ClassType.create(GlobalOutputSchemaGenerator.class);
                        }

                        AnnotationValue inputSchemaValue = featureAnnotation.value("inputSchema");
                        if (inputSchemaValue != null) {
                            AnnotationValue inputSchemaValueGeneratorValue = inputSchemaValue.asNested().value("generator");
                            if (inputSchemaValueGeneratorValue != null) {
                                inputSchemaGenerator = inputSchemaValueGeneratorValue.asClass();
                            } else {
                                inputSchemaGenerator = ClassType.create(GlobalInputSchemaGenerator.class);
                            }
                        }
                    }

                    // @McpServer bindings
                    Set<String> servers = initServerBindings(config, beanArchiveIndex.getIndex(), method);

                    // Process metadata entries
                    AnnotationInstance metaField = method.declaredAnnotation(DotNames.META_FIELD);
                    if (metaField != null) {
                        addMetaField(metadata, metaField);
                    } else {
                        AnnotationInstance metaFields = method.declaredAnnotation(DotNames.META_FIELDS);
                        if (metaFields != null) {
                            for (AnnotationInstance entry : metaFields.value().asNestedArray()) {
                                addMetaField(metadata, entry);
                            }
                        }
                    }

                    // Input/output guardrails
                    List<DotName> inputGuardrails = List.of();
                    List<DotName> outputGuardrails = List.of();
                    if (feature == TOOL) {
                        AnnotationInstance guardrails = method.declaredAnnotation(DotNames.TOOL_GUARDRAILS);
                        if (guardrails != null) {
                            AnnotationValue input = guardrails.value("input");
                            if (input != null) {
                                inputGuardrails = new ArrayList<>();
                                for (org.jboss.jandex.Type clazz : input.asClassArray()) {
                                    inputGuardrails.add(clazz.name());
                                }
                            }
                            AnnotationValue output = guardrails.value("output");
                            if (output != null) {
                                outputGuardrails = new ArrayList<>();
                                for (org.jboss.jandex.Type clazz : output.asClassArray()) {
                                    outputGuardrails.add(clazz.name());
                                }
                            }
                        }
                    }

                    // Icons
                    DotName iconsProvider = null;
                    AnnotationInstance icons = method.declaredAnnotation(DotNames.ICONS);
                    if (icons != null) {
                        AnnotationValue value = icons.value();
                        if (value != null) {
                            iconsProvider = value.asClass().name();
                        }
                    }

                    if (feature == TOOL) {
                        for (String server : servers) {
                            McpServerBuildTimeConfig serverConfig = config.servers().get(server);
                            OptionalInt nameMaxLength = serverConfig.tools().nameMaxLength();
                            if (nameMaxLength.isPresent()
                                    && name.length() > nameMaxLength.getAsInt()) {
                                String message = "Tool name [%s] exceeds the maximum length of %s characters"
                                        .formatted(name, nameMaxLength.getAsInt());
                                errors.produce(new ValidationErrorBuildItem(new IllegalStateException(message)));
                                break feature;
                            }
                            Optional<Pattern> namePattern = serverConfig.tools().namePattern();
                            if (namePattern.isPresent()
                                    && !namePattern.get().matcher(name).matches()) {
                                String message = "Tool name [%s] does not match the pattern: %s"
                                        .formatted(name, namePattern.get());
                                errors.produce(new ValidationErrorBuildItem(new IllegalStateException(message)));
                                break feature;
                            }
                        }
                    }

                    FeatureMethodBuildItem fm = new FeatureMethodBuildItem(bean, method, invokerBuilder.build(), name,
                            title, description, uri, mimeType, size, feature, toolAnnotations,
                            servers, structuredContent,
                            outputSchemaFrom, outputSchemaGenerator, inputSchemaGenerator, resourceAnnotations, metadata,
                            inputGuardrails, outputGuardrails, executionModel(method, transformedAnnotations),
                            iconsProvider);
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

        // Check duplicate names - only report errors when features with the same name have overlapping server bindings
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
                    checkOverlappingServers(e.getKey(), e.getValue(), "Duplicate feature method", errors);
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
            for (Map.Entry<String, List<FeatureMethodBuildItem>> e : byUri.entrySet()) {
                if (e.getValue().size() > 1) {
                    checkOverlappingServers(e.getKey(), e.getValue(), "Duplicate resource uri", errors);
                }
            }
        }

        // Check duplicate uris for resource templates
        List<FeatureMethodBuildItem> resourceTemplates = found.get(RESOURCE_TEMPLATE);
        if (resourceTemplates != null) {
            Map<String, List<FeatureMethodBuildItem>> byUri = resourceTemplates.stream()
                    .collect(Collectors.toMap(FeatureMethodBuildItem::getUri, List::of, (v1, v2) -> {
                        List<FeatureMethodBuildItem> list = new ArrayList<>();
                        list.addAll(v1);
                        list.addAll(v2);
                        return list;
                    }));
            for (Map.Entry<String, List<FeatureMethodBuildItem>> e : byUri.entrySet()) {
                if (e.getValue().size() > 1) {
                    checkOverlappingServers(e.getKey(), e.getValue(), "Duplicate resource template uri", errors);
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

    private Set<String> initServerBindings(McpServersBuildTimeConfig config, IndexView index, MethodInfo method) {
        Set<String> ret = new HashSet<String>();
        Optional<Boolean> multiServerBindings = config.supportMultiServerBindings();
        if (multiServerBindings.orElse(true)) {
            // The set of bindings includes all values declared on the feature method and all values defined on the declaring class of the feature
            List<AnnotationInstance> methodAnnotations = method.declaredAnnotationsWithRepeatable(DotNames.MCP_SERVER, index);
            List<AnnotationInstance> classAnnotations = method.declaringClass()
                    .declaredAnnotationsWithRepeatable(DotNames.MCP_SERVER, index);
            for (AnnotationInstance a : methodAnnotations) {
                ret.add(a.value().asString());
            }
            for (AnnotationInstance a : classAnnotations) {
                ret.add(a.value().asString());
            }
            if (ret.size() > 1 && methodAnnotations.size() == 1 && classAnnotations.size() == 1
                    && multiServerBindings.isEmpty()) {
                // In versions 1.10 and lower, the method-level annotation overrode the class-level one;
                // now both are merged - fail the build if the user has not explicitly opted in
                throw new IllegalStateException(
                        String.format(
                                "Feature method %s#%s() is bound to servers %s because @McpServer bindings declared on"
                                        + " the method and the declaring class are now merged."
                                        + " In previous versions, the method-level binding would override the class-level one."
                                        + " Set 'quarkus.mcp.server.support-multi-server-bindings=true' to use the new merge behavior,"
                                        + " or 'quarkus.mcp.server.support-multi-server-bindings=false' to revert to the old behavior.",
                                method.declaringClass().name().withoutPackagePrefix(), method.name(), ret));
            }
        } else {
            // Compatibility mode - only a single binding is allowed
            List<AnnotationInstance> methodAnnotations = method.declaredAnnotationsWithRepeatable(DotNames.MCP_SERVER, index);
            if (methodAnnotations.size() > 1) {
                throw new IllegalStateException(
                        "Only single @McpServer binding is allowed in compatibility mode: " + method.declaringClass() + "#"
                                + method.name()
                                + "()");
            } else if (methodAnnotations.size() == 1) {
                ret.add(methodAnnotations.get(0).value().asString());
            } else {
                // Try the declaring class
                List<AnnotationInstance> classAnnotations = method.declaringClass()
                        .declaredAnnotationsWithRepeatable(DotNames.MCP_SERVER, index);
                if (classAnnotations.size() > 1) {
                    throw new IllegalStateException(
                            "Only single @McpServer binding is allowed in compatibility mode: " + method.declaringClass() + "#"
                                    + method.name()
                                    + "()");
                } else if (classAnnotations.size() == 1) {
                    ret.add(classAnnotations.get(0).value().asString());
                }
            }
        }
        return ret.isEmpty() ? Set.of(McpServer.DEFAULT) : Set.copyOf(ret);
    }

    @BuildStep
    void validateGuardrailsAndIconProviders(BeanArchiveIndexBuildItem beanArchiveIndex,
            List<FeatureMethodBuildItem> featureMethods,
            ValidationPhaseBuildItem validationPhase,
            BuildProducer<ValidationErrorBuildItem> errors,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        for (FeatureMethodBuildItem featureMethod : featureMethods) {
            if (featureMethod.isTool()) {
                for (DotName inputGuardRail : featureMethod.getInputGuardrails()) {
                    validateGuardrail(beanArchiveIndex.getIndex(), featureMethod, inputGuardRail, validationPhase.getContext(),
                            errors, reflectiveClasses);
                }
                for (DotName outputGuardRail : featureMethod.getOutputGuardrails()) {
                    validateGuardrail(beanArchiveIndex.getIndex(), featureMethod, outputGuardRail, validationPhase.getContext(),
                            errors, reflectiveClasses);
                }
            }
            DotName iconsProviderClazzName = featureMethod.getIconsProvider();
            if (iconsProviderClazzName != null) {
                validateBeanOrPublicNoArgsConstructor("IconsProvider", iconsProviderClazzName, featureMethod,
                        beanArchiveIndex.getIndex(),
                        validationPhase.getContext(), errors, reflectiveClasses);
            }
        }
    }

    private void validateBeanOrPublicNoArgsConstructor(String componentType,
            DotName clazzName,
            FeatureMethodBuildItem featureMethod,
            IndexView index,
            ValidationContext validationContext,
            BuildProducer<ValidationErrorBuildItem> errors,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        ClassInfo clazz = index.getClassByName(clazzName);
        if (clazz != null) {
            List<BeanInfo> beans = validationContext.beans().withBeanType(clazzName).collect();
            if (beans.size() > 1) {
                String message = String.format(
                        "There must be exactly one bean that matches %s: \"%s\" declared on: %s; beans: %s",
                        componentType,
                        clazzName,
                        featureMethod.getMethod().declaringClass().name() + "#" + featureMethod.getMethod().name()
                                + "()",
                        beans);
                errors.produce(new ValidationErrorBuildItem(new IllegalStateException(message)));
            } else if (beans.isEmpty()) {
                if (clazz != null) {
                    if (clazz.nestingType() == NestingType.INNER && !Modifier.isStatic(clazz.flags())) {
                        errors.produce(new ValidationErrorBuildItem(new IllegalStateException(
                                componentType + " implementations must not be inner classes: "
                                        + clazz)));
                    } else {
                        MethodInfo noArgsConstructor = clazz.method("<init>");
                        if (noArgsConstructor == null || !Modifier.isPublic(noArgsConstructor.flags())) {
                            errors.produce(new ValidationErrorBuildItem(new IllegalStateException(
                                    componentType
                                            + " implementations must be CDI beans, or declare a public no-args constructor: "
                                            + clazz)));
                        } else {
                            // Also register the component for reflection
                            reflectiveClasses
                                    .produce(ReflectiveClassBuildItem.builder(clazzName.toString())
                                            .constructors().build());
                        }
                    }
                }
            }
        }
    }

    @BuildStep
    void addMetricsSupport(BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            Optional<MetricsCapabilityBuildItem> metricsCapability) {
        if (metricsCapability.map(m -> m.metricsSupported(MetricsFactory.MICROMETER)).orElse(false)) {
            additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(MicrometerMcpMetrics.class));
        }
    }

    @BuildStep
    void addTracingSupport(BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            Optional<OpenTelemetrySdkBuildItem> openTelemetrySdk) {
        if (openTelemetrySdk.filter(OpenTelemetrySdkBuildItem::isTracingBuildTimeEnabled).isPresent()) {
            additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(McpTracingInstrumenter.class));
        }
    }

    private void validateGuardrail(IndexView index, FeatureMethodBuildItem featureMethod, DotName guardrailClazzName,
            ValidationContext validationContext, BuildProducer<ValidationErrorBuildItem> errors,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        validateBeanOrPublicNoArgsConstructor("Guardrail", guardrailClazzName, featureMethod, index,
                validationContext, errors, reflectiveClasses);

        ClassInfo clazz = index.getClassByName(guardrailClazzName);
        if (clazz != null) {
            AnnotationInstance supportedExecutionModels = clazz.declaredAnnotation(DotNames.SUPPORTED_EXEC_MODELS);
            if (supportedExecutionModels != null) {
                for (String supportedModel : supportedExecutionModels.value().asEnumArray()) {
                    if (supportedModel.equals(featureMethod.getExecutionModel().toString())) {
                        return;
                    }
                }
                errors.produce(new ValidationErrorBuildItem(new IllegalStateException(
                        "Guardrail %s does not support the execution model %s of %s#%s()".formatted(guardrailClazzName,
                                featureMethod.getExecutionModel(), featureMethod.getMethod().declaringClass(),
                                featureMethod.getMethod().name()))));
            }
        }
    }

    private void addMetaField(Map<String, String> metadata, AnnotationInstance metaEntry) {
        AnnotationValue prefixValue = metaEntry.value("prefix");
        String name = metaEntry.value("name").asString();
        MetaKey key = new MetaKey(prefixValue != null ? prefixValue.asString() : null, name);
        String value = metaEntry.value("value").asString();
        AnnotationValue typeValue = metaEntry.value("type");
        MetaField.Type type = typeValue != null ? MetaField.Type.valueOf(typeValue.asEnum()) : MetaField.Type.STRING;
        String jsonValue = switch (type) {
            case STRING -> Json.encode(value);
            case BOOLEAN, INT -> value;
            case JSON -> {
                // Try to parse the JSON first
                try {
                    Json.decodeValue(value);
                } catch (DecodeException e) {
                    throw new IllegalArgumentException("Invalid JSON value: " + value);
                }
                yield value;
            }
            default -> throw new IllegalArgumentException("Unexpected value: " + type);
        };
        metadata.put(key.toString(), jsonValue);
    }

    private ClassType outputSchemaFromReturnType(org.jboss.jandex.Type returnType) {
        if (returnType.name().equals(DotNames.UNI)) {
            return ClassType.create(returnType.asParameterizedType().arguments().get(0).name());
        }
        return ClassType.create(returnType.name());
    }

    private Content.Annotations parseResourceAnnotations(AnnotationInstance featureAnnotation) {
        AnnotationValue annotationsValue = featureAnnotation.value("annotations");
        if (annotationsValue != null) {
            AnnotationInstance annotationsAnnotation = annotationsValue.asNested();
            AnnotationValue audienceValue = annotationsAnnotation.value("audience");
            AnnotationValue lastModifiedValue = annotationsAnnotation.value("lastModified");
            AnnotationValue priorityValue = annotationsAnnotation.value("priority");
            return new Content.Annotations(audienceValue != null ? Role.valueOf(audienceValue.asEnum()) : null,
                    lastModifiedValue != null ? lastModifiedValue.asString() : null,
                    priorityValue != null ? priorityValue.asDouble() : null);
        }
        return null;
    }

    private List<Throwable> findWrongAnnotationUsage(IndexView index,
            FeatureAnnotationsBuildItem featureAnnotations,
            BuildProducer<ValidationErrorBuildItem> validationErrors) {
        List<Throwable> wrongUsages = new ArrayList<>();
        for (Map.Entry<DotName, Feature> e : featureAnnotations.annotationToFeature().entrySet()) {
            findWrongMethods(index, e.getKey(), e.getValue(), wrongUsages);
        }
        return wrongUsages;
    }

    private void findWrongMethods(IndexView index, DotName annotationName, Feature feature, List<Throwable> wrongUsages) {
        for (AnnotationInstance annotation : index.getAnnotations(annotationName)) {
            if (annotation.target().kind() == AnnotationTarget.Kind.METHOD) {
                if (Modifier.isStatic(annotation.target().asMethod().flags())) {
                    wrongUsages
                            .add(new IllegalStateException(feature + " method must not be static: "
                                    + methodDesc(annotation.target().asMethod())));
                } else if (annotation.target().asMethod().declaringClass().isInterface()) {
                    wrongUsages
                            .add(new IllegalStateException(feature + " method must not be declared on an interface: "
                                    + methodDesc(annotation.target().asMethod())));
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

    private void checkOverlappingServers(String key, List<FeatureMethodBuildItem> features, String errorPrefix,
            BuildProducer<ValidationErrorBuildItem> errors) {
        for (int i = 0; i < features.size(); i++) {
            for (int j = i + 1; j < features.size(); j++) {
                if (!Collections.disjoint(features.get(i).getServers(), features.get(j).getServers())) {
                    String message = "%s found for %s:\n\t%s"
                            .formatted(errorPrefix, key,
                                    features.stream().map(Object::toString).collect(Collectors.joining("\n\t")));
                    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(message)));
                    return;
                }
            }
        }
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void generateMetadata(McpServerRecorder recorder,
            RecorderContext recorderContext,
            BeanDiscoveryFinishedBuildItem beanDiscovery,
            List<FeatureMethodBuildItem> featureMethods,
            List<DefaultValueConverterBuildItem> defaultValueConverters,
            List<ServerNameBuildItem> serverNames,
            BeanArchiveIndexBuildItem beanArchiveIndex,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        // Note that the generated McpMetadata impl must be considered an application class
        // so that it can see the generated invokers
        ClassOutput classOutput = new GeneratedClassGizmo2Adaptor(generatedClasses, generatedResources, true);
        Gizmo gizmo = Gizmo.create(classOutput)
                .withDebugInfo(false)
                .withParameters(false);

        ManagerUsage managerUsage = analyzeManagerUsage(beanDiscovery);

        String metadataClassName = "io.quarkiverse.mcp.server.runtime.McpMetadata_Impl";
        gizmo.class_(metadataClassName, cc -> {
            cc.implements_(McpMetadata.class);
            cc.defaultConstructor();

            // McpMetadata.isResourceManagerUsed()
            cc.method("isResourceManagerUsed", mc -> {
                mc.returning(boolean.class);
                mc.body(bc -> bc.return_(managerUsage.resources()));
            });

            // McpMetadata.isResourceTemplateManagerUsed()
            cc.method("isResourceTemplateManagerUsed", mc -> {
                mc.returning(boolean.class);
                mc.body(bc -> bc.return_(managerUsage.resourceTemplates()));
            });

            // McpMetadata.isPromptManagerUsed()
            cc.method("isPromptManagerUsed", mc -> {
                mc.returning(boolean.class);
                mc.body(bc -> bc.return_(managerUsage.prompts()));
            });

            // McpMetadata.isToolManagerUsed()
            cc.method("isToolManagerUsed", mc -> {
                mc.returning(boolean.class);
                mc.body(bc -> bc.return_(managerUsage.tools()));
            });

            AtomicInteger counter = new AtomicInteger();

            // McpMetadata.prompts()
            cc.method("prompts", mc -> {
                mc.returning(List.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class));
                    for (FeatureMethodBuildItem prompt : featureMethods.stream()
                            .filter(FeatureMethodBuildItem::isPrompt)
                            .sorted(FeatureMethodBuildItem.NAME_COMPARATOR)
                            .toList()) {
                        // ret.add(meta$123());
                        bc.invokeInterface(MD_Collection.add, ret,
                                bc.invokeVirtual(processFeatureMethod(counter, cc, prompt, DotNames.PROMPT_ARG), cc.this_()));
                    }
                    bc.return_(ret);
                });
            });

            // McpMetadata.promptCompletions()
            cc.method("promptCompletions", mc -> {
                mc.returning(List.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class));
                    for (FeatureMethodBuildItem promptCompletion : featureMethods.stream()
                            .filter(FeatureMethodBuildItem::isPromptComplete)
                            .sorted(FeatureMethodBuildItem.NAME_COMPARATOR)
                            .toList()) {
                        // ret.add(meta$123());
                        bc.invokeInterface(MD_Collection.add, ret,
                                bc.invokeVirtual(processFeatureMethod(counter, cc, promptCompletion, DotNames.COMPLETE_ARG),
                                        cc.this_()));
                    }
                    bc.return_(ret);
                });
            });

            // McpMetadata.tools()
            cc.method("tools", mc -> {
                mc.returning(List.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class));
                    for (FeatureMethodBuildItem tool : featureMethods.stream()
                            .filter(FeatureMethodBuildItem::isTool)
                            .sorted(FeatureMethodBuildItem.NAME_COMPARATOR)
                            .toList()) {
                        // ret.add(meta$123());
                        bc.invokeInterface(MD_Collection.add, ret,
                                bc.invokeVirtual(processFeatureMethod(counter, cc, tool,
                                        tool.getMethod().hasDeclaredAnnotation(DotNames.LANGCHAIN4J_TOOL)
                                                ? DotNames.LANGCHAIN4J_P
                                                : DotNames.TOOL_ARG),
                                        cc.this_()));
                    }
                    bc.return_(ret);
                });
            });

            // McpMetadata.resources()
            cc.method("resources", mc -> {
                mc.returning(List.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class));
                    for (FeatureMethodBuildItem resource : featureMethods.stream()
                            .filter(FeatureMethodBuildItem::isResource)
                            .sorted(FeatureMethodBuildItem.NAME_COMPARATOR)
                            .toList()) {
                        // ret.add(meta$123());
                        bc.invokeInterface(MD_Collection.add, ret,
                                bc.invokeVirtual(processFeatureMethod(counter, cc, resource, null),
                                        cc.this_()));
                    }
                    bc.return_(ret);
                });
            });

            // McpMetadata.resourceTemplates()
            cc.method("resourceTemplates", mc -> {
                mc.returning(List.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class));
                    for (FeatureMethodBuildItem resourceTemplate : featureMethods.stream()
                            .filter(FeatureMethodBuildItem::isResourceTemplate)
                            .sorted(FeatureMethodBuildItem.NAME_COMPARATOR)
                            .toList()) {
                        // ret.add(meta$123());
                        bc.invokeInterface(MD_Collection.add, ret,
                                bc.invokeVirtual(
                                        processFeatureMethod(counter, cc, resourceTemplate, DotNames.RESOURCE_TEMPLATE_ARG),
                                        cc.this_()));
                    }
                    bc.return_(ret);
                });
            });

            // McpMetadata.resourceTemplateCompletions()
            cc.method("resourceTemplateCompletions", mc -> {
                mc.returning(List.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class));
                    for (FeatureMethodBuildItem resourceTemplateCompletion : featureMethods.stream()
                            .filter(FeatureMethodBuildItem::isResourceTemplateComplete)
                            .sorted(FeatureMethodBuildItem.NAME_COMPARATOR)
                            .toList()) {
                        // ret.add(meta$123());
                        bc.invokeInterface(MD_Collection.add, ret,
                                bc.invokeVirtual(
                                        processFeatureMethod(counter, cc, resourceTemplateCompletion, DotNames.COMPLETE_ARG),
                                        cc.this_()));
                    }
                    bc.return_(ret);
                });
            });

            // McpMetadata.notifications()
            cc.method("notifications", mc -> {
                mc.returning(List.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class));
                    for (FeatureMethodBuildItem notification : featureMethods.stream()
                            .filter(FeatureMethodBuildItem::isNotification)
                            .sorted(FeatureMethodBuildItem.NAME_COMPARATOR)
                            .toList()) {
                        // ret.add(meta$123());
                        bc.invokeInterface(MD_Collection.add, ret,
                                bc.invokeVirtual(
                                        processFeatureMethod(counter, cc, notification, null),
                                        cc.this_()));
                    }
                    bc.return_(ret);
                });
            });

            // McpMetadata.defaultValueConverters()
            cc.method("defaultValueConverters", mc -> {
                mc.returning(Map.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(HashMap.class));
                    List<DefaultValueConverterBuildItem> sortedConverters = defaultValueConverters.stream()
                            .sorted(Comparator.comparing(DefaultValueConverterBuildItem::getClassName))
                            .toList();
                    for (DefaultValueConverterBuildItem converter : sortedConverters) {
                        LocalVar converterType = RuntimeTypeCreator.of(bc).create(converter.getArgumentType());
                        bc.withMap(ret).put(converterType, bc.new_(ClassDesc.of(converter.getClassName())));
                    }
                    bc.return_(ret);
                });

            });

            // McpMetadata.serverNames()
            cc.method("serverNames", mc -> {
                mc.returning(Set.class);
                mc.body(bc -> {
                    bc.return_(bc.setOf(serverNames.stream()
                            .map(ServerNameBuildItem::getName)
                            .distinct()
                            .sorted()
                            .map(Const::of)
                            .toList()));
                });
            });

            // McpMetadata.toolArgumentHolders()
            // Generated tool argument holders are used to workaround a limitation of com.github.victools.jsonschema.generator.SchemaGenerator
            // that can only consume java.lang.reflect.Type
            cc.method("toolArgumentHolders", mc -> {
                mc.returning(Map.class);
                mc.body(bc -> {
                    LocalVar ret = bc.localVar("ret", bc.new_(HashMap.class));
                    for (FeatureMethodBuildItem tool : featureMethods.stream()
                            .filter(FeatureMethodBuildItem::isTool)
                            .sorted(FeatureMethodBuildItem.NAME_COMPARATOR)
                            .toList()) {
                        String generatedClassName = generateToolArgsHolder(gizmo, tool, classOutput, reflectiveClasses,
                                beanArchiveIndex.getIndex());
                        if (generatedClassName != null) {
                            bc.withMap(ret).put(Const.of(tool.getName()), Const.of(ClassDesc.of(generatedClassName)));
                        }
                    }
                    bc.return_(ret);
                });
            });

        });

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(McpMetadata.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .runtimeValue(recorderContext.newInstance(metadataClassName))
                .done());
    }

    private ManagerUsage analyzeManagerUsage(BeanDiscoveryFinishedBuildItem beanDiscovery) {
        boolean isResourceManagerUsed = false;
        boolean isResourceTemplateManagerUsed = false;
        boolean isPromptManagerUsed = false;
        boolean isToolManagerUsed = false;
        for (InjectionPointInfo ip : beanDiscovery.getInjectionPoints()) {
            DotName requiredType = ip.getRequiredType().name();
            if (requiredType.equals(DotNames.RESOURCE_MANAGER)) {
                isResourceManagerUsed = true;
            } else if (requiredType.equals(DotNames.RESOURCE_TEMPLATE_MANAGER)) {
                isResourceTemplateManagerUsed = true;
            } else if (requiredType.equals(DotNames.PROMPT_MANAGER)) {
                isPromptManagerUsed = true;
            } else if (requiredType.equals(DotNames.TOOL_MANAGER)) {
                isToolManagerUsed = true;
            }
        }
        return new ManagerUsage(isResourceManagerUsed, isResourceTemplateManagerUsed, isPromptManagerUsed, isToolManagerUsed);
    }

    private record ManagerUsage(boolean resources, boolean resourceTemplates, boolean prompts, boolean tools) {
    }

    private String generateToolArgsHolder(Gizmo gizmo, FeatureMethodBuildItem tool,
            ClassOutput classOutput, BuildProducer<ReflectiveClassBuildItem> reflectiveClasses, IndexView index) {
        // Generate a holder for each tool with at least one argument that is:
        // - serialized
        // - annotated with any other annotation than @ToolArg and @P
        boolean generateHolder = false;
        List<MethodParameterInfo> serializedArguments = new ArrayList<>();
        for (MethodParameterInfo param : tool.getMethod().parameters()) {
            if (providerFrom(param.type()) == Provider.PARAMS) {
                serializedArguments.add(param);
                List<AnnotationInstance> annotations = param.declaredAnnotations();
                if (!annotations.isEmpty()
                        && annotations.stream().anyMatch(a -> !a.name().equals(DotNames.TOOL_ARG)
                                && !a.name().equals(DotNames.LANGCHAIN4J_P))) {
                    generateHolder = true;
                }
            }
        }
        if (!generateHolder) {
            return null;
        }
        // org.example.MyTool_foo
        String className = tool.getMethod().declaringClass().name().toString() + "_" + tool.getName();
        LOG.debugf("Generate tool arguments holder: %s", className);
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(className).fields().build());
        gizmo.class_(className, cc -> {
            cc.defaultConstructor();
            for (MethodParameterInfo param : serializedArguments) {
                cc.field(param.name(), fc -> {
                    fc.public_();
                    fc.setType(Jandex2Gizmo.genericTypeOf(param.type()));
                    for (AnnotationInstance annotation : param.declaredAnnotations()) {
                        Jandex2Gizmo.addAnnotation(fc, annotation, index);
                    }
                });
            }
        });
        return className;
    }

    @BuildStep
    void registerForReflection(List<FeatureMethodBuildItem> featureMethods,
            List<DefaultValueConverterBuildItem> defaultValueConverters,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchies) {
        // JsonObject.encode() may use Jackson under the hood which requires reflection
        for (FeatureMethodBuildItem m : featureMethods) {
            org.jboss.jandex.Type returnType = m.getMethod().returnType();
            if (DotNames.UNI.equals(returnType.name()) && returnType.kind() == Kind.PARAMETERIZED_TYPE) {
                returnType = returnType.asParameterizedType().arguments().get(0);
            }
            if (DotNames.LIST.equals(returnType.name()) && returnType.kind() == Kind.PARAMETERIZED_TYPE) {
                returnType = returnType.asParameterizedType().arguments().get(0);
            }
            if (isReturnTypeReflectionNeeded(returnType.name())) {
                reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(m.getMethod().returnType()).build());
            }
            for (org.jboss.jandex.Type paramType : m.getMethod().parameterTypes()) {
                if (isParamTypeReflectionNeeded(paramType)) {
                    reflectiveHierarchies.produce(ReflectiveHierarchyBuildItem.builder(paramType).build());
                }
            }
        }

        // Register all default value converters
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(defaultValueConverters.stream()
                .map(DefaultValueConverterBuildItem::getClassName).toList().toArray(String[]::new))
                .constructors()
                .build());

        // Various classes serialized by Jackson
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                Content.class,
                TextContent.class,
                ImageContent.class,
                EmbeddedResource.class,
                AudioContent.class,
                PromptResponse.class,
                PromptMessage.class,
                ToolResponse.class,
                FeatureMethodInfo.class,
                FeatureArgument.class,
                ResourceResponse.class,
                ResourceContents.class,
                TextResourceContents.class,
                BlobResourceContents.class,
                Role.class,
                SamplingMessage.class,
                ModelPreferences.class,
                ModelHint.class,
                IncludeContext.class,
                SamplingRequestImpl.class,
                Icon.class)
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
        for (ClassInfo converter : combinedIndex.getIndex().getAllKnownImplementations(DefaultValueConverter.class)) {
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

    private static final Set<DotName> ARG_ANNOTATIONS = Set.of(DotNames.TOOL_ARG, DotNames.PROMPT_ARG, DotNames.COMPLETE_ARG,
            DotNames.RESOURCE_TEMPLATE_ARG);

    private void validateFeatureMethod(MethodInfo method, Feature feature, AnnotationInstance featureAnnotation,
            List<DefaultValueConverterBuildItem> defaultValueConverters, IndexView index) {
        if (Modifier.isStatic(method.flags())) {
            throw new IllegalStateException(feature + " method must not be static: " + methodDesc(method));
        }
        if (Modifier.isPrivate(method.flags())) {
            throw new IllegalStateException(feature + " method must not be private: " + methodDesc(method));
        }
        if (method.returnType().kind() == Kind.VOID && feature != NOTIFICATION) {
            throw new IllegalStateException(feature + " method may not return void: " + methodDesc(method));
        }
        for (MethodParameterInfo param : method.parameters()) {
            DotName argAnnotation = switch (feature) {
                case TOOL -> DotNames.TOOL_ARG;
                case PROMPT -> DotNames.PROMPT_ARG;
                case RESOURCE_TEMPLATE -> DotNames.RESOURCE_TEMPLATE_ARG;
                case PROMPT_COMPLETE, RESOURCE_TEMPLATE_COMPLETE -> DotNames.COMPLETE_ARG;
                default -> null;
            };
            Set<DotName> invalidAnnotations = new HashSet<>(ARG_ANNOTATIONS);
            if (argAnnotation != null) {
                invalidAnnotations.remove(argAnnotation);
            }
            for (DotName invalidAnnotation : invalidAnnotations) {
                if (param.hasDeclaredAnnotation(invalidAnnotation)) {
                    throw new IllegalStateException("Parameter of a %s method may not be annotated with @%s: %s"
                            .formatted(feature, invalidAnnotation.withoutPackagePrefix(), methodDesc(method)));
                }
            }
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

        List<MethodParameterInfo> parameters = parameters(method, PROMPT);
        for (MethodParameterInfo param : parameters) {
            if (!param.type().name().equals(DotNames.STRING)) {
                throw new IllegalStateException(
                        "Prompt method must only consume String parameters: " + methodDesc(method));
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
            throw new IllegalStateException("Unsupported Prompt complete method return type: " + methodDesc(method));
        }

        List<MethodParameterInfo> parameters = parameters(method, PROMPT_COMPLETE);
        if (parameters.size() != 1 || !parameters.get(0).type().name().equals(DotNames.STRING)) {
            throw new IllegalStateException(
                    "Prompt complete must consume exactly one String argument: " + methodDesc(method));
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
            throw new IllegalStateException(
                    "Unsupported Resource template complete method return type: " + methodDesc(method));
        }

        List<MethodParameterInfo> parameters = parameters(method, RESOURCE_TEMPLATE_COMPLETE);
        if (parameters.size() != 1 || !parameters.get(0).type().name().equals(DotNames.STRING)) {
            throw new IllegalStateException(
                    "Resource template complete must consume exactly one String argument: " + methodDesc(method));
        }
    }

    private static final Set<org.jboss.jandex.Type> TOOL_TYPES = Set.of(ClassType.create(DotNames.TOOL_RESPONSE),
            ClassType.create(DotNames.CONTENT), ClassType.create(DotNames.TEXT_CONTENT),
            ClassType.create(DotNames.IMAGE_CONTENT), ClassType.create(DotNames.EMBEDDED_RESOURCE),
            ClassType.create(DotNames.AUDIO_CONTENT), ClassType.create(DotNames.RESOURCE_LINK),
            ClassType.create(DotNames.STRING));

    private void validateToolMethod(MethodInfo method, List<DefaultValueConverterBuildItem> defaultValueConverters,
            IndexView index) {
        parameters(method, TOOL);
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
                    TypeEquivalenceKey argEquivalenceKey = TypeEquivalenceKey.of(argType);
                    if (defaultValueConverters.stream()
                            .noneMatch(c -> TypeEquivalenceKey.of(c.getArgumentType()).equals(argEquivalenceKey))) {
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

        List<MethodParameterInfo> parameters = parameters(method, RESOURCE);
        if (!parameters.isEmpty()) {
            throw new IllegalStateException(
                    "Resource method may only accept built-in parameter types" + methodDesc(method));
        }
    }

    private void validateResourceTemplateMethod(MethodInfo method, AnnotationInstance featureAnnotation) {
        // No need to validate return type

        AnnotationValue uriTemplateValue = featureAnnotation.value("uriTemplate");
        if (uriTemplateValue == null) {
            throw new IllegalStateException("URI template not found");
        }
        VariableMatcher variableMatcher = ResourceTemplateManagerImpl.createMatcherFromUriTemplate(uriTemplateValue.asString());

        List<MethodParameterInfo> parameters = parameters(method, RESOURCE_TEMPLATE);
        for (MethodParameterInfo param : parameters) {
            if (!param.type().name().equals(DotNames.STRING)) {
                throw new IllegalStateException(
                        "Resource template method must only consume String parameters: " + methodDesc(method));
            }
            if (!variableMatcher.variables().contains(param.name())) {
                throw new IllegalStateException(
                        "Parameter [" + param.name() + "] does not match an URI template variable: "
                                + methodDesc(method));
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
        List<MethodParameterInfo> params = parameters(method, NOTIFICATION);
        if (!params.isEmpty()) {
            throw new IllegalStateException(
                    "Notification method %s may not consume the following parameter types: %s".formatted(methodDesc(method),
                            params.stream().map(MethodParameterInfo::type).toList()));
        }
    }

    private List<MethodParameterInfo> parameters(MethodInfo method, Feature feature) {
        List<MethodParameterInfo> ret = new ArrayList<>();
        for (MethodParameterInfo param : method.parameters()) {
            Provider provider = providerFrom(param.type());
            if (!provider.isValidFor(feature)) {
                throw new IllegalStateException("%s feature method %s may not accept parameter of type %s".formatted(feature,
                        methodDesc(method), param.type()));
            }
            if (provider == Provider.PARAMS) {
                ret.add(param);
            }
        }
        return ret;
    }

    private MethodDesc processFeatureMethod(AtomicInteger counter, ClassCreator cc,
            FeatureMethodBuildItem featureMethod, DotName argAnnotationName) {
        return cc.method("meta$" + counter.incrementAndGet(), mc -> {
            mc.returning(FeatureMetadata.class);

            mc.body(bc -> {
                LocalVar args = bc.localVar("args", List.class, bc.new_(ArrayList.class));

                for (MethodParameterInfo param : featureMethod.getMethod().parameters()) {
                    String name = param.name();
                    String title = null;
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
                            AnnotationValue titleValue = argAnnotation.value("title");
                            if (titleValue != null) {
                                title = titleValue.asString();
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

                    // Fail the build if argument name is not available
                    if (name == null) {
                        String explicitAnnotation = argAnnotationName != null
                                ? " or define the name explicitly with @" + argAnnotationName.withoutPackagePrefix()
                                : "";
                        throw new IllegalStateException(
                                "Missing argument name - compile the class %s with -parameters%s"
                                        .formatted(featureMethod.getMethod().declaringClass().name(), explicitAnnotation));
                    }

                    LocalVar type = RuntimeTypeCreator.of(bc).create(param.type());
                    // new FeatureArgument(String name, String title, String description, boolean required, Type type, String defaultValue, Provider provider)
                    LocalVar arg = bc.localVar("arg", bc.new_(FeatureArgument.class,
                            Const.of(name),
                            title != null ? Const.of(title) : Const.ofNull(String.class),
                            Const.of(description),
                            Const.of(required),
                            type,
                            defaultValue != null ? Const.of(defaultValue) : Const.ofNull(String.class),
                            Const.of(providerFrom(param.type()))));
                    bc.withList(args).add(arg);
                }

                LocalVar toolAnnotations;
                if (featureMethod.isTool() && featureMethod.getToolAnnotations() != null) {
                    ToolAnnotations annotations = featureMethod.getToolAnnotations();
                    // new ToolAnnotations(String title, boolean readOnlyHint, boolean destructiveHint, boolean idempotentHint, boolean openWorldHint)
                    toolAnnotations = bc.localVar("toolAnnotations", bc.new_(ToolManager.ToolAnnotations.class,
                            annotations.title() != null ? Const.of(annotations.title()) : Const.ofNull(String.class),
                            Const.of(annotations.readOnlyHint()),
                            Const.of(annotations.destructiveHint()),
                            Const.of(annotations.idempotentHint()),
                            Const.of(annotations.openWorldHint())));
                } else {
                    toolAnnotations = bc.localVar("toolAnnotations", Const.ofNull(ToolManager.ToolAnnotations.class));
                }

                LocalVar resourceAnnotations;
                if ((featureMethod.isResource() || featureMethod.isResourceTemplate())
                        && featureMethod.getResourceAnnotations() != null) {
                    // new Annotations(Role audience, String lastModified, Double priority)
                    Content.Annotations annotations = featureMethod.getResourceAnnotations();
                    resourceAnnotations = bc.localVar("resourceAnnotations", bc.new_(
                            ConstructorDesc.of(Content.Annotations.class, Role.class, String.class, Double.class),
                            annotations.audience() == null ? Const.ofNull(Role.class) : Const.of(annotations.audience()),
                            annotations.lastModified() == null ? Const.ofNull(String.class)
                                    : Const.of(annotations.lastModified()),
                            annotations.priority() == null ? Const.ofNull(Double.class) : Const.of(annotations.priority())));
                } else {
                    resourceAnnotations = bc.localVar("resourceAnnotations", Const.ofNull(Content.Annotations.class));
                }

                LocalVar meta;
                Map<String, String> metaEntries = featureMethod.getMetadata();
                if (metaEntries.isEmpty()) {
                    meta = bc.localVar("meta", bc.mapOf());
                } else {
                    meta = bc.localVar("meta", bc.invokeStatic(MD_Map.ofEntries, bc.newArray(Entry.class, metaEntries.entrySet()
                            .stream()
                            .sorted(Comparator.comparing(Entry::getKey))
                            .map(e -> bc.mapEntry(Const.of(e.getKey()), Const.of(e.getValue())))
                            .toList())));
                }

                LocalVar inputGuardrails;
                if (featureMethod.getInputGuardrails().isEmpty()) {
                    inputGuardrails = bc.localVar("inputGuardrails", bc.listOf());
                } else {
                    inputGuardrails = bc.localVar("inputGuardrails", bc.listOf(
                            featureMethod.getInputGuardrails().stream()
                                    .map(name -> Const.of(Jandex2Gizmo.classDescOf(name)))
                                    .toList()));
                }
                LocalVar outputGuardrails;
                if (featureMethod.getOutputGuardrails().isEmpty()) {
                    outputGuardrails = bc.localVar("outputGuardrails", bc.listOf());
                } else {
                    outputGuardrails = bc.localVar("outputGuardrails", bc.listOf(
                            featureMethod.getOutputGuardrails().stream()
                                    .map(name -> Const.of(Jandex2Gizmo.classDescOf(name)))
                                    .toList()));
                }

                LocalVar iconsProvider;
                if (featureMethod.getIconsProvider() != null) {
                    iconsProvider = bc.localVar("iconsProvider",
                            Const.of(ClassDesc.of(featureMethod.getIconsProvider().toString())));
                } else {
                    iconsProvider = bc.localVar("iconsProvider", Const.ofNull(Class.class));
                }

                LocalVar serverNames = bc.localVar("serverNames", bc.setOf(featureMethod.getServers()
                        .stream()
                        .sorted()
                        .map(Const::of).toList()));

                // new FeatureMethodInfo(...)
                LocalVar info = bc.localVar("info", bc.new_(FeatureMethodInfo.class,
                        Const.of(featureMethod.getName()),
                        featureMethod.getTitle() != null ? Const.of(featureMethod.getTitle()) : Const.ofNull(String.class),
                        Const.of(featureMethod.getDescription()),
                        featureMethod.getUri() == null ? Const.ofNull(String.class) : Const.of(featureMethod.getUri()),
                        featureMethod.getMimeType() == null ? Const.ofNull(String.class)
                                : Const.of(featureMethod.getMimeType()),
                        Const.of(featureMethod.getSize()),
                        args,
                        Const.of(featureMethod.getMethod().declaringClass().name().toString()),
                        toolAnnotations,
                        resourceAnnotations,
                        serverNames,
                        featureMethod.getOutputSchemaFrom() == null ? Const.ofNull(Class.class)
                                : Const.of(Jandex2Gizmo.classDescOf(featureMethod.getOutputSchemaFrom().name())),
                        featureMethod.getOutputSchemaGenerator() == null ? Const.ofNull(Class.class)
                                : Const.of(Jandex2Gizmo.classDescOf(featureMethod.getOutputSchemaGenerator().name())),
                        featureMethod.getInputSchemaGenerator() == null ? Const.ofNull(Class.class)
                                : Const.of(Jandex2Gizmo.classDescOf(featureMethod.getInputSchemaGenerator().name())),
                        meta,
                        inputGuardrails,
                        outputGuardrails,
                        iconsProvider));

                LocalVar invoker = bc.localVar("invoker", Invoker.class, bc.new_(featureMethod.getInvoker().getClassDesc()));
                LocalVar executionModel = bc.localVar("executionModel", Const.of(featureMethod.getExecutionModel()));
                Var resultMapper = getMapper(bc, featureMethod);

                // new FeatureMetadata<>(Feature feature, FeatureMethodInfo info, Invoker<Object, Object> invoker, ExecutionModel executionModel, Function<Result<Object>, Uni<M>> resultMapper)
                bc.return_(bc.new_(FeatureMetadata.class,
                        Const.of(featureMethod.getFeature()),
                        info,
                        invoker,
                        executionModel,
                        bc.cast(resultMapper, Function.class)));
            });

        });
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
        } else if (type.name().equals(DotNames.CANCELLATION)) {
            return FeatureArgument.Provider.CANCELLATION;
        } else if (type.name().equals(DotNames.RAW_MESSAGE)) {
            return FeatureArgument.Provider.RAW_MESSAGE;
        } else if (type.name().equals(DotNames.COMPLETE_CONTEXT)) {
            return FeatureArgument.Provider.COMPLETE_CONTEXT;
        } else if (type.name().equals(DotNames.META)) {
            return FeatureArgument.Provider.META;
        } else if (type.name().equals(DotNames.ELICITATION)) {
            return FeatureArgument.Provider.ELICITATION;
        } else {
            return FeatureArgument.Provider.PARAMS;
        }
    }

    private Var getMapper(BlockCreator bc, FeatureMethodBuildItem featureMethod) {
        // Returns a function that converts the returned object to Uni<RESPONSE>
        // where the RESPONSE is one of ToolResponse, PromptResponse, ResourceResponse, CompleteResponse
        // IMPL NOTE: at this point the method return type is already validated
        org.jboss.jandex.Type returnType = featureMethod.getMethod().returnType();
        return switch (featureMethod.getFeature()) {
            case PROMPT -> promptResultMapper(featureMethod, bc, returnType);
            case PROMPT_COMPLETE -> readResultMapper(bc,
                    createMapperClassSimpleName(PROMPT_COMPLETE, returnType, DotNames.COMPLETE_RESPONSE, c -> "String"));
            case TOOL -> toolResultMapper(featureMethod, bc, returnType);
            case RESOURCE, RESOURCE_TEMPLATE -> resourceResultMapper(featureMethod, bc, returnType);
            case RESOURCE_TEMPLATE_COMPLETE -> readResultMapper(bc,
                    createMapperClassSimpleName(RESOURCE_TEMPLATE_COMPLETE, returnType, DotNames.COMPLETE_RESPONSE,
                            c -> "String"));
            case NOTIFICATION -> readResultMapper(bc, returnType.kind() == Kind.VOID ? "ToUni" : "Identity");
            default -> throw new IllegalArgumentException("Unsupported feature: " + featureMethod.getFeature());
        };
    }

    Var resourceResultMapper(FeatureMethodBuildItem featureMethod, BlockCreator bc,
            org.jboss.jandex.Type returnType) {
        if (useEncoder(returnType, RESOURCE_TYPES)) {
            return encoderResultMapper(featureMethod, bc, returnType, ResourceContentsEncoderResultMapper.class);
        } else {
            return readResultMapper(bc,
                    createMapperClassSimpleName(RESOURCE, returnType, DotNames.RESOURCE_RESPONSE, c -> "Content"));
        }
    }

    Var encoderResultMapper(FeatureMethodBuildItem featureMethod, BlockCreator bc,
            org.jboss.jandex.Type returnType,
            Class<?> mapperClazz) {
        // Arc.container().instance(mapperClazz).get();
        Expr container = bc.invokeStatic(MethodDesc.of(Arc.class, "container", ArcContainer.class));
        Expr instance = bc.invokeInterface(MethodDesc.of(ArcContainer.class, "instance",
                InstanceHandle.class, Class.class, Annotation[].class), container,
                Const.of(mapperClazz), bc.newArray(Annotation.class));
        LocalVar mapper = bc.localVar("mapper", bc.invokeInterface(
                MethodDesc.of(InstanceHandle.class, "get", Object.class),
                instance));
        if (DotNames.UNI.equals(returnType.name())) {
            if (useUniList(featureMethod, returnType.asParameterizedType().arguments().get(0))) {
                bc.set(mapper, bc.invokeVirtual(
                        MethodDesc.of(mapperClazz, "uniList", Function.class), mapper));
            } else {
                bc.set(mapper, bc.invokeVirtual(
                        MethodDesc.of(mapperClazz, "uni", Function.class), mapper));
            }
        } else if (useUniList(featureMethod, returnType)) {
            bc.set(mapper, bc.invokeVirtual(
                    MethodDesc.of(mapperClazz, "list", Function.class), mapper));
        }
        return mapper;
    }

    private boolean useUniList(FeatureMethodBuildItem featureMethod, org.jboss.jandex.Type type) {
        return featureMethod.getFeature() != PROMPT
                && !featureMethod.isStructuredContent()
                && DotNames.LIST.equals(type.name());
    }

    Var toolResultMapper(FeatureMethodBuildItem featureMethod, BlockCreator bc,
            org.jboss.jandex.Type returnType) {
        if (useEncoder(returnType, TOOL_TYPES)) {
            if (featureMethod.isStructuredContent()) {
                return encoderResultMapper(featureMethod, bc, returnType, ToolStructuredContentResultMapper.class);
            } else {
                return encoderResultMapper(featureMethod, bc, returnType, ToolEncoderResultMapper.class);
            }
        } else {
            return readResultMapper(bc, createMapperClassSimpleName(TOOL, returnType, DotNames.TOOL_RESPONSE, c -> {
                return isContent(c) ? "Content" : "String";
            }));
        }
    }

    Var promptResultMapper(FeatureMethodBuildItem featureMethod, BlockCreator bc,
            org.jboss.jandex.Type returnType) {
        if (useEncoder(returnType, PROMPT_TYPES)) {
            return encoderResultMapper(featureMethod, bc, returnType, PromptEncoderResultMapper.class);
        } else {
            return readResultMapper(bc,
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
        return DotNames.CONTENT.equals(typeName)
                || DotNames.TEXT_CONTENT.equals(typeName)
                || DotNames.IMAGE_CONTENT.equals(typeName)
                || DotNames.EMBEDDED_RESOURCE.equals(typeName)
                || DotNames.RESOURCE_LINK.equals(typeName)
                || DotNames.AUDIO_CONTENT.equals(typeName);
    }

    private Var readResultMapper(BlockCreator bc, String mapperClassSimpleName) {
        ClassDesc mapperClassDesc = ClassDesc.of(ResultMappers.class.getName() + "$" + mapperClassSimpleName);
        return Expr.staticField(FieldDesc.of(mapperClassDesc, "INSTANCE", mapperClassDesc));
    }

    private static ExecutionModel executionModel(MethodInfo method, TransformedAnnotationsBuildItem transformedAnnotations) {
        if (KotlinUtils.isKotlinSuspendMethod(method)
                && (transformedAnnotations.hasAnnotation(method, DotNames.RUN_ON_VIRTUAL_THREAD)
                        || transformedAnnotations.hasAnnotation(method.declaringClass(),
                                DotNames.RUN_ON_VIRTUAL_THREAD)
                        || transformedAnnotations.hasAnnotation(method, DotNames.BLOCKING)
                        || transformedAnnotations.hasAnnotation(method, DotNames.NON_BLOCKING))) {
            throw new IllegalStateException("Kotlin `suspend` functions in MCP components may not be "
                    + "annotated @Blocking, @NonBlocking or @RunOnVirtualThread: " + methodDesc(method));
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
                        "Unsupported return type:" + methodDesc(method));
        }
    }

    private static String methodDesc(MethodInfo method) {
        StringBuilder builder = new StringBuilder()
                .append(method.declaringClass().name().withoutPackagePrefix())
                .append("#")
                .append(method.name())
                .append('(');
        for (Iterator<org.jboss.jandex.Type> it = method.parameterTypes().iterator(); it.hasNext();) {
            builder.append(it.next());
            if (it.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append(')');
        return builder.toString();
    }

    private static boolean isReturnTypeReflectionNeeded(DotName returnTypeName) {
        return !isNameInTypes(returnTypeName, TOOL_TYPES)
                && !isNameInTypes(returnTypeName, PROMPT_TYPES)
                && !isNameInTypes(returnTypeName, RESOURCE_TYPES)
                && !isNameInTypes(returnTypeName, COMPLETE_TYPES);
    }

    private static boolean isNameInTypes(DotName name, Set<org.jboss.jandex.Type> types) {
        for (org.jboss.jandex.Type type : types) {
            if (name.equals(type.name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isParamTypeReflectionNeeded(org.jboss.jandex.Type paramType) {
        return paramType.kind() != Kind.PRIMITIVE
                && !paramType.name().equals(DotNames.STRING)
                && !paramType.name().equals(DotNames.MCP_CONNECTION)
                && !paramType.name().equals(DotNames.MCP_LOG)
                && !paramType.name().equals(DotNames.REQUEST_ID)
                && !paramType.name().equals(DotNames.REQUEST_URI)
                && !paramType.name().equals(DotNames.PROGRESS)
                && !paramType.name().equals(DotNames.ROOTS)
                && !paramType.name().equals(DotNames.SAMPLING)
                && !paramType.name().equals(DotNames.CANCELLATION)
                && !paramType.name().equals(DotNames.COMPLETE_CONTEXT)
                && !paramType.name().equals(DotNames.RAW_MESSAGE)
                && !paramType.name().equals(DotNames.META)
                && !paramType.name().equals(DotNames.ELICITATION);
    }

}
