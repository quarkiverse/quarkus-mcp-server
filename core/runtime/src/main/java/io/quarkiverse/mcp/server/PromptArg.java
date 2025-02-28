package io.quarkiverse.mcp.server;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;

/**
 * Annotates a parameter of a {@link Prompt} method.
 */
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PromptArg {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    String name() default ELEMENT_NAME;

    String description() default "";

    /**
     * An argument is required by default. However, if the annotated type is {@link Optional} and no annotation value is set
     * explicitly then the argument is not required.
     */
    boolean required() default true;

}
