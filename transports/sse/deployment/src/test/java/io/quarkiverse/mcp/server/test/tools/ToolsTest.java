package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyTools.class));

    @Test
    public void testTools() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .toolsList(page -> {
                    assertEquals(8, page.size());

                    ToolInfo alpha = page.findByName("alpha");
                    assertNull(alpha.title());
                    JsonObject schema = alpha.inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject priceProperty = properties.getJsonObject("price");
                    assertNotNull(priceProperty);
                    assertEquals("integer", priceProperty.getString("type"));
                    assertEquals("Define the price...", priceProperty.getString("description"));
                    assertTrue(schema.getJsonArray("required").isEmpty());

                    ToolInfo uniAlpha = page.findByName("uni_alpha");
                    assertEquals("Uni Alpha!", uniAlpha.title());
                    schema = uniAlpha.inputSchema();
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
