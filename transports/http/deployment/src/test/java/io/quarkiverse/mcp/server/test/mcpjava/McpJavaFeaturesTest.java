package io.quarkiverse.mcp.server.test.mcpjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class McpJavaFeaturesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(McpJavaFeatures.class, Checks.class));

    @Test
    public void testTools() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList(page -> {
                    assertEquals(5, page.size());

                    ToolInfo alpha = page.findByName("alpha");
                    assertNull(alpha.title());
                    assertEquals("A simple tool", alpha.description());
                    JsonObject properties = alpha.inputSchema().getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject priceProperty = properties.getJsonObject("price");
                    assertNotNull(priceProperty);
                    assertEquals("integer", priceProperty.getString("type"));
                    assertEquals("The price", priceProperty.getString("description"));
                    assertTrue(alpha.inputSchema().getJsonArray("required").isEmpty());

                    ToolInfo bravo = page.findByName("bravo");
                    assertEquals("Bravo Tool", bravo.title());
                    properties = bravo.inputSchema().getJsonObject("properties");
                    assertEquals(1, properties.size());
                    assertNotNull(properties.getJsonObject("val"));

                    ToolInfo charlie = page.findByName("charlie");
                    assertTrue(charlie.annotations().isPresent());
                    assertTrue(charlie.annotations().get().readOnlyHint());
                    assertEquals(false, charlie.annotations().get().destructiveHint());

                    ToolInfo delta = page.findByName("delta");
                    assertEquals("Returns TextContent", delta.description());

                    ToolInfo echo = page.findByName("echo");
                    assertEquals("Returns ToolResponse", echo.description());
                })
                .toolsCall("alpha", Map.of("price", 42),
                        r -> assertEquals("alpha:42", r.firstContent().asText().text()))
                .toolsCall("alpha",
                        r -> assertEquals("alpha:0", r.firstContent().asText().text()))
                .toolsCall("bravo", Map.of("val", "hello"),
                        r -> assertEquals("bravo:hello", r.firstContent().asText().text()))
                .toolsCall("charlie",
                        r -> assertEquals("charlie1", r.firstContent().asText().text()))
                .toolsCall("delta", Map.of("text", "world"),
                        r -> assertEquals("delta:world", r.firstContent().asText().text()))
                .toolsCall("echo", Map.of("msg", "hi"),
                        r -> assertEquals("echo:hi", r.firstContent().asText().text()))
                .thenAssertResults();
    }

    @Test
    public void testPrompts() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .promptsList(page -> {
                    assertEquals(3, page.size());

                    PromptInfo greet = page.findByName("greet");
                    assertEquals("Greeting", greet.title());
                    assertEquals("A greeting prompt", greet.description());
                    assertEquals(1, greet.arguments().size());
                    assertEquals("name", greet.arguments().get(0).name());
                    assertEquals("Name", greet.arguments().get(0).title());
                    assertEquals("The name", greet.arguments().get(0).description());

                    PromptInfo farewell = page.findByName("farewell");
                    assertNull(farewell.title());

                    PromptInfo info = page.findByName("info");
                    assertEquals("Returns PromptResponse", info.description());
                })
                .promptsGet("greet", Map.of("name", "Martin"),
                        r -> assertEquals("Hello Martin!", r.messages().get(0).content().asText().text()))
                .promptsGet("farewell", Map.of("name", "Lu"),
                        r -> assertEquals("Goodbye Lu!", r.messages().get(0).content().asText().text()))
                .promptsGet("info", Map.of("topic", "quarkus"), r -> {
                    assertEquals(Role.ASSISTANT, r.messages().get(0).role());
                    assertEquals("info:quarkus", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    public void testResources() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .resourcesList(page -> {
                    assertEquals(3, page.size());

                    ResourceInfo alpha = page.findByUri("file:///mcpjava/alpha");
                    assertEquals("resourceAlpha", alpha.name());
                    assertEquals("Alpha Resource", alpha.title());
                    assertEquals(10, alpha.size());
                    assertNotNull(alpha.annotations());
                    assertEquals(Role.USER, alpha.annotations().audience().get(0));
                    assertEquals(0.8, alpha.annotations().priority());

                    ResourceInfo bravo = page.findByUri("file:///mcpjava/bravo");
                    assertEquals("resourceBravo", bravo.name());

                    ResourceInfo charlie = page.findByUri("file:///mcpjava/charlie");
                    assertEquals("resourceCharlie", charlie.name());
                })
                .resourcesRead("file:///mcpjava/alpha",
                        r -> assertEquals("resource-alpha", r.contents().get(0).asText().text()))
                .resourcesRead("file:///mcpjava/bravo",
                        r -> assertEquals("resource-bravo", r.contents().get(0).asText().text()))
                .resourcesRead("file:///mcpjava/charlie",
                        r -> assertEquals("resource-charlie", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @Test
    public void testResourceTemplates() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .resourcesTemplatesList(page -> {
                    assertEquals(1, page.size());

                    ResourceTemplateInfo fileTemplate = page.findByUriTemplate("file:///mcpjava/{path}");
                    assertEquals("fileTemplate", fileTemplate.name());
                    assertEquals("File Template", fileTemplate.title());
                })
                .resourcesRead("file:///mcpjava/readme.txt",
                        r -> assertEquals("file:readme.txt", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @Test
    public void testCompletions() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .promptComplete("greet", "name", "Ma", completionResponse -> {
                    assertEquals(2, completionResponse.values().size());
                    assertTrue(completionResponse.values().contains("Martin"));
                    assertTrue(completionResponse.values().contains("Matej"));
                })
                .promptComplete("info", "topic", "j", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("java", completionResponse.values().get(0));
                })
                .resourceTemplateComplete("file:///mcpjava/{path}", "path", "p", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("pom.xml", completionResponse.values().get(0));
                })
                .thenAssertResults();
    }
}
