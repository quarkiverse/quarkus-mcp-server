package io.quarkiverse.mcp.server.deployment;

import java.lang.reflect.Modifier;
import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.builder.item.SimpleBuildItem;

final class FeatureAnnotationsBuildItem extends SimpleBuildItem {

    private final Map<DotName, Feature> annotationToFeature;

    FeatureAnnotationsBuildItem(Map<DotName, Feature> annotationToFeature) {
        this.annotationToFeature = annotationToFeature;
    }

    Map<DotName, Feature> annotationToFeature() {
        return annotationToFeature;
    }

    boolean isFeatureMethod(MethodInfo method) {
        if (!Modifier.isStatic(method.flags())) {
            for (DotName annotationName : annotationToFeature.keySet()) {
                if (method.hasDeclaredAnnotation(annotationName)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean hasFeatureMethod(BeanInfo bean) {
        ClassInfo beanClass = bean.getTarget().get().asClass();
        for (DotName annotationName : annotationToFeature.keySet()) {
            if (beanClass.hasAnnotation(annotationName)) {
                return true;
            }
        }
        return false;
    }

    AnnotationInstance getFeatureAnnotation(MethodInfo method) {
        for (AnnotationInstance annotation : method.declaredAnnotations()) {
            if (annotationToFeature.containsKey(annotation.name())) {
                return annotation;
            }
        }
        return null;
    }

    Feature getFeature(AnnotationInstance annotation) {
        Feature ret = annotationToFeature.get(annotation.name());
        if (ret != null) {
            return ret;
        }
        throw new IllegalStateException("Not a feature annotation");
    }

}
