package io.quarkiverse.mcp.server.stdio.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;

public class ServerFeaturesIT {

    @Test
    public void testPrompt() {
        try (McpStdioTestClient client = McpAssured.newConnectedStdioClient()) {
            client.when()
                    .promptsList(page -> {
                        assertEquals(1, page.size());
                        var prompt = page.findByName("code_assist");
                        assertNotNull(prompt);
                        assertEquals(1, prompt.arguments().size());
                        assertEquals("lang", prompt.arguments().get(0).name());
                        assertTrue(prompt.arguments().get(0).required());
                    })
                    .promptsGet("code_assist", Map.of("lang", "java"), response -> {
                        assertEquals(1, response.messages().size());
                        var message = response.messages().get(0);
                        assertEquals("System.out.println(\"Hello world!\");", message.content().asText().text());
                    })
                    .thenAssertResults();

            // Assert that quarkus log is redirected to stderr by default
            assertFalse(client.stderrLines().isEmpty());
            assertTrue(client.stderrLines().stream().anyMatch(l -> l.contains("Log from code assist...")));
        }
    }

    @Test
    public void testTool() {
        try (McpStdioTestClient client = McpAssured.newConnectedStdioClient()) {
            client.when()
                    .toolsList(page -> {
                        assertEquals(1, page.size());
                        var tool = page.findByName("toLowerCase");
                        assertNotNull(tool);
                        assertNotNull(tool.inputSchema());
                        assertEquals(1, tool.inputSchema().getJsonObject("properties").size());
                    })
                    .thenAssertResults();

            // tools/call triggers a tool registration and a list_changed notification
            client.when()
                    .toolsCall("toLowerCase", Map.of("value", "LooP"), response -> {
                        assertFalse(response.isError());
                        assertEquals(1, response.content().size());
                        assertEquals("loop", response.content().get(0).asText().text());
                    })
                    .thenAssertResults();

            // Verify the list_changed notification was received
            client.waitForNotifications(1);
            var snapshot = client.snapshot();
            assertTrue(snapshot.notifications().stream()
                    .anyMatch(n -> "notifications/tools/list_changed".equals(n.getString("method"))));
        }
    }

    @Test
    public void testResource() {
        try (McpStdioTestClient client = McpAssured.newConnectedStdioClient()) {
            client.when()
                    .resourcesList(page -> {
                        assertEquals(1, page.size());
                        var resource = page.findByUri("file:///project/alpha");
                        assertNotNull(resource);
                        assertEquals("alpha", resource.name());
                    })
                    .resourcesRead("file:///project/alpha", response -> {
                        assertEquals(1, response.contents().size());
                        var blob = response.contents().get(0).asBlob();
                        assertEquals("file:///project/alpha", blob.uri());
                        assertEquals(Base64.getMimeEncoder().encodeToString("data".getBytes()),
                                blob.blob());
                    })
                    .thenAssertResults();
        }
    }

}
