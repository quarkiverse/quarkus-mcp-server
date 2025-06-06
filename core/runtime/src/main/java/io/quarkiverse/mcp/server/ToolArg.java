package io.quarkiverse.mcp.server;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;

/**
 * Annotates a parameter of a {@link Tool} method.
 */
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolArg {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    String name() default ELEMENT_NAME;

    String description() default "";

    /**
     * An argument is required by default unless no annotation value is set explicitly and the type of the annotated parameter
     * is {@link Optional} or the default value is set with {@link ToolArg#defaultValue()}.
     */
    boolean required() default true;

    /**
     * The default value is used when an MCP client does not provide an argument value.
     * <p>
     * {@link String}, primitive types and corresponding wrappers, and enums are converted automatically. For any other
     * parameter type a custom {@link DefaultValueConverter} is needed.
     *
     * @see DefaultValueConverter
     */
    String defaultValue() default "";

}
