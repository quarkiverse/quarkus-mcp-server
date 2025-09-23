package io.quarkiverse.mcp.server.hibernate.validator.deployment;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter;
import io.quarkiverse.mcp.server.hibernate.validator.runtime.WrapConstraintViolations;
import io.quarkiverse.mcp.server.hibernate.validator.runtime.WrapConstraintViolationsInterceptor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class McpServerHibernateValidatorProcessor {

    private static final DotName WRAP_CONSTRAINT_VIOLATIONS = DotName.createSimple(WrapConstraintViolations.class);
    private static final DotName METHOD_VALIDATED = DotName
            .createSimple("io.quarkus.hibernate.validator.runtime.interceptor.MethodValidated");

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(
                new AdditionalBeanBuildItem(WrapConstraintViolations.class, WrapConstraintViolationsInterceptor.class,
                        ConstraintViolationConverter.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(
                "io.quarkiverse.mcp.server.hibernate.validator.runtime.ConstraintViolationConverterImpl"));
    }

    @BuildStep
    AnnotationsTransformerBuildItem wrapConstraintViolations() {
        return new AnnotationsTransformerBuildItem(
                AnnotationTransformation.forMethods()
                        .whenAnyMatch(METHOD_VALIDATED)
                        // Make sure the transformation happens after hibernate validator added @MethodValidated
                        .priority(AnnotationTransformation.DEFAULT_PRIORITY_VALUE - 1000)
                        .transform(tc -> {
                            tc.add(AnnotationInstance.builder(WRAP_CONSTRAINT_VIOLATIONS).build());
                        }));
    }

}
