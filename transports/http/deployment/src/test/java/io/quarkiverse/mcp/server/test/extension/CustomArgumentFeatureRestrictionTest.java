package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Set;

import jakarta.inject.Singleton;

import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.FeatureArgumentProvider;
import io.quarkiverse.mcp.server.FeatureManager.RequestFeatureArguments;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.deployment.FeatureArgumentProviderBuildItem;
import io.quarkiverse.mcp.server.runtime.Feature;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A custom argument provider registered only for {@link Feature#TOOL} must not be injectable into a {@link Prompt} method.
 */
public class CustomArgumentFeatureRestrictionTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyPrompts.class, Greeter.class, GreeterProvider.class))
            // The provider is restricted to @Tool methods only
            .addBuildChainCustomizer(buildChainBuilder -> buildChainBuilder.addBuildStep(context -> {
                context.produce(new FeatureArgumentProviderBuildItem(
                        DotName.createSimple(Greeter.class.getName()),
                        GreeterProvider.class.getName(),
                        Set.of(Feature.TOOL)));
            }).produces(FeatureArgumentProviderBuildItem.class).build())
            // ...but it's declared on a @Prompt method
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public record Greeter(String greeting) {
    }

    @Singleton
    public static class GreeterProvider implements FeatureArgumentProvider<Greeter> {

        @Override
        public Greeter provide(RequestFeatureArguments arguments) {
            return new Greeter("Hello from " + arguments.connection().serverName());
        }
    }

    public static class MyPrompts {

        @Prompt
        PromptMessage greet(Greeter greeter) {
            return PromptMessage.withUserRole(greeter.greeting());
        }
    }
}
