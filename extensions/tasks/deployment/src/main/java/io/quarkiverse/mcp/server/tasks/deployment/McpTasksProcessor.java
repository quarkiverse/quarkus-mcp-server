package io.quarkiverse.mcp.server.tasks.deployment;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;

import io.quarkiverse.mcp.server.deployment.CustomArgumentTypeBuildItem;
import io.quarkiverse.mcp.server.deployment.FeatureMethodBuildItem;
import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkiverse.mcp.server.tasks.Task;
import io.quarkiverse.mcp.server.tasks.TaskContext;
import io.quarkiverse.mcp.server.tasks.runtime.TaskManagerImpl;
import io.quarkiverse.mcp.server.tasks.runtime.TaskSubscriptionFilterExtension;
import io.quarkiverse.mcp.server.tasks.runtime.TaskToolCallInterceptor;
import io.quarkiverse.mcp.server.tasks.runtime.TasksExtension;
import io.quarkiverse.mcp.server.tasks.runtime.TasksRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.configuration.DurationConverter;

class McpTasksProcessor {

    private static final DotName TASK = DotName.createSimple(Task.class);
    private static final DotName TASK_CONTEXT = DotName.createSimple(TaskContext.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-tasks");
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(TaskManagerImpl.class, TasksExtension.class, TaskToolCallInterceptor.class,
                        TaskSubscriptionFilterExtension.class)
                .build());
    }

    @BuildStep
    CustomArgumentTypeBuildItem taskContextArgument() {
        // A TaskContext parameter is supplied by the tasks extension; it is only valid for tools
        return new CustomArgumentTypeBuildItem(TASK_CONTEXT, Set.of(Feature.TOOL));
    }

    @BuildStep
    void validate(BeanArchiveIndexBuildItem beanArchiveIndex,
            List<FeatureMethodBuildItem> featureMethods,
            BuildProducer<ValidationErrorBuildItem> errors) {
        Set<MethodInfo> toolMethods = new HashSet<>();
        for (FeatureMethodBuildItem featureMethod : featureMethods) {
            if (featureMethod.isTool()) {
                toolMethods.add(featureMethod.getMethod());
            }
        }
        // @Task may only be declared on a tool method
        for (AnnotationInstance task : beanArchiveIndex.getIndex().getAnnotations(TASK)) {
            if (task.target().kind() == AnnotationTarget.Kind.METHOD) {
                MethodInfo method = task.target().asMethod();
                if (!toolMethods.contains(method)) {
                    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(
                            "@Task may only be declared on a @Tool method: " + methodDesc(method))));
                }
            }
        }
        for (MethodInfo method : toolMethods) {
            AnnotationInstance task = method.declaredAnnotation(TASK);
            if (task != null) {
                // Validate the durations
                try {
                    parseDuration(task.value("ttl"), "ttl");
                    Duration pollInterval = parseDuration(task.value("pollInterval"), "pollInterval");
                    if (pollInterval != null && (pollInterval.isZero() || pollInterval.isNegative())) {
                        throw new IllegalArgumentException("pollInterval must be positive: " + pollInterval);
                    }
                } catch (IllegalArgumentException e) {
                    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(
                            "Invalid @Task declared on " + methodDesc(method) + ": " + e.getMessage(), e)));
                }
            } else {
                // A TaskContext parameter may only be declared on a task-augmented tool
                for (MethodParameterInfo param : method.parameters()) {
                    if (param.type().name().equals(TASK_CONTEXT)) {
                        errors.produce(new ValidationErrorBuildItem(new IllegalStateException(
                                "A tool method that declares a TaskContext parameter must be annotated with @Task: "
                                        + methodDesc(method))));
                    }
                }
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerTaskOptions(TasksRecorder recorder, List<FeatureMethodBuildItem> featureMethods,
            BeanContainerBuildItem beanContainer) {
        for (FeatureMethodBuildItem featureMethod : featureMethods) {
            if (!featureMethod.isTool()) {
                continue;
            }
            AnnotationInstance task = featureMethod.getMethod().declaredAnnotation(TASK);
            if (task == null) {
                continue;
            }
            AnnotationValue requiredValue = task.value("required");
            boolean required = requiredValue != null && requiredValue.asBoolean();
            Duration ttl = parseDuration(task.value("ttl"), "ttl");
            Duration pollInterval = parseDuration(task.value("pollInterval"), "pollInterval");
            recorder.registerTaskOptions(featureMethod.getName(), Set.copyOf(featureMethod.getServers()), required,
                    ttl != null ? ttl.toMillis() : null, pollInterval != null ? pollInterval.toMillis() : null);
        }
    }

    /**
     * @throws IllegalArgumentException if the duration is not valid
     */
    private static Duration parseDuration(AnnotationValue value, String name) {
        if (value == null || value.asString().isBlank()) {
            return null;
        }
        try {
            return DurationConverter.parseDuration(value.asString());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid " + name + " duration: " + value.asString(), e);
        }
    }

    private static String methodDesc(MethodInfo method) {
        return method.declaringClass().name().withoutPackagePrefix() + "#" + method.name() + "()";
    }

}
