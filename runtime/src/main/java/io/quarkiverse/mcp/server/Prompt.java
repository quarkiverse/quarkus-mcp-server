package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import io.smallrye.mutiny.Uni;

/**
 * Annotates a business method of a CDI bean as an exposed prompt template.
 * <p>
 * A result of a "prompt get" is always represented as a {@link PromptResponse}. However, the annotated method can also return
 * other types that are converted according to the following rules.
 * <ul>
 * <li>If the method returns a {@link PromptMessage} then the reponse has no description and contains the single
 * message object.</li>
 * <li>If the method returns a {@link List} of {@link PromptMessage}s then the reponse has no description and contains the
 * list of messages.</li>
 * <li>The method may return a {@link Uni} that wraps any of the type mentioned above.</li>
 * </ul>
 * In other words, the return type must be one of the following list:
 * <ul>
 * <li>{@code PromptResponse}</li>
 * <li>{@code PromptMessage}</li>
 * <li>{@code List<PromptMessage>}</li>
 * <li>{@code Uni<PromptResponse>}</li>
 * <li>{@code Uni<PromptMessage>}</li>
 * <li>{@code Uni<List<PromptMessage>>}</li>
 * </ul>
 *
 * @see PromptResponse
 * @see PromptArg
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Prompt {

    /**
     * Constant value for {@link #name()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    /**
     *
     */
    String name() default ELEMENT_NAME;

    /**
     *
     */
    String description() default "";

}
