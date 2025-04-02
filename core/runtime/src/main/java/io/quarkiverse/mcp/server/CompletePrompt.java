package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import io.smallrye.mutiny.Uni;

/**
 * Annotates a business method of a CDI bean used to complete a prompt argument.
 * <p>
 * The result of a "complete" operation is always represented as a {@link CompletionResponse}. However, the annotated method can
 * also return other types that are converted according to the following rules.
 * <ul>
 * <li>If the method returns {@link String} then the response contains the single value.</li>
 * <li>If the method returns a {@link List} of {@link String}s then the response contains the list of values.</li>
 * <li>The method may return a {@link Uni} that wraps any of the type mentioned above.</li>
 * </ul>
 * In other words, the return type must be one of the following list:
 * <ul>
 * <li>{@code CompletionResponse}</li>
 * <li>{@code String}</li>
 * <li>{@code List<String>}</li>
 * <li>{@code Uni<CompletionResponse>}</li>
 * <li>{@code Uni<String>}</li>
 * <li>{@code Uni<List<String>>}</li>
 * </ul>
 *
 * A prompt completion method must consume exactly one {@link String} argument.
 *
 * @see Prompt#name()
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface CompletePrompt {

    /**
     * The name reference to a prompt. If not such {@link Prompt} exists then the build fails.
     */
    String value();

}
