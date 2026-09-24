package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import jakarta.inject.Singleton;

import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.FeatureArgumentProvider;
import io.quarkiverse.mcp.server.FeatureManager.RequestFeatureArguments;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.deployment.FeatureArgumentProviderBuildItem;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that an MCP extension can contribute a custom injectable parameter type of a feature method via an
 * {@link FeatureArgumentProviderBuildItem} and a {@link FeatureArgumentProvider}.
 */
public class CustomArgumentProviderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, Greeter.class, GreeterProvider.class))
            // Simulate an MCP extension that registers a custom injectable parameter type
            .addBuildChainCustomizer(buildChainBuilder -> buildChainBuilder.addBuildStep(context -> {
                context.produce(new FeatureArgumentProviderBuildItem(
                        DotName.createSimple(Greeter.class.getName()),
                        GreeterProvider.class.getName()));
            }).produces(FeatureArgumentProviderBuildItem.class).build());

    @Test
    public void testSse() {
        assertGreet(McpAssured.newConnectedSseClient());
    }

    @Test
    public void testStreamable() {
        assertGreet(McpAssured.newConnectedStreamableClient());
    }

    @Test
    public void testStreamableStateless() {
        assertGreet(McpAssured.newStreamableClient().setStateless().build().connect());
    }

    static <A extends McpAssert<A>> void assertGreet(McpTestClient<A, ?> client) {
        try (client) {
            client.when()
                    // "greeter" is injected by the GreeterProvider, only "name" is a regular argument
                    .toolsCall("greet", Map.of("name", "Lu"), r -> {
                        assertFalse(r.isError());
                        assertEquals("Hello from <default>, Lu!", r.firstContent().asText().text());
                    })
                    .thenAssertResults();
        }
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

    public static class MyTools {

        @Tool
        String greet(String name, Greeter greeter) {
            return greeter.greeting() + ", " + name + "!";
        }
    }
}
