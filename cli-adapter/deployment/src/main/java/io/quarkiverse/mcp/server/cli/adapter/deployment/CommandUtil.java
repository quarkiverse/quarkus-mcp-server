package io.quarkiverse.mcp.server.cli.adapter.deployment;

import static io.quarkiverse.mcp.server.cli.adapter.deployment.DotNames.QUALIFIER;
import static io.quarkus.arc.processor.DotNames.INJECT;
import static io.quarkus.arc.processor.DotNames.OBJECT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;

import io.quarkus.picocli.runtime.annotations.TopCommand;

class CommandUtil {

    /**
     * Look for a {@link TopCommand) annotation in the index.
     *
     * @param indexView the index view
     * @return an {@link Optional} containing the name of the class annotated with {@link TopCommand} if found, empty otherwise
     */
    public static Optional<DotName> findTopCommand(IndexView indexView) {
        return indexView.getAnnotations(DotNames.TOP_COMAMND)
                .stream()
                .filter(ann -> ann.target().kind() == AnnotationTarget.Kind.CLASS)
                .map(ann -> ann.target().asClass().name())
                .findFirst();
    }

    /**
     * Check if the given class should be instantiated.
     * The class can be instantiated if it has a default constructor and does not require container services.
     *
     * @param clazz the given class
     * @return {@code true} if the class can be instantiated, {@code false} otherwise
     */
    public static boolean canBeInstantiated(ClassInfo clazz, IndexView index) {
        return hasNoArgConstructor(clazz) && !requiresContainerServices(clazz, index);
    }

    /**
     * List all the qualifiers for the given class.
     *
     * @param clazz the given class
     * @param index the index
     * @return a list of {@link AnnotationInstance}
     */
    public static List<AnnotationInstance> listQualifiers(ClassInfo clazz, IndexView index) {
        return clazz.annotations().stream()
                .filter(a -> isQualifier(a, index))
                .toList();
    }

    /**
     * List all Fields annotated with {@link picocli.CommandLine.Parameters}.
     * The search is done recursively on the super classes.
     *
     * @param clazz the given class
     * @param index the index
     * @return a list of {@link FieldInfo}
     */
    public static List<FieldInfo> listParameters(ClassInfo clazz, IndexView index) {
        List<FieldInfo> parameters = new ArrayList<>();
        clazz.fields().stream().filter(f -> f.annotations(DotNames.PARAMETERS).size() > 0).forEach(parameters::add);
        DotName superClassName = clazz.superClassType().name();
        if (superClassName != null) {
            ClassInfo superClassInfo = index.getClassByName(superClassName);
            if (superClassInfo != null) {
                parameters.addAll(listParameters(superClassInfo, index));
            }
        }
        return parameters;
    }

    /**
     * List all Fields annotated with {@link picocli.CommandLine.Option}.
     * The search is done recursively on the super classes.
     *
     * @param clazz the given class
     * @param index the index
     * @return a map of option names to {@link FieldInfo}
     */
    public static Map<String, FieldInfo> listOptions(ClassInfo clazz, IndexView index) {
        Map<String, FieldInfo> options = new HashMap<>();
        clazz.fields().stream().filter(f -> f.annotations(DotNames.OPTION).size() > 0).forEach(a -> {
            a.annotations(DotNames.OPTION).forEach(o -> {
                String[] names = o.value("names").asStringArray();
                if (names.length != 0) {
                    options.put(names[0], a);
                }
            });
        });
        DotName superClassName = clazz.superClassType().name();
        if (superClassName != null) {
            ClassInfo superClassInfo = index.getClassByName(superClassName);
            if (superClassInfo != null) {
                options.putAll(listOptions(superClassInfo, index));
            }
        }
        return options;
    }

    /**
     * Check if the given class is a qualifier.
     *
     * @param clazz the given class
     * @return {@code true} if the class is a qualifier, {@code false} otherwise
     */
    public static boolean isQualifier(AnnotationInstance annotationInstance, IndexView index) {
        return isQualifier(index.getClassByName(annotationInstance.name()));
    }

    /**
     * Check if the given class is a qualifier.
     *
     * @param clazz the given class
     * @return {@code true} if the class is a qualifier, {@code false} otherwise
     */
    public static boolean isQualifier(ClassInfo clazz) {
        return clazz != null && clazz.annotations().stream().anyMatch(a -> QUALIFIER.equals(a.name()));
    }

    protected static String findCommonPrefix(Collection<String> names) {
        if (names.isEmpty() || names.size() == 1) {
            return "";
        }
        return names.stream()
                .reduce((prefix, name) -> {
                    while (!name.startsWith(prefix)) {
                        prefix = prefix.substring(0, prefix.length() - 1);
                        if (prefix.isEmpty()) {
                            return "";
                        }
                    }
                    return prefix;
                })
                .orElse("");
    }

    private static boolean hasNoArgConstructor(ClassInfo clazz) {
        return !clazz.constructors().stream().anyMatch(c -> !c.parameters().isEmpty());
    }

    private static boolean requiresContainerServices(ClassInfo clazz, IndexView index) {
        return requiresContainerServices(clazz, Set.of(INJECT), index);
    }

    private static boolean requiresContainerServices(ClassInfo clazz, Set<DotName> containerAnnotationNames, IndexView index) {
        if (hasContainerAnnotation(clazz, containerAnnotationNames)) {
            return true;
        }
        if (index != null) {
            DotName superName = clazz.superName();
            while (superName != null && !superName.equals(OBJECT)) {
                final ClassInfo superClass = index.getClassByName(superName);
                if (superClass != null) {
                    if (hasContainerAnnotation(superClass, containerAnnotationNames)) {
                        return true;
                    }
                    superName = superClass.superName();
                } else {
                    superName = null;
                }
            }
        }
        return false;
    }

    private static boolean hasContainerAnnotation(ClassInfo clazz, Set<DotName> containerAnnotationNames) {
        if (clazz.annotationsMap().isEmpty() || containerAnnotationNames.isEmpty()) {
            return false;
        }
        return containsAny(clazz, containerAnnotationNames);
    }

    private static boolean containsAny(ClassInfo clazz, Set<DotName> annotationNames) {
        return clazz.annotationsMap().keySet().stream().anyMatch(annotationNames::contains);
    }
}
