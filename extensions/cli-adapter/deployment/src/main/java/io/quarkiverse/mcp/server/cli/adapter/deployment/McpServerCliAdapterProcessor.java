package io.quarkiverse.mcp.server.cli.adapter.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.cli.adapter.runtime.McpAdapter;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo.CatchBlockCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.gizmo.TryBlock;
import picocli.CommandLine.Command;

class McpServerCliAdapterProcessor {

    private static final String FEATURE = "mcp-server-cli-adapter";

    private static final String NON_ALPHANUMERIC = "[^a-zA-Z0-9]";
    private static final String UNDERSCORE = "_";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void createCommandWrappers(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            List<AutoAddScopeBuildItem> autoAddScopeBuildItems) {

        IndexView index = combinedIndex.getIndex();
        for (DotName c : classesAnnotatedWith(index, Command.class.getName())) {
            ClassInfo beanClass = index.getClassByName(c);

            if (beanClass.name().toString().startsWith(DotNames.COMMANDLINE.packagePrefix())) {
                continue;
            }

            AnnotationInstance commandAnnotation = beanClass.annotation(DotNames.COMMAND);
            String name = beanClass.simpleName().replaceAll(NON_ALPHANUMERIC, UNDERSCORE).toLowerCase();
            String description = getCommandDescriptionOrHeader(commandAnnotation);

            String beanClassName = beanClass.name().toString();
            String generatedWrapperClassName = beanClassName + "McpWrapper";

            // Use Gizmo to generate the new class.
            ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeans);
            try (ClassCreator cc = ClassCreator.builder()
                    .className(generatedWrapperClassName)
                    .classOutput(classOutput)
                    .build()) {

                cc.addAnnotation(Unremovable.class);
                cc.addAnnotation(ApplicationScoped.class);

                boolean canBeInstantiated = CommandUtil.canBeInstantiated(beanClass, index);

                FieldCreator commandField = cc.getFieldCreator("command", beanClassName);
                commandField.setModifiers(Modifier.PROTECTED);

                if (!canBeInstantiated) {
                    commandField.addAnnotation(Inject.class);
                    for (AnnotationInstance qualifier : CommandUtil.listQualifiers(beanClass, index)) {
                        commandField.addAnnotation(qualifier);
                    }

                } else {
                    MethodCreator constructor = cc.getMethodCreator("<init>", void.class);
                    constructor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), constructor.getThis());
                    ResultHandle newCommand = constructor.newInstance(MethodDescriptor.ofConstructor(beanClassName));
                    constructor.writeInstanceField(
                            FieldDescriptor.of(generatedWrapperClassName, "command", beanClassName),
                            constructor.getThis(), newCommand);
                    constructor.returnValue(null);
                }

                Map<String, FieldInfo> options = CommandUtil.listOptions(beanClass, index);
                List<FieldInfo> parameters = CommandUtil.listParameters(beanClass, index);

                String[] parameterTypes = IntStream.range(0, options.size() + parameters.size())
                        .mapToObj(i -> String.class.getName())
                        .toArray(String[]::new);

                MethodCreator executeMethod = cc.getMethodCreator(name, String.class.getName(), parameterTypes);

                int optionIndex = 0;

                for (Entry<String, FieldInfo> entry : options.entrySet()) {
                    FieldInfo option = entry.getValue();
                    String optionName = option.name().toString();
                    String optionDescription = String.join("\n",
                            option.annotation(DotNames.OPTION).value("description").asStringArray());

                    executeMethod.getParameterAnnotations(optionIndex).addAnnotation(ToolArg.class)
                            .add("name", optionName.replaceAll("^-*", ""))
                            .add("description", optionDescription)
                            .add("required", false);
                    optionIndex++;
                }

                for (int i = 0; i < parameters.size(); i++) {
                    FieldInfo parameter = parameters.get(i);
                    String parameterName = parameter.name().toString();
                    String parameterDescription = String.join("\n",
                            parameter.annotation(DotNames.PARAMETERS).value("description").asStringArray());

                    boolean required = !DotNames.OPTIONAL.equals(parameter.type().name());
                    executeMethod.getParameterAnnotations(optionIndex + i).addAnnotation(ToolArg.class)
                            .add("name", parameterName)
                            .add("description", parameterDescription)
                            .add("required", required);
                }

                executeMethod.addAnnotation(Tool.class).add("description", description);
                TryBlock tryBlock = executeMethod.tryBlock();

