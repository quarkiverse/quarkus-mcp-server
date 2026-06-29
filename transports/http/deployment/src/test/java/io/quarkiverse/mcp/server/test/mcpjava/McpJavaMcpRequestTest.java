package io.quarkiverse.mcp.server.test.mcpjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class McpJavaMcpRequestTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(McpJavaMcpRequestFeatures.class));

    @Test
    public void testRequestInfo() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("requestInfo",
                        r -> {
                            String text = r.firstContent().asText().text();
                            assertTrue(text.contains("session:"), text);
                            assertTrue(!text.contains("session:none"), text);
                            assertTrue(text.contains("protocol:"), text);
                            assertTrue(text.contains("client:"), text);
                            assertTrue(text.contains("title:"), text);
                        })
                .thenAssertResults();
    }

    @Test
    public void testRequestId() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("requestId",
                        r -> {
                            String text = r.firstContent().asText().text();
                            assertTrue(text.startsWith("id:"), text);
                            String idPart = text.substring("id:".length());
                            assertTrue(!idPart.isEmpty() && !idPart.equals("null"), text);
                        })
                .thenAssertResults();
    }

    @Test
    public void testRequestMetadata() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("requestMetadata")
                .withArguments(Map.of("key", "myKey"))
                .withMetadata(Map.of("myKey", "myValue"))
                .withAssert(r -> {
                    String text = r.firstContent().asText().text();
                    assertEquals("meta:myValue", text);
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testRequestMetadataAbsent() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("requestMetadata")
                .withArguments(Map.of("key", "missing"))
                .withAssert(r -> {
                    String text = r.firstContent().asText().text();
                    assertEquals("meta:null", text);
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testClientCapabilities() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(new ClientCapability("roots", Map.of()), new ClientCapability("sampling", Map.of()))
                .build()
                .connect();

        client.when()
                .toolsCall("clientCapabilities",
                        r -> {
                            String text = r.firstContent().asText().text();
                            assertTrue(text.contains("roots"), text);
                            assertTrue(text.contains("sampling"), text);
                        })
                .thenAssertResults();
    }

    @Test
    public void testClientInfoDetails() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setTitle("Test Title")
                .setDescription("Test Description")
                .setWebsiteUrl("https://example.com")
                .setIcons(new Icon("https://example.com/icon.png", "image/png",
                        List.of("16x16"), Icon.Theme.DARK))
                .build()
                .connect();

        client.when()
                .toolsCall("clientInfoDetails",
                        r -> {
                            String text = r.firstContent().asText().text();
                            assertTrue(text.contains("title:Test Title"), text);
                            assertTrue(text.contains("description:Test Description"), text);
                            assertTrue(text.contains("websiteUrl:https://example.com"), text);
                            assertTrue(text.contains("icons:1"), text);
                            assertTrue(text.contains("icon0.src:https://example.com/icon.png"), text);
                            assertTrue(text.contains("icon0.mimeType:image/png"), text);
                            assertTrue(text.contains("icon0.theme:DARK"), text);
                        })
                .thenAssertResults();
    }

    @Test
    public void testClientInfoDetailsMinimalIcon() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setIcons(new Icon("https://example.com/icon.png", "image/png", List.of(), null))
                .build()
                .connect();

        client.when()
                .toolsCall("clientInfoDetails",
                        r -> {
                            String text = r.firstContent().asText().text();
                            assertTrue(text.contains("icons:1"), text);
                            assertTrue(text.contains("icon0.src:https://example.com/icon.png"), text);
                            assertTrue(text.contains("icon0.sizes:[]"), text);
                            assertTrue(text.contains("icon0.theme:none"), text);
                        })
                .thenAssertResults();
    }

    @Test
    public void testClientInfoDetailsNoOptionals() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("clientInfoDetails",
                        r -> {
                            String text = r.firstContent().asText().text();
                            assertTrue(text.contains("description:none"), text);
                            assertTrue(text.contains("websiteUrl:none"), text);
                        })
                .thenAssertResults();
    }
}
