package io.quarkiverse.mcp.server.runtime.mcpjava;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mcpjava.server.content.ContentBlock;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceLink;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;

/**
 * Converts java-mcp-annotations types to Quarkus MCP Server types.
 */
public class McpJavaTypeConverter {

    public static Content convertContentBlock(ContentBlock block) {
        if (block instanceof org.mcpjava.server.content.TextContent tc) {
            return new TextContent(tc.text(), convertMetadata(tc.metadata()),
                    convertAnnotations(tc.annotations().orElse(null)));
        } else if (block instanceof org.mcpjava.server.content.ImageContent ic) {
            return new ImageContent(Base64.getEncoder().encodeToString(ic.data()), ic.mimeType(),
                    convertMetadata(ic.metadata()),
                    convertAnnotations(ic.annotations().orElse(null)));
        } else if (block instanceof org.mcpjava.server.content.AudioContent ac) {
            return new AudioContent(Base64.getEncoder().encodeToString(ac.data()), ac.mimeType(),
                    convertMetadata(ac.metadata()),
                    convertAnnotations(ac.annotations().orElse(null)));
        } else if (block instanceof org.mcpjava.server.content.EmbeddedResource er) {
            return new EmbeddedResource(convertResourceContents(er.resource()), convertMetadata(er.metadata()),
                    convertAnnotations(er.annotations().orElse(null)));
        } else if (block instanceof org.mcpjava.server.content.ResourceLink rl) {
            return new ResourceLink(rl.uri(), rl.mimeType().orElse(null), rl.name(), rl.title(),
                    rl.description().orElse(null), rl.size().isPresent() ? (int) rl.size().getAsLong() : null,
                    convertMetadata(rl.metadata()),
                    convertAnnotations(rl.annotations().orElse(null)));
        }
        throw new IllegalArgumentException("Unknown ContentBlock type: " + block.getClass());
    }

    public static ResourceContents convertResourceContents(org.mcpjava.server.resources.ResourceContents rc) {
        if (rc instanceof org.mcpjava.server.resources.TextResourceContents trc) {
            return new TextResourceContents(trc.uri(), trc.text(), trc.mimeType().orElse(null),
                    convertMetadata(trc.metadata()));
        } else if (rc instanceof org.mcpjava.server.resources.BlobResourceContents brc) {
            return new BlobResourceContents(brc.uri(), Base64.getEncoder().encodeToString(brc.blob()),
                    brc.mimeType().orElse(null), convertMetadata(brc.metadata()));
        }
        throw new IllegalArgumentException("Unknown ResourceContents type: " + rc.getClass());
    }

    public static PromptMessage convertPromptMessage(org.mcpjava.server.prompts.PromptMessage pm) {
        return new PromptMessage(convertRole(pm.role()), convertContentBlock(pm.content()));
    }

    public static Content.Annotations convertAnnotations(org.mcpjava.server.content.Annotations ann) {
        if (ann == null) {
            return null;
        }
        List<Role> audience = ann.audience()
                .map(roles -> roles.stream().map(McpJavaTypeConverter::convertRole).toList())
                .orElse(null);
        String lastModified = ann.lastModified().map(Instant::toString).orElse(null);
        Double priority = ann.priority().isPresent() ? ann.priority().getAsDouble() : null;
        return new Content.Annotations(audience, lastModified, priority);
    }

    public static Role convertRole(org.mcpjava.server.Role role) {
        return Role.valueOf(role.name());
    }

    public static Map<MetaKey, Object> convertMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<MetaKey, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            result.put(MetaKey.from(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public static List<Content> convertContentBlocks(List<ContentBlock> blocks) {
        return blocks.stream().map(McpJavaTypeConverter::convertContentBlock).toList();
    }

    public static List<ResourceContents> convertResourceContentsList(
            List<org.mcpjava.server.resources.ResourceContents> contents) {
        return contents.stream().map(McpJavaTypeConverter::convertResourceContents).toList();
    }

    private McpJavaTypeConverter() {
    }
}