                ResultHandle hashMapHandle = tryBlock.newInstance(MethodDescriptor.ofConstructor(java.util.HashMap.class));
                ResultHandle arrayListHandle = tryBlock.newInstance(MethodDescriptor.ofConstructor(java.util.ArrayList.class));

                ResultHandle commandInstance = tryBlock.readInstanceField(
                        FieldDescriptor.of(generatedWrapperClassName, "command", beanClassName),
                        tryBlock.getThis());

                optionIndex = 0;
                for (Entry<String, FieldInfo> entry : options.entrySet()) {
                    ResultHandle paramKey = tryBlock.load(entry.getKey());
                    ResultHandle paramValue = executeMethod.getMethodParam(optionIndex);
                    ResultHandle paramAsString = tryBlock.invokeStaticMethod(
                            MethodDescriptor.ofMethod(McpAdapter.class, "toString", String.class, Object.class), paramValue);
                    tryBlock.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(HashMap.class, "put", Object.class, Object.class, Object.class),
                            hashMapHandle, paramKey, paramAsString);
                    optionIndex++;
                }

                for (int i = 0; i < parameters.size(); i++) {
                    ResultHandle paramValue = executeMethod.getMethodParam(optionIndex + i);
                    ResultHandle paramAsString = tryBlock.invokeStaticMethod(
                            MethodDescriptor.ofMethod(McpAdapter.class, "toString", String.class, Object.class), paramValue);
                    tryBlock.invokeVirtualMethod(MethodDescriptor.ofMethod(ArrayList.class, "add", boolean.class, Object.class),
                            arrayListHandle, paramAsString);
                }

                ResultHandle callCommandHandle = tryBlock.invokeStaticMethod(MethodDescriptor.ofMethod(McpAdapter.class,
                        "callCommand", String.class, Object.class, Map.class, List.class), commandInstance, hashMapHandle,
                        arrayListHandle);
                tryBlock.returnValue(callCommandHandle);

                CatchBlockCreator catchBlock = tryBlock.addCatch(Exception.class);
                ResultHandle caughtException = catchBlock.getCaughtException();

                ResultHandle exceptionMessage = catchBlock.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(Exception.class, "getMessage", String.class),
                        caughtException);
                catchBlock.returnValue(exceptionMessage);
            }
        }
    }

    public MethodInfo getMethodInfo(IndexView index, ClassInfo classInfo, String methodName) {
        // Look for the method declared in this class (this example simply checks the method name)
        for (MethodInfo method : classInfo.methods()) {
            if (method.name().equals(methodName)) {
                return method;
            }
        }
        // If not found and there is a superclass, search there.
        DotName superClassName = classInfo.superName();
        if (superClassName != null) {
            ClassInfo superClassInfo = index.getClassByName(superClassName);
            if (superClassInfo != null) {
                return getMethodInfo(index, superClassInfo, methodName);
            }
        }
        // Not found in this class or any superclass.
        return null;
    }

    private List<DotName> classesAnnotatedWith(IndexView indexView, String annotationClassName) {
        return indexView.getAnnotations(DotName.createSimple(annotationClassName))
                .stream()
                .filter(ann -> ann.target().kind() == AnnotationTarget.Kind.CLASS)
                .map(ann -> ann.target().asClass().name())
                .collect(Collectors.toList());
    }

    private static String getCommandDescriptionOrHeader(AnnotationInstance annotation) {
        String result = getCommandDescription(annotation);
        if (result == null || result.isBlank()) {
            result = getCommandHeader(annotation);
        }
        return result;
    }

    private static String getCommandDescription(AnnotationInstance annotation) {
        Optional<String> descriptionHeading = Optional.ofNullable(annotation.value("descriptionHeading"))
                .map(AnnotationValue::asString);
        Optional<String[]> description = Optional.ofNullable(annotation.value("description"))
                .map(AnnotationValue::asStringArray);
        return descriptionHeading.orElse("") + String.join("\n", description.orElseGet(() -> new String[0]));
    }

    private static String getCommandHeader(AnnotationInstance annotation) {
        Optional<String> headerHeading = Optional.ofNullable(annotation.value("headerHeading"))
                .map(AnnotationValue::asString);
        Optional<String[]> header = Optional.ofNullable(annotation.value("header")).map(AnnotationValue::asStringArray);
        return headerHeading.orElse("") + String.join("\n", header.orElseGet(() -> new String[0]));
    }
}
