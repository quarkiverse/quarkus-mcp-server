package io.quarkiverse.mcp.server.test.mcpjava;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.mcpjava.server.content.AudioContent;
import org.mcpjava.server.content.EmbeddedResource;
import org.mcpjava.server.content.ImageContent;
import org.mcpjava.server.content.ResourceLink;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mcpjava.server.tools.ToolResponse;

public class McpJavaContentTypesFeatures {

    @Tool(description = "Returns ImageContent")
    ImageContent imageContent() {
        return ImageContent.of("IMG".getBytes(StandardCharsets.UTF_8), "image/png");
    }

    @Tool(description = "Returns AudioContent")
    AudioContent audioContent() {
        return AudioContent.of("AUDIO".getBytes(StandardCharsets.UTF_8), "audio/wav");
    }

    @Tool(description = "Returns text EmbeddedResource")
    EmbeddedResource textEmbeddedResource() {
        return EmbeddedResource.builder("embedded text", "file:///embedded.txt")
                .setMimeType("text/plain")
                .build();
    }

    @Tool(description = "Returns ResourceLink")
    ResourceLink resourceLink() {
        return ResourceLink.builder("myResource", "file:///resource.md")
                .setTitle("My Resource")
                .setDescription("A linked resource")
                .setMimeType("text/markdown")
                .setSize(512)
                .build();
    }

    @Tool(description = "Returns error ToolResponse")
    ToolResponse errorResponse(@ToolArg(description = "Error message") String msg) {
        return ToolResponse.ofError(msg);
    }

    @Tool(description = "Returns ToolResponse with structured content")
    ToolResponse structuredResponse() {
        return ToolResponse.builder()
                .addTextContent("result text")
                .setStructuredContent(Map.of("status", "ok"))
                .build();
    }

    @Resource(uri = "file:///mcpjava/blob", title = "Blob Resource")
    ResourceResponse blobResource() {
        return ResourceResponse.of("file:///mcpjava/blob",
                "BLOB".getBytes(StandardCharsets.UTF_8));
    }
}
