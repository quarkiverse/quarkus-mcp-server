package io.quarkiverse.mcp.server.deployment;

import static io.quarkiverse.mcp.server.runtime.Feature.NOTIFICATION;
import static io.quarkiverse.mcp.server.runtime.Feature.PROMPT;
import static io.quarkiverse.mcp.server.runtime.Feature.PROMPT_COMPLETE;
import static io.quarkiverse.mcp.server.runtime.Feature.RESOURCE;
import static io.quarkiverse.mcp.server.runtime.Feature.RESOURCE_TEMPLATE;
import static io.quarkiverse.mcp.server.runtime.Feature.RESOURCE_TEMPLATE_COMPLETE;
import static io.quarkiverse.mcp.server.runtime.Feature.TOOL;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.EquivalenceKey.TypeEquivalenceKey;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type.Kind;

import io.quarkiverse.mcp.server.ExecutionModel;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl.VariableMatcher;
import io.quarkiverse.mcp.server.runtime.config.McpServersBuildTimeConfig;
import io.quarkus.arc.deployment.TransformedAnnotationsBuildItem;
import io.quarkus.arc.processor.KotlinUtils;
import io.quarkus.deployment.annotations.BuildProducer;

final class FeatureMethods {

    private FeatureMethods() {
    }

    static final String DEFAULT_VALUE = "defaultValue";

    static final Set<DotName> ARG_ANNOTATIONS = Set.of(DotNames.TOOL_ARG, DotNames.PROMPT_ARG,
            DotNames.COMPLETE_ARG,
            DotNames.RESOURCE_TEMPLATE_ARG,
            DotNames.MCPJAVA_TOOL_ARG, DotNames.MCPJAVA_PROMPT_ARG, DotNames.MCPJAVA_COMPLETE_ARG,
            DotNames.MCPJAVA_RESOURCE_TEMPLATE_ARG);

    static final Set<org.jboss.jandex.Type> PROMPT_TYPES = Set.of(ClassType.create(DotNames.PROMPT_RESPONSE),
            ClassType.create(DotNames.PROMPT_MESSAGE), ClassType.create(DotNames.STRING));

    static final Set<org.jboss.jandex.Type> COMPLETE_TYPES = Set.of(
            ClassType.create(DotNames.COMPLETE_RESPONSE),
            ClassType.create(DotNames.STRING),
            ClassType.create(DotNames.MCPJAVA_COMPLETION_RESULT));

    static final Set<org.jboss.jandex.Type> TOOL_TYPES = Set.of(ClassType.create(DotNames.TOOL_RESPONSE),
            ClassType.create(DotNames.CONTENT), ClassType.create(DotNames.TEXT_CONTENT),
            ClassType.create(DotNames.IMAGE_CONTENT), ClassType.create(DotNames.EMBEDDED_RESOURCE),
            ClassType.create(DotNames.AUDIO_CONTENT), ClassType.create(DotNames.RESOURCE_LINK),
            ClassType.create(DotNames.STRING));

    static final Set<org.jboss.jandex.Type> RESOURCE_TYPES = Set.of(ClassType.create(DotNames.RESOURCE_RESPONSE),
            ClassType.create(DotNames.RESOURCE_CONTENTS), ClassType.create(DotNames.TEXT_RESOURCE_CONTENTS),
            ClassType.create(DotNames.BLOB_RESOURCE_CONTENTS));

