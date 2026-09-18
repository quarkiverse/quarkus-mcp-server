package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;

/**
 * Annotates a parameter of an {@link McpExtensionMethod} method.
 * <p>
 * Extension method parameters are bound from the top-level {@code params} object of the JSON-RPC request. By default the
 * parameter name is derived from the reflection metadata (the class must be compiled with {@code -parameters}). This annotation
 * allows the wire name to be set explicitly, which is useful when the JSON-RPC parameter name declared by the extension is not
 * a
 * valid or desirable Java identifier.
 *
 * @see McpExtensionMethod
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface McpExtensionMethodArg {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    /**
     * @return the name of the parameter within the request {@code params} object
     */
    String name() default ELEMENT_NAME;

    /**
     * A parameter is required by default unless no annotation value is set explicitly and the type of the annotated parameter
     * is {@link Optional} or the default value is set with {@link #defaultValue()}.
     *
     * @return {@code true} if the parameter is required
     */
    boolean required() default true;

    /**
     * The default value is used when an MCP client does not provide a parameter value.
     * <p>
     * {@link String}, primitive types and corresponding wrappers, and enums are converted automatically. For any other
     * parameter type a custom {@link DefaultValueConverter} is needed.
     *
     * @return the default value
     * @see DefaultValueConverter
     */
    String defaultValue() default "";

}
