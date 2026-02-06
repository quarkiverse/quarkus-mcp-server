package io.quarkiverse.mcp.server.test.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class MetricsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest test = defaultConfig()
            .withApplicationRoot(root -> root
                    .addClasses(MyFeatures.class))
            .overrideConfigKey("quarkus.mcp.server.metrics.enabled", "true");

    @Inject
    MeterRegistry registry;

    @BeforeAll
    static void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @Test
    void testMetrics() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("alpha", toolResponse -> {
                    assertEquals("20", toolResponse.firstContent().asText().text());
                })
                .toolsCall("alpha", Map.of("price", 1), toolResponse -> {
                    assertEquals("2", toolResponse.firstContent().asText().text());
                })
                .toolsCall("bravo", toolResponse -> {
                    assertEquals("20", toolResponse.firstContent().asText().text());
                })
                .promptsGet("charlie", promptResponse -> {
                    assertEquals("ok", promptResponse.firstMessage().content().asText().text());
                })
                .promptsList(page -> {
                    assertEquals(1, page.size());
                })
                .toolsList(page -> {
                    assertEquals(2, page.size());
                })
                .resourcesTemplatesList(page -> {
                    assertEquals(1, page.size());
                })
                .thenAssertResults();

        assertEquals(1, registry.find("mcp.server.connections.active").gauge().value());

        assertTimer("mcp.server.requests.tools.call", timer -> {
            assertNotNull(timer);
            assertEquals(2, timer.count());
        }, "tool.name", "alpha");

        assertTimer("mcp.server.requests.tools.call", timer -> {
            assertNotNull(timer);
            assertEquals(1, timer.count());
        }, "tool.name", "bravo");

        assertTimer("mcp.server.requests.tools.list", timer -> {
            assertEquals(1, timer.count());
        });

        assertTimer("mcp.server.requests.prompts.get", timer -> {
            assertNotNull(timer);
            assertEquals(1, timer.count());
        }, "prompt.name", "charlie");

        assertTimer("mcp.server.requests.prompts.list", timer -> {
            assertEquals(1, timer.count());
        });

        assertTimer("mcp.server.requests.resources.templates.list", timer -> {
            assertEquals(1, timer.count());
        });
    }

    private void assertTimer(String name, Consumer<Timer> assertion, String... tags) throws InterruptedException {
        Search search = registry.find(name);
        if (tags.length > 0) {
            search.tags(tags);
        }
        int i = 0;
        while (search.timer() == null && i++ < 10) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        Timer found = search.timer();
        if (found == null) {
            fail("Timer not found");
        }
        assertion.accept(found);
    }

    public static class MyFeatures {

        @Tool
        String alpha(@ToolArg(defaultValue = "10") int price) {
            return "" + price * 2;
        }

        @Tool
        String bravo(@ToolArg(defaultValue = "10") int price) {
            return "" + price * 2;
        }

        @Prompt
        PromptResponse charlie() {
            return PromptResponse.withMessages(PromptMessage.withUserRole("ok"));
        }

        @Resource(uri = "file:///project/delta")
        TextResourceContents delta(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "3");
        }

        @ResourceTemplate(uriTemplate = "file:///{path}")
        TextResourceContents echo(String path, RequestUri uri) {
            return TextResourceContents.create(uri.value(), "foo:" + path);
        }

    }

}
