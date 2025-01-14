package io.quarkiverse.mcp.server;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a parameter of a "complete" method, such as {@link CompletePrompt}.
 */
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CompleteArg {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    String name() default ELEMENT_NAME;

}
