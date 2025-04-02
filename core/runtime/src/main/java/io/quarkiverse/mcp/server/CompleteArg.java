package io.quarkiverse.mcp.server;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This annotation can be used to customize the name of a completed argument, i.e. the name of a parameter of a completion
 * method.
 * <p>
 * A completion method must consume exactly one {@link String} argument.
 *
 * @see CompletePrompt
 * @see CompleteResourceTemplate
 */
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CompleteArg {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    /**
     * @return the name of the completed argument
     */
    String name() default ELEMENT_NAME;

}
