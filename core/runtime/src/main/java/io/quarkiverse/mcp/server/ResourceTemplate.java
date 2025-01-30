package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import io.smallrye.mutiny.Uni;

/**
 * Annotates a business method of a CDI bean as an exposed resource template.
 * <p>
 * The result of a "resource read" operation is always represented as a {@link ResourceResponse}. However, the annotated method
 * can also return other types that are converted according to the following rules.
 *
 * <ul>
 * <li>If it returns an implementation of {@link ResourceContents} then the response contains the single contents
 * object.</li>
 * <li>If it returns a {@link List} of {@link ResourceContents} implementations then the response contains the list of
 * contents objects.</li>
 * <li>If it returns any other type {@code X} or {@code List<X>} then {@code X} is encoded using the
 * {@link ResourceContentsEncoder} API and afterwards the rules above apply.</li>
 * <li>The method may return a {@link Uni} that wraps any of the type mentioned above.</li>
 * </ul>
 *
 * <p>
 * There is a default resource contents encoder registered; it encodes the returned value as JSON.
 *
 * @see Resource
 * @see ResourceResponse
 * @see ResourceContentsEncoder
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ResourceTemplate {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    /**
     * The human-readable name for this resource template.
     */
    String name() default ELEMENT_NAME;

    /**
     * The description of what this resource template represents.
     */
    String description() default "";

    /**
     * The Level 1 URI template that can be used to construct resource URIs.
     * <p>
     * See <a href="https://datatracker.ietf.org/doc/html/rfc6570#section-1.2">the RFC 6570</a> for syntax definition.
     */
    String uriTemplate();

    /**
     * The MIME type of this resource template.
     */
    String mimeType() default "";

}
