package io.quarkiverse.mcp.server.websocket.test.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpExtensionSetting;
import io.quarkiverse.mcp.server.MetaField.Type;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

/**
 * Basic smoke test proving that MCP extensions are advertised and their custom methods are dispatched over the WebSocket
 * transport.
 */
public class ExtensionMethodTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(SkillsExtension.class));

    @Test
    public void testExtension() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .build()
                .connect(initResult -> {
                    ServerCapability extensions = initResult.capabilities().stream()
                            .filter(c -> c.name().equals("extensions"))
                            .findFirst()
                            .orElse(null);
                    assertNotNull(extensions, "The extensions capability should be advertised");
                    assertEquals(Map.of("directoryRead", Boolean.TRUE),
                            extensions.properties().get("io.modelcontextprotocol/skills"));
                });
        try (client) {
            client.when()
                    .message(client.newRequest("skills/get").put("params", new JsonObject().put("uri", "code-review")))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertNotNull(result);
                        assertEquals("code-review", result.getString("uri"));
                        assertEquals("Skill for code-review", result.getString("description"));
                    })
                    .send()
                    .thenAssertResults();
        }
    }

    @McpExtension(id = "io.modelcontextprotocol/skills")
    @McpExtensionSetting(name = "directoryRead", type = Type.BOOLEAN, value = "true")
    public static class SkillsExtension {

        @McpExtensionMethod("skills/get")
        public Skill get(String uri) {
            return new Skill(uri, "Skill for " + uri);
        }
    }

    public record Skill(String uri, String description) {
    }

}
