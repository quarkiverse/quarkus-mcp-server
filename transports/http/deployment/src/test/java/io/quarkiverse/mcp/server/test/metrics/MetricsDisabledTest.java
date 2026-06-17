package io.quarkiverse.mcp.server.test.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class MetricsDisabledTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest test = defaultConfig()
            .withApplicationRoot(root -> root
                    .addClasses(MyFeatures.class));

    @Inject
    MeterRegistry registry;

    @BeforeAll
    static void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @Test
    void testMetricsDisabledByDefault() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("alpha", toolResponse -> {
                    assertEquals("20", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();

        assertNull(registry.find("mcp.server.connections.active").gauge());
        assertNull(registry.find("mcp.server.requests.tools.call").timer());
    }

    public static class MyFeatures {

        @Tool
        String alpha(@ToolArg(defaultValue = "10") int price) {
            return "" + price * 2;
        }
    }

}
