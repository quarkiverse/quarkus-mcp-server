package io.quarkiverse.mcp.server.test.tools.contents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.Content.Type;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.ResourceLink;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolContentsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testContents() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("alpha", toolResponse -> {
                    assertEquals(5, toolResponse.content().size());
                    for (Content c : toolResponse.content()) {
                        if (c.type() == Type.TEXT) {
                            assertEquals("bravo", c.asText().text());
                        } else if (c.type() == Type.IMAGE) {
                            assertEquals(Base64.getEncoder().encodeToString("hello".getBytes()), c.asImage().data());
                            assertEquals("img/png", c.asImage().mimeType());
                        } else if (c.type() == Type.RESOURCE) {
                            assertEquals("file://hi", c.asResource().resource().asText().uri());
                            assertEquals("hi", c.asResource().resource().asText().text());
                        } else if (c.type() == Type.AUDIO) {
                            assertEquals(Base64.getEncoder().encodeToString("hello".getBytes()), c.asAudio().data());
                            assertEquals("audio/wav", c.asAudio().mimeType());
                        } else if (c.type() == Type.RESOURCE_LINK) {
                            assertEquals("file://link", c.asResourceLink().uri());
                            assertEquals("link", c.asResourceLink().name());
                        }
                    }
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        ToolResponse alpha() {
            return ToolResponse.success(
                    new TextContent("bravo"),
                    new ImageContent(Base64.getEncoder().encodeToString("hello".getBytes()), "img/png"),
                    new EmbeddedResource(new TextResourceContents("file://hi", "hi", null)),
                    new AudioContent(Base64.getEncoder().encodeToString("hello".getBytes()), "audio/wav"),
                    new ResourceLink("file://link", "link"));
        }

    }

}
