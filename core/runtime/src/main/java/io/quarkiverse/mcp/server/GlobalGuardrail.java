package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * This qualifier marks a {@link ToolInputGuardrail} and/or {@link ToolOutputGuardrail} bean as a global guardrail, i.e. a
 * guardrail that is applied to all tools, including the tools registered programmatically via {@link ToolManager}.
 * <p>
 * A global guardrail must be a CDI bean; it can be a class bean, a producer method/field or a synthetic bean. If no scope
 * is declared on a class bean then {@link jakarta.inject.Singleton} is added automatically. A single instance of a global
 * guardrail is shared by all tools. Note that a global guardrail bean does not have the
 * {@link jakarta.enterprise.inject.Default}
 * qualifier, i.e. the {@link GlobalGuardrail} qualifier must be used if injected elsewhere.
 * <p>
 * Global input guardrails are executed <em>before</em> the input guardrails declared on a tool. Global output guardrails
 * are executed <em>after</em> the output guardrails declared on a tool. Global guardrails are sorted by the priority of the
 * bean, i.e. {@link jakarta.annotation.Priority}; a guardrail with higher priority is executed first. The default priority
 * is {@code 0}. If a global guardrail is also declared on a specific tool then it's only executed once, as a global
 * guardrail.
 * <p>
 * A global guardrail must support all execution models. If the bean class (or the declared type of a producer, if it's a
 * class) is annotated with {@link SupportedExecutionModels} that does not include all execution models then the build
 * fails. If the declared type of a producer is an interface then the produced guardrail is still required to support all
 * execution models but it's not validated.
 *
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 */
@Qualifier
@Retention(RUNTIME)
@Target({ TYPE, METHOD, FIELD, PARAMETER })
public @interface GlobalGuardrail {

    /**
     * Supports inline instantiation of this qualifier.
     */
    final class Literal extends AnnotationLiteral<GlobalGuardrail> implements GlobalGuardrail {

        public static final Literal INSTANCE = new Literal();

        private static final long serialVersionUID = 1L;

    }

}
