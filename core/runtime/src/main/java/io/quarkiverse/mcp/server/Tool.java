package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import io.smallrye.mutiny.Uni;

/**
 * Annotates a business method of a CDI bean as an exposed tool.
 * <p>
 * A result of a "tool call" operation is always represented as a {@link ToolResponse}. However, the annotated method can also
 * return other types that are converted according to the following rules.
 * <p>
 * <ul>
 * <li>If it returns {@link String} then the reponse is {@code success} and contains a single {@link TextContent}.</li>
 * <li>If it returns an implementation of {@link Content} then the reponse is {@code success} and contains a single
 * content object.</li>
 * <li>If it returns a {@link List} of {@link Content} implementations or strings then the reponse is
 * {@code success} and contains a list of relevant content objects.</li>
 * <li>If it returns any other type {@code X} or {@code List<X>} then {@code X} is encoded using the {@link ToolResponseEncoder}
 * and {@link ContentEncoder} API and afterwards the rules above apply.</li>
 * <li>It may also return a {@link Uni} that wraps any of the type mentioned above.</li>
 * </ul>
 *
 * <p>
 * There is a default content encoder registered; it encodes the returned value as JSON.
 *
 * @see ToolResponse
 * @see ToolArg
 * @see ToolResponseEncoder
 * @see ContentEncoder
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Tool {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    /**
     * Each tool must have a unique name. By default, the name is derived from the name of the annotated method.
     */
    String name() default ELEMENT_NAME;

    /**
     * An optional description.
     */
    String description() default "";

}
