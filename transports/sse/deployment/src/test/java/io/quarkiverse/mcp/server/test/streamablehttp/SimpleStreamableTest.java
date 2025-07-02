package io.quarkiverse.mcp.server.test.streamablehttp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkiverse.mcp.server.test.tools.MyTools;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class SimpleStreamableTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyTools.class));

    @Inject
    ToolManager toolManager;

    @Test
    public void testTools() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        // Just test that registration does not fail even if notification is not sent
        toolManager.newTool("foofoo")
                .setDescription("foofoo")
                .setHandler(atgs -> null)
                .register();
        assertNotNull(toolManager.getTool("foofoo"));
        assertNotNull(toolManager.removeTool("foofoo"));

        client.when()
                .toolsList(page -> {
                    assertEquals(8, page.size());

                    JsonObject schema = page.findByName("alpha").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject priceProperty = properties.getJsonObject("price");
                    assertNotNull(priceProperty);
                    assertEquals("integer", priceProperty.getString("type"));
                    assertEquals("Define the price...", priceProperty.getString("description"));
                    assertTrue(schema.getJsonArray("required").isEmpty());

                    schema = page.findByName("uni_alpha").inputSchema();
                    properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    priceProperty = properties.getJsonObject("uni_price");
                    assertNotNull(priceProperty);
                    assertEquals("number", priceProperty.getString("type"));
                    assertEquals(1, schema.getJsonArray("required").size());
                    assertEquals("uni_price", schema.getJsonArray("required").getString(0));

                    schema = page.findByName("charlie").inputSchema();
                    properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject dayProperty = properties.getJsonObject("day");
                    assertNotNull(dayProperty);
                    assertEquals("string", dayProperty.getString("type"));
                })
                .toolsCall("alpha", Map.of("price", 1), r -> assertEquals("Hello 1!", r.content().get(0).asText().text()))
                .toolsCall("uni_alpha", Map.of("uni_price", 1),
                        r -> assertEquals("Hello 1.0!", r.content().get(0).asText().text()))
                .toolsCall("bravo", Map.of("price", 1), r -> assertEquals("Hello 1!", r.content().get(0).asText().text()))
                .toolsCall("uni_bravo", Map.of("price", 1), r -> assertEquals("Hello 1!", r.content().get(0).asText().text()))
                .toolsCall("charlie", Map.of("day", DayOfWeek.FRIDAY),
                        r -> assertEquals("charlie1", r.content().get(0).asText().text()))
                .toolsCall("charlie", Map.of("day", DayOfWeek.MONDAY),
                        r -> assertEquals("charlie11", r.content().get(0).asText().text()))
                .toolsCall("uni_charlie", r -> assertEquals("charlie2", r.content().get(0).asText().text()))
                .toolsCall("list_charlie", r -> assertEquals("charlie3", r.content().get(0).asText().text()))
                .toolsCall("uni_list_charlie", r -> assertEquals("charlie4", r.content().get(0).asText().text()))
                .thenAssertResults();
    }

}