    static void validateFeatureMethod(MethodInfo method, Feature feature, AnnotationInstance featureAnnotation,
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
            Set<DotName> validArgAnnotations = switch (feature) {
                case TOOL -> Set.of(DotNames.TOOL_ARG, DotNames.MCPJAVA_TOOL_ARG);
                case PROMPT -> Set.of(DotNames.PROMPT_ARG, DotNames.MCPJAVA_PROMPT_ARG);
                case RESOURCE_TEMPLATE ->
                    Set.of(DotNames.RESOURCE_TEMPLATE_ARG, DotNames.MCPJAVA_RESOURCE_TEMPLATE_ARG);
                case PROMPT_COMPLETE, RESOURCE_TEMPLATE_COMPLETE ->
                    Set.of(DotNames.COMPLETE_ARG, DotNames.MCPJAVA_COMPLETE_ARG);
                default -> Set.of();
            };
            Set<DotName> invalidAnnotations = new HashSet<>(ARG_ANNOTATIONS);
            invalidAnnotations.removeAll(validArgAnnotations);
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

    private static void validatePromptMethod(MethodInfo method) {
        List<MethodParameterInfo> parameters = parameters(method, PROMPT);
        for (MethodParameterInfo param : parameters) {
            if (!param.type().name().equals(DotNames.STRING)) {
                throw new IllegalStateException(
                        "Prompt method must only consume String parameters: " + methodDesc(method));
            }
        }
    }

    private static void validatePromptCompleteMethod(MethodInfo method) {
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

    private static void validateResourceTemplateCompleteMethod(MethodInfo method) {
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

    private static void validateToolMethod(MethodInfo method, List<DefaultValueConverterBuildItem> defaultValueConverters,
            IndexView index) {
        parameters(method, TOOL);
        for (MethodParameterInfo p : method.parameters()) {
            AnnotationInstance toolArg = p.annotation(DotNames.TOOL_ARG);
            if (toolArg == null) {
                toolArg = p.annotation(DotNames.MCPJAVA_TOOL_ARG);
            }
            if (toolArg != null) {
                AnnotationValue defaultValueValue = toolArg.value(DEFAULT_VALUE);
                if (defaultValueValue != null) {
                    if (p.type().name().equals(DotNames.STRING)) {
                        continue;
                    }
                    if (p.type().kind() == Kind.CLASS) {
                        ClassInfo pclazz = index.getClassByName(p.type().name());
                        if (pclazz != null && pclazz.isEnum()) {
                            continue;
                        }
                    }
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
                                "No matching default value converter found for argument type [" + p.type()
                                        + "] declared on: "
                                        + p);
                    }
                }
            }
        }
    }

    private static void validateResourceMethod(MethodInfo method) {
        List<MethodParameterInfo> parameters = parameters(method, RESOURCE);
        if (!parameters.isEmpty()) {
            throw new IllegalStateException(
                    "Resource method may only accept built-in parameter types" + methodDesc(method));
        }
    }

    private static void validateResourceTemplateMethod(MethodInfo method, AnnotationInstance featureAnnotation) {
        AnnotationValue uriTemplateValue = featureAnnotation.value("uriTemplate");
        if (uriTemplateValue == null) {
            throw new IllegalStateException("URI template not found");
        }
        VariableMatcher variableMatcher = io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl
                .createMatcherFromUriTemplate(uriTemplateValue.asString());

        List<MethodParameterInfo> parameters = parameters(method, RESOURCE_TEMPLATE);
        for (MethodParameterInfo param : parameters) {
            String paramName = param.name();
            AnnotationInstance resourceTemplateArg = param.annotation(DotNames.RESOURCE_TEMPLATE_ARG);
            if (resourceTemplateArg != null) {
                AnnotationValue nameValue = resourceTemplateArg.value("name");
                if (nameValue != null && !ResourceTemplateArg.ELEMENT_NAME.equals(nameValue.asString())) {
                    paramName = nameValue.asString();
                }
            }
            if (!param.type().name().equals(DotNames.STRING)) {
                throw new IllegalStateException(
                        "Resource template method must only consume String parameters: " + methodDesc(method));
            }
            if (!variableMatcher.variables().contains(paramName)) {
                throw new IllegalStateException(
                        "Parameter [" + paramName + "] does not match an URI template variable: "
                                + methodDesc(method));
            }
        }
    }

    private static void validateNotificationMethod(MethodInfo method) {
        if (method.returnType().kind() != Kind.VOID
                && (!method.returnType().name().equals(DotNames.UNI)
                        || !method.returnType().asParameterizedType().arguments().get(0).name()
                                .equals(DotName.createSimple(Void.class)))) {
            throw new IllegalStateException("Notification method must return void or Uni<Void>");
        }
        List<MethodParameterInfo> params = parameters(method, NOTIFICATION);
        if (!params.isEmpty()) {
            throw new IllegalStateException(
                    "Notification method %s may not consume the following parameter types: %s".formatted(
                            methodDesc(method),
                            params.stream().map(MethodParameterInfo::type).toList()));
        }
    }

    static List<MethodParameterInfo> parameters(MethodInfo method, Feature feature) {
        List<MethodParameterInfo> ret = new ArrayList<>();
        for (MethodParameterInfo param : method.parameters()) {
            Provider provider = FeatureArguments.providerFrom(param.type());
            if (!provider.isValidFor(feature)) {
                throw new IllegalStateException(
                        "%s feature method %s may not accept parameter of type %s".formatted(feature,
                                methodDesc(method), param.type()));
            }
            if (provider == Provider.PARAMS) {
                ret.add(param);
            }
        }
        return ret;
    }

    static List<Throwable> findWrongAnnotationUsage(IndexView index,
            FeatureAnnotationsBuildItem featureAnnotations,
            BuildProducer<io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem> validationErrors) {
        List<Throwable> wrongUsages = new ArrayList<>();
        for (Map.Entry<DotName, Feature> e : featureAnnotations.annotationToFeature().entrySet()) {
            findWrongMethods(index, e.getKey(), e.getValue(), wrongUsages);
        }
        return wrongUsages;
    }

    private static void findWrongMethods(IndexView index, DotName annotationName, Feature feature,
            List<Throwable> wrongUsages) {
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

    static String methodDesc(MethodInfo method) {
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
                DotName name = method.returnType().asParameterizedType().name();
                return !name.equals(DotNames.UNI) && !name.equals(DotNames.MULTI);
            default:
                throw new IllegalStateException(
                        "Unsupported return type:" + methodDesc(method));
        }
    }

    static ExecutionModel executionModel(MethodInfo method,
            TransformedAnnotationsBuildItem transformedAnnotations) {
        if (KotlinUtils.isKotlinSuspendMethod(method)
                && (transformedAnnotations.hasAnnotation(method, DotNames.RUN_ON_VIRTUAL_THREAD)
                        || transformedAnnotations.hasAnnotation(method.declaringClass(),
                                DotNames.RUN_ON_VIRTUAL_THREAD)
                        || transformedAnnotations.hasAnnotation(method, DotNames.BLOCKING)
                        || transformedAnnotations.hasAnnotation(method, DotNames.NON_BLOCKING))) {
            throw new IllegalStateException("Kotlin `suspend` functions in MCP components may not be "
                    + "annotated @Blocking, @NonBlocking or @RunOnVirtualThread: "
                    + methodDesc(method));
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
            return ExecutionModel.WORKER_THREAD;
        } else {
            return hasBlockingSignature(method) ? ExecutionModel.WORKER_THREAD : ExecutionModel.EVENT_LOOP;
        }
    }

    static boolean isReturnTypeReflectionNeeded(DotName returnTypeName) {
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

    static Set<String> initServerBindings(McpServersBuildTimeConfig config, IndexView index, MethodInfo method) {
        Set<String> ret = new HashSet<String>();
        Optional<Boolean> multiServerBindings = config.supportMultiServerBindings();
        if (multiServerBindings.orElse(true)) {
            // The set of bindings includes all values declared on the feature method and
            // all values defined on the declaring class of the feature
            List<AnnotationInstance> methodAnnotations = new ArrayList<>(
                    method.declaredAnnotationsWithRepeatable(DotNames.MCP_SERVER, index));
            methodAnnotations.addAll(method.declaredAnnotationsWithRepeatable(DotNames.MCPJAVA_MCP_SERVER, index));
            List<AnnotationInstance> classAnnotations = new ArrayList<>(
                    method.declaringClass().declaredAnnotationsWithRepeatable(DotNames.MCP_SERVER, index));
            classAnnotations.addAll(
                    method.declaringClass().declaredAnnotationsWithRepeatable(DotNames.MCPJAVA_MCP_SERVER, index));
            for (AnnotationInstance a : methodAnnotations) {
                ret.add(a.value().asString());
            }
            for (AnnotationInstance a : classAnnotations) {
                ret.add(a.value().asString());
            }
            if (ret.size() > 1 && methodAnnotations.size() == 1 && classAnnotations.size() == 1
                    && multiServerBindings.isEmpty()) {
                // In versions 1.10 and lower, the method-level annotation overrode the
                // class-level one;
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
            List<AnnotationInstance> methodAnnotations = new ArrayList<>(
                    method.declaredAnnotationsWithRepeatable(DotNames.MCP_SERVER, index));
            methodAnnotations.addAll(method.declaredAnnotationsWithRepeatable(DotNames.MCPJAVA_MCP_SERVER, index));
            if (methodAnnotations.size() > 1) {
                throw new IllegalStateException(
                        "Only single @McpServer binding is allowed in compatibility mode: " + method.declaringClass()
                                + "#"
                                + method.name()
                                + "()");
            } else if (methodAnnotations.size() == 1) {
                ret.add(methodAnnotations.get(0).value().asString());
            } else {
                // Try the declaring class
                List<AnnotationInstance> classAnnotations = new ArrayList<>(
                        method.declaringClass().declaredAnnotationsWithRepeatable(DotNames.MCP_SERVER, index));
                classAnnotations.addAll(
                        method.declaringClass().declaredAnnotationsWithRepeatable(DotNames.MCPJAVA_MCP_SERVER, index));
                if (classAnnotations.size() > 1) {
                    throw new IllegalStateException(
                            "Only single @McpServer binding is allowed in compatibility mode: "
                                    + method.declaringClass() + "#"
                                    + method.name()
                                    + "()");
                } else if (classAnnotations.size() == 1) {
                    ret.add(classAnnotations.get(0).value().asString());
                }
            }
        }
        return ret.isEmpty() ? Set.of(McpServer.DEFAULT) : Set.copyOf(ret);
    }

    static boolean isParamTypeReflectionNeeded(org.jboss.jandex.Type paramType) {
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
