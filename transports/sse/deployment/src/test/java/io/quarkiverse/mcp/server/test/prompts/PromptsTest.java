package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkus.test.QuarkusUnitTest;

public class PromptsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyPrompts.class));

    @Test
    public void testPrompts() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .promptsList(page -> {
                    assertEquals(6, page.size());
                    PromptInfo bar = page.findByName("BAR");
                    assertEquals(1, bar.arguments().size());
                    assertEquals("val", bar.arguments().get(0).name());
                    PromptInfo foo = page.findByName("foo");
                    assertEquals("Not much we can say here.", foo.description());
                    assertEquals(2, foo.arguments().size());
                })
                .promptsGet("foo", Map.of("name", "Lu", "repeat", "1"), r -> {
                    assertEquals("Hello Lu!", r.messages().get(0).content().asText().text());
                })
                .promptsGet("BAR", Map.of("val", "Jachym"), r -> {
                    assertEquals("JACHYM", r.messages().get(0).content().asText().text());
                })
                .promptsGet("uni_bar", Map.of("val", "Vojtech"), r -> {
                    assertEquals("VOJTECH", r.messages().get(0).content().asText().text());
                })
                .promptsGet("uni_list_bar", Map.of("val", "Ondrej"), r -> {
                    assertEquals("ONDREJ", r.messages().get(0).content().asText().text());
                })
                .promptsGet("response", Map.of("val", "Martin"), r -> {
                    assertEquals("MARTIN", r.messages().get(0).content().asText().text());
                })
                .promptsGet("uni_response", Map.of("val", "Martin"), r -> {
                    assertEquals("MARTIN", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();
    }

}
