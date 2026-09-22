package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.FeatureArgumentProvider;
import io.quarkiverse.mcp.server.FeatureManager.RequestFeatureArguments;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.deployment.FeatureArgumentProviderBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A parameter type must be registered by exactly one custom argument provider, otherwise the build fails.
 */
public class CustomArgumentDuplicateProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, Greeter.class, GreeterProvider.class,
                    OtherGreeterProvider.class))
            // Two providers registered for the same parameter type
            .addBuildChainCustomizer(buildChainBuilder -> buildChainBuilder.addBuildStep(context -> {
                DotName greeter = DotName.createSimple(Greeter.class.getName());
                context.produce(new FeatureArgumentProviderBuildItem(greeter, GreeterProvider.class.getName()));
                context.produce(new FeatureArgumentProviderBuildItem(greeter, OtherGreeterProvider.class.getName()));
            }).produces(FeatureArgumentProviderBuildItem.class).build())
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
            return new Greeter("Hello");
        }
    }

    @Singleton
    public static class OtherGreeterProvider implements FeatureArgumentProvider<Greeter> {

        @Override
        public Greeter provide(RequestFeatureArguments arguments) {
            return new Greeter("Hi");
        }
    }

    public static class MyTools {

        @Tool
        String greet(String name, Greeter greeter) {
            return greeter.greeting() + ", " + name + "!";
        }
    }
}
