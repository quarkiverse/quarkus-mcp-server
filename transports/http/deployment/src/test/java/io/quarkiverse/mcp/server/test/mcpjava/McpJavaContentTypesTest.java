package io.quarkiverse.mcp.server.test.mcpjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class McpJavaContentTypesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(McpJavaContentTypesFeatures.class));

    @Test
    public void testImageContent() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("imageContent",
                        r -> {
                            Content content = r.firstContent();
                            assertEquals(Content.Type.IMAGE, content.type());
                            assertEquals("image/png", content.asImage().mimeType());
                            String expectedBase64 = Base64.getEncoder()
                                    .encodeToString("IMG".getBytes(StandardCharsets.UTF_8));
                            assertEquals(expectedBase64, content.asImage().data());
                        })
                .thenAssertResults();
    }

    @Test
    public void testAudioContent() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("audioContent",
                        r -> {
                            Content content = r.firstContent();
                            assertEquals(Content.Type.AUDIO, content.type());
                            assertEquals("audio/wav", content.asAudio().mimeType());
                            String expectedBase64 = Base64.getEncoder()
                                    .encodeToString("AUDIO".getBytes(StandardCharsets.UTF_8));
                            assertEquals(expectedBase64, content.asAudio().data());
                        })
                .thenAssertResults();
    }

    @Test
    public void testTextEmbeddedResource() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("textEmbeddedResource",
                        r -> {
                            Content content = r.firstContent();
                            assertEquals(Content.Type.RESOURCE, content.type());
                            assertEquals("embedded text", content.asResource().resource().asText().text());
                            assertEquals("file:///embedded.txt", content.asResource().resource().asText().uri());
                        })
                .thenAssertResults();
    }

    @Test
    public void testResourceLink() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("resourceLink",
                        r -> {
                            Content content = r.firstContent();
                            assertEquals(Content.Type.RESOURCE_LINK, content.type());
                            assertEquals("file:///resource.md", content.asResourceLink().uri());
                            assertEquals("myResource", content.asResourceLink().name());
                            assertEquals("My Resource", content.asResourceLink().title());
                            assertEquals("A linked resource", content.asResourceLink().description());
                            assertEquals("text/markdown", content.asResourceLink().mimeType());
                            assertEquals(512, content.asResourceLink().size());
                        })
                .thenAssertResults();
    }

    @Test
    public void testErrorResponse() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("errorResponse", Map.of("msg", "something failed"),
                        r -> {
                            assertTrue(r.isError());
                            assertEquals("something failed", r.firstContent().asText().text());
                        })
                .thenAssertResults();
    }

    @Test
    public void testStructuredResponse() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("structuredResponse",
                        r -> {
                            assertEquals("result text", r.firstContent().asText().text());
                            assertNotNull(r.structuredContent());
                        })
                .thenAssertResults();
    }

    @Test
    public void testBlobResource() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .resourcesRead("file:///mcpjava/blob",
                        r -> {
                            assertEquals(1, r.contents().size());
                            assertNotNull(r.contents().get(0).asBlob());
                            String expectedBase64 = Base64.getEncoder()
                                    .encodeToString("BLOB".getBytes(StandardCharsets.UTF_8));
                            assertEquals(expectedBase64, r.contents().get(0).asBlob().blob());
                        })
                .thenAssertResults();
    }
}
