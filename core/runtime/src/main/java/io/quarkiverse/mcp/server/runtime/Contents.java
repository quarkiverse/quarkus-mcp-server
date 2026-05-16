package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceLink;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolResultContent;
import io.quarkiverse.mcp.server.ToolUseContent;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class Contents {

    public static Content parseContent(JsonObject content) {
        Content.Type contentType = Content.Type.valueOf(content.getString("type").toUpperCase());
        return switch (contentType) {
            case TEXT -> new TextContent(content.getString("text"), parseMeta(content), parseAnnotations(content));
            case IMAGE -> new ImageContent(content.getString("data"), content.getString("mimeType"), parseMeta(content),
                    parseAnnotations(content));
            case AUDIO -> new AudioContent(content.getString("data"), content.getString("mimeType"), parseMeta(content),
                    parseAnnotations(content));
            case RESOURCE -> new EmbeddedResource(parseResourceContents(content.getJsonObject("resource")), parseMeta(content),
                    parseAnnotations(content));
            case RESOURCE_LINK -> new ResourceLink(content.getString("uri"), content.getString("mimeType"),
                    content.getString("name"), content.getString("title"), content.getString("description"),
                    content.getInteger("size"), parseMeta(content), parseAnnotations(content));
            case TOOL_USE -> new ToolUseContent(content.getString("id"), content.getString("name"),
                    content.getJsonObject("input") != null ? content.getJsonObject("input").getMap() : Map.of(),
                    parseMeta(content));
            case TOOL_RESULT -> new ToolResultContent(content.getString("toolUseId"),
                    parseContentBlocks(content.getValue("content")),
                    content.getJsonObject("structuredContent") != null ? content.getJsonObject("structuredContent").getMap()
                            : null,
                    content.containsKey("isError") ? content.getBoolean("isError") : null, parseMeta(content));
            default -> throw new IllegalArgumentException("Unexpected value: " + contentType);
        };
    }

    public static List<Content> parseSamplingMessageContents(Object value) {
        if (value instanceof JsonObject json) {
            return List.of(parseContent(json));
        }
        if (value instanceof JsonArray array) {
            List<Content> ret = new ArrayList<>(array.size());
            for (Object item : array) {
                ret.add(parseContent((JsonObject) item));
            }
            return List.copyOf(ret);
        }
        throw new IllegalStateException("Unsupported sampling content: " + value);
    }

    static List<Content> parseContentBlocks(Object value) {
        List<Content> ret = parseSamplingMessageContents(value);
        for (Content item : ret) {
            if (item instanceof ToolUseContent || item instanceof ToolResultContent) {
                throw new IllegalStateException("Tool result content may contain only plain content blocks");
            }
        }
        return ret;
    }

    public static ResourceContents parseResourceContents(JsonObject resourceContent) {
        if (resourceContent.containsKey("text")) {
            return new TextResourceContents(resourceContent.getString("uri"), resourceContent.getString("text"),
                    resourceContent.getString("mimeType"), parseMeta(resourceContent));
        } else if (resourceContent.containsKey("blob")) {
            return new BlobResourceContents(resourceContent.getString("uri"), resourceContent.getString("blob"),
                    resourceContent.getString("mimeType"), parseMeta(resourceContent));
        } else {
            throw new IllegalStateException("Unsupported resource content type");
        }
    }

    public static Map<MetaKey, Object> parseMeta(JsonObject json) {
        JsonObject meta = json.getJsonObject("_meta");
        if (meta == null) {
            return null;
        }
        Map<MetaKey, Object> ret = new HashMap<>();
        for (Entry<String, Object> e : meta) {
            ret.put(MetaKey.from(e.getKey()), e.getValue());
        }
        return ret;
    }

    public static Content.Annotations parseAnnotations(JsonObject json) {
        JsonObject annotations = json.getJsonObject("annotations");
        if (annotations == null) {
            return null;
        }
        return new Content.Annotations(
                annotations.containsKey("audience") ? Role.valueOf(annotations.getString("audience").toUpperCase()) : null,
                annotations.getString("lastModified"), annotations.getDouble("priority"));
    }

}
