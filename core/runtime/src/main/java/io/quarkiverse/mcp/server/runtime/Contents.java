package io.quarkiverse.mcp.server.runtime;

import java.util.HashMap;
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
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.vertx.core.json.JsonObject;

public final class Contents {

    public static Content parseContent(JsonObject content) {
        Content.Type contentType = Content.Type.valueOf(content.getString("type").toUpperCase());
        return switch (contentType) {
            case TEXT -> new TextContent(content.getString("text"), parseMeta(content));
            case IMAGE -> new ImageContent(content.getString("data"), content.getString("mimeType"), parseMeta(content));
            case AUDIO -> new AudioContent(content.getString("data"), content.getString("mimeType"), parseMeta(content));
            case RESOURCE -> new EmbeddedResource(parseResourceContents(content.getJsonObject("resource")), parseMeta(content));
            case RESOURCE_LINK -> new ResourceLink(content.getString("uri"), content.getString("mimeType"),
                    content.getString("name"), content.getString("title"), content.getString("description"),
                    content.getInteger("size"), parseMeta(content));
            default -> throw new IllegalArgumentException("Unexpected value: " + contentType);
        };
    }

    public static ResourceContents parseResourceContents(JsonObject resourceContent) {
        if (resourceContent.containsKey("text")) {
            return new TextResourceContents(resourceContent.getString("uri"), resourceContent.getString("text"),
                    resourceContent.getString("mime"));
        } else if (resourceContent.containsKey("blob")) {
            return new BlobResourceContents(resourceContent.getString("uri"), resourceContent.getString("blob"),
                    resourceContent.getString("mime"));
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

}
