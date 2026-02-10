package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import io.quarkiverse.mcp.server.Resource.Annotations;
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
 * There is a default resource contents encoder registered. If the return type is {@link String} then it encodes the returned
 * value as {@link TextResourceContents}. If the return type is {@code byte[]} then it encodes the returned value as
 * {@link BlobResourceContents}. Any other return value is serialized to JSON and encoded as {@link TextResourceContents}.
 *
 * <p>
 * If you need to provide additional metadata in the {@code _meta} object of the resource template definition included in the
 * response to the
 * {@code resources/templates/list} request, use the {@link MetaField} annotation.
 *
 * @see Resource
 * @see ResourceResponse
 * @see ResourceContentsEncoder
 * @see MetaField
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ResourceTemplate {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    /**
     * Each resource template must have a unique name.
     * <p>
     * Intended for programmatic or logical use, but used for UI in past specs or as fallback if title isn't present.
     * <p>
     * By default, the name is derived from the name of the annotated method.
     */
    String name() default ELEMENT_NAME;

    /**
     * A human-readable name for this resource template.
     */
    String title() default "";

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

    /**
     * Optional annotations for the client.
     * <p>
     * Note that the default value of this annotation member is ignored. In other words, the annotations have to be declared
     * explicitly in order to be included in Resource metadata.
     */
    Annotations annotations() default @Annotations(audience = Role.USER, lastModified = "", priority = 0.5);

}
