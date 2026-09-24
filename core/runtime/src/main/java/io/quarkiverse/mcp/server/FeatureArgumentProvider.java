package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.FeatureManager.RequestFeatureArguments;

/**
 * Supplies the value of a custom feature method parameter.
 * <p>
 * This is an extension point that makes it possible for an MCP extension to contribute to the list of injectable parameters of
 * a feature method (such as a {@link Tool}, {@link Prompt} or {@link Resource} method). A typical use case is an extension that
 * needs to inject a helper object built from the current request context, e.g.
 *
 * <pre>
 * &#64;Tool
 * Uni&lt;ToolResponse&gt; runPipeline(String repo, Tasks tasks) { // Tasks is injected by an MCP extension
 *     // ...
 * }
 * </pre>
 * <p>
 * An extension registers a provider for a specific parameter type by producing a {@code FeatureArgumentProviderBuildItem} from
 * its
 * own build step.
 * <p>
 * Implementations must be CDI beans, or declare a public no-args constructor. In case of CDI, there must be exactly one bean
 * that has the implementation class in its set of bean types, otherwise the build fails. Qualifiers are ignored. Furthermore,
 * the context of the bean must be active during execution. If the scope is {@link jakarta.enterprise.context.Dependent} then
 * the bean instance is reused for all invocations.
 *
 * @param <T> the type of the injected argument
 */
public interface FeatureArgumentProvider<T> {

    /**
     * Produces the value that should be injected as the argument.
     *
     * @param arguments the arguments of the current request (never {@code null})
     * @return the argument value (may be {@code null})
     */
    T provide(RequestFeatureArguments arguments);

}
