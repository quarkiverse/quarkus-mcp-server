package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.FeatureArgumentProvider;
import io.quarkiverse.mcp.server.FeatureManager.RequestFeatureArguments;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.deployment.FeatureArgumentProviderBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A {@link FeatureArgumentProvider} must be a CDI bean or declare a public no-args constructor, otherwise the build fails.
 */
public class CustomArgumentInvalidProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, Greeter.class, BadProvider.class))
            .addBuildChainCustomizer(buildChainBuilder -> buildChainBuilder.addBuildStep(context -> {
                context.produce(new FeatureArgumentProviderBuildItem(
                        DotName.createSimple(Greeter.class.getName()),
                        BadProvider.class.getName()));
            }).produces(FeatureArgumentProviderBuildItem.class).build())
            // BadProvider is neither a CDI bean nor declares a public no-args constructor
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public record Greeter(String greeting) {
    }

    // Not a bean and no public no-args constructor
    public static class BadProvider implements FeatureArgumentProvider<Greeter> {

        public BadProvider(String ignored) {
        }

        @Override
        public Greeter provide(RequestFeatureArguments arguments) {
            return new Greeter("nope");
        }
    }

    public static class MyTools {

        @Tool
        String greet(String name, Greeter greeter) {
            return greeter.greeting() + ", " + name + "!";
        }
    }
}
