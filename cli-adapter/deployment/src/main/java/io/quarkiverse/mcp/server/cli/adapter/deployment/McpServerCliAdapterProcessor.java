package io.quarkiverse.mcp.server.cli.adapter.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiFunction;
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
import org.jboss.jandex.Type;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.cli.adapter.runtime.McpAdapter;
import io.quarkiverse.mcp.server.stdio.deployment.McpStdioExplicitInitializationBuildItem;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo.CatchBlockCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.gizmo.TryBlock;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

class McpServerCliAdapterProcessor {

    private static final String FEATURE = "mcp-server-cli-adapter";

    private static final String NON_ALPHANUMERIC = "[^a-zA-Z0-9]";
    private static final String UNDERSCORE = "_";

    private static final String OPTION_TYPENAME = "L" + Option.class.getName().replace('.', '/') + ";";
    private static final String MCP_ADAPTER_TYPE = org.objectweb.asm.Type.getType(McpAdapter.class).getInternalName();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    McpStdioExplicitInitializationBuildItem requestExplicitMcpStdioInitialization() {
        return new McpStdioExplicitInitializationBuildItem();
    }

    /***
     * Generate a bean that wraps around the actual picocli command
     * providing access to it as a {@link Tool}.
     *
     * The code of the generated bean will look
     *
     * <pre>
     * &#64;Unremovable
     * &#64;ApplicationScoped
     * public class TaskUninstallMcpWrapper {
     *     &#64;Inject
     *     protected TaskUninstall command;
     *
     *     &#64;Tool(description = "Uninstall Tekton tasks.")
     *     public String taskuninstall(
     *             &#64;ToolArg(name = "all", description = "Install all the tasks.", required = false) String all,
     *             &#64;ToolArg(name = "namespace", description = "The target namespace (where the Custom Resources will be installed)", required = false) String namespace,
     *             @ToolArg(name = "taskName", description = "Task name.", required = false) String taskName) {
     *         try {
     *             HashMap map = new HashMap();
     *             ArrayList list = new ArrayList();
     *             TaskUninstall cmd = this.command;
     *             map.put("--all", all);
     *             map.put("--namespace", namespace);
     *             list.add(taskName);
     *             return McpAdapter.callCommand(cmd, (Map) map, (List) list);
     *         } catch (Exception e) {
     *             return e.getMessage();
     *         }
     *     }
     * }
     * </pre>
     */
    @BuildStep
    void createCommandWrappers(CombinedIndexBuildItem combinedIndex,
            BuildProducer<BytecodeTransformerBuildItem> transformers,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans) {

        IndexView index = combinedIndex.getIndex();
        Optional<DotName> topCommand = findTopCommand(index);

        topCommand.ifPresentOrElse(dotName -> {
            ClassInfo classInfo = index.getClassByName(dotName);
            if (classInfo != null) {
                AnnotationInstance topCommandAnnotation = classInfo.annotation(DotNames.TOP_COMAMND);
                AnnotationValue annotationValue = topCommandAnnotation.value("subcommands");
                if (annotationValue != null) {
                    Type[] subcommands = annotationValue.asClassArray();
                    for (Type subcommand : subcommands) {
                        ClassInfo subcommandClass = index.getClassByName(subcommand.name());
                        if (subcommandClass != null) {
                            wrapCommand(index, subcommandClass, generatedBeans);
                        }
                    }
                } else {
                    wrapCommand(index, classInfo, generatedBeans);
                }

                String className = classInfo.name().toString();
                transformers.produce(
                        new BytecodeTransformerBuildItem(className, new BiFunction<String, ClassVisitor, ClassVisitor>() {
                            @Override
                            public ClassVisitor apply(String originalClassName, ClassVisitor cv) {
                                return new ClassVisitor(Gizmo.ASM_API_VERSION, cv) {
                                    private String currentClassName;
                                    private boolean hasMcpField = false;

                                    @Override
                                    public void visit(int version, int access, String name, String signature, String superName,
                                            String[] interfaces) {
                                        currentClassName = name;
                                        super.visit(version, access, name, signature, superName, interfaces);
                                    }

                                    @Override
                                    public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                                            Object value) {
                                        if ("mcp".equals(name) && "Z".equals(descriptor)) {
                                            hasMcpField = true;
                                        }
                                        return super.visitField(access, name, descriptor, signature, value);
                                    }

                                    @Override
                                    public void visitEnd() {
                                        if (!hasMcpField) {
                                            // Add: public boolean mcp;
                                            FieldVisitor fv = cv.visitField(Opcodes.ACC_PUBLIC, "mcp", "Z", null, null);
                                            // Add the @Option annotation with names and description.
                                            AnnotationVisitor av = fv.visitAnnotation(OPTION_TYPENAME, true);

                                            AnnotationVisitor namesAv = av.visitArray("names");
                                            namesAv.visit(null, "--mcp");
                                            namesAv.visitEnd();

                                            AnnotationVisitor descAv = av.visitArray("description");
                                            descAv.visit(null, "Start an MCP server for the current CLI.");
                                            descAv.visitEnd();

                                            av.visitEnd();
                                            fv.visitEnd();
                                        }
                                        super.visitEnd();
                                    }

                                    @Override
                                    public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                            String signature, String[] exceptions) {
                                        MethodVisitor mv = super.visitMethod(access, methodName, descriptor, signature,
                                                exceptions);
                                        // Target the "call" method.
                                        if ("call".equals(methodName)) {
                                            return new MethodVisitor(Gizmo.ASM_API_VERSION, mv) {
                                                @Override
                                                public void visitCode() {
                                                    mv.visitCode();
                                                    // Insert: if (this.mcp) { return McpAdapter.startMcp(); }
                                                    // Load 'this'
                                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                                    // Get the boolean field "mcp" (descriptor "Z")
                                                    mv.visitFieldInsn(Opcodes.GETFIELD, currentClassName, "mcp", "Z");
                                                    Label continueLabel = new Label();
                                                    // If mcp == false, jump to continueLabel.
                                                    mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                                    // Else, call McpAdapter.startMcp(); assumed descriptor: ()I.
                                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, MCP_ADAPTER_TYPE, "startMcp",
                                                            "()Ljava/lang/Integer;", false);
                                                    // Return the int value.
                                                    mv.visitInsn(Opcodes.ARETURN);
                                                    // Mark the continue label.
                                                    mv.visitLabel(continueLabel);
                                                }
                                            };
                                        }
                                        return mv;
                                    }
                                };
                            }
                        }));
            }
        }, () -> {
            for (DotName c : classesAnnotatedWith(index, Command.class.getName())) {
                ClassInfo beanClass = index.getClassByName(c);

                if (beanClass.name().toString().startsWith(DotNames.COMMANDLINE.packagePrefix())) {
                    continue;
                }
                wrapCommand(index, beanClass, generatedBeans);
            }
        });
    }

    void wrapCommand(IndexView index, ClassInfo beanClass, BuildProducer<GeneratedBeanBuildItem> generatedBeans) {
        AnnotationInstance commandAnnotation = beanClass.annotation(DotNames.COMMAND);
        String name = beanClass.simpleName().replaceAll(NON_ALPHANUMERIC, UNDERSCORE).toLowerCase();
        String description = getCommandDescriptionOrHeader(commandAnnotation);

        String beanClassName = beanClass.name().toString();
        // If the command has itself subcommands we need to wrap them too.
        AnnotationValue annotationValue = commandAnnotation.value("subcommands");
        if (annotationValue != null) {
            Type[] subcommands = annotationValue.asClassArray();
            for (Type subcommand : subcommands) {
                ClassInfo subcommandClass = index.getClassByName(subcommand.name());
                if (subcommandClass != null) {
                    wrapCommand(index, subcommandClass, generatedBeans);
                }
            }
        }

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
                tryBlock.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(HashMap.class, "put", Object.class, Object.class, Object.class),
                        hashMapHandle, paramKey, paramValue);
                optionIndex++;
            }

            for (int i = 0; i < parameters.size(); i++) {
                ResultHandle paramValue = executeMethod.getMethodParam(optionIndex + i);
                tryBlock.invokeVirtualMethod(MethodDescriptor.ofMethod(ArrayList.class, "add", boolean.class, Object.class),
                        arrayListHandle, paramValue);
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

    private Optional<DotName> findTopCommand(IndexView indexView) {
        return indexView.getAnnotations(DotNames.TOP_COMAMND)
                .stream()
                .filter(ann -> ann.target().kind() == AnnotationTarget.Kind.CLASS)
                .map(ann -> ann.target().asClass().name())
                .findFirst();
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
