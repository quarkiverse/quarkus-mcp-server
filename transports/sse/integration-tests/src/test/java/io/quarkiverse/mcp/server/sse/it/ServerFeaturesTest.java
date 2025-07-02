package io.quarkiverse.mcp.server.sse.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;

@QuarkusTest
class ServerFeaturesTest {

    @TestHTTPResource
    URI testUri;

    @BeforeEach
    void setBaseUri() {
        McpAssured.baseUri = testUri;
    }

    @Test
    void testPrompt() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .promptsList(p -> {
                    assertEquals(1, p.size());
                    PromptInfo codeAssist = p.findByName("code_assist");
                    assertEquals(1, codeAssist.arguments().size());
                    assertEquals("lang", codeAssist.arguments().get(0).name());
                    assertTrue(codeAssist.arguments().get(0).required());

                })
                .promptsGet("code_assist", Map.of("lang", "java"), r -> {
                    assertEquals("System.out.println(\"Hello world!\");", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    void testTool() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(p -> {
                    assertEquals(1, p.size());
                    JsonObject schema = p.findByName("toLowerCase").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject valueProperty = properties.getJsonObject("value");
                    assertNotNull(valueProperty);
                    assertEquals("string", valueProperty.getString("type"));

                })
                .toolsCall("toLowerCase", Map.of("value", "LooP"), r -> {
                    assertEquals("loop", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Test
    void testResource() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesList(p -> {
                    assertEquals(1, p.size());
                    assertEquals("alpha", p.findByUri("file:///project/alpha").name());
                })
                .resourcesRead("file:///project/alpha", r -> {
                    assertEquals(Base64.getMimeEncoder().encodeToString("data".getBytes()),
                            r.contents().get(0).asBlob().blob());
                })
                .thenAssertResults();
    }

}
