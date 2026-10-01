package io.quarkiverse.mcp.server.tasks.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.ElicitationResponse.Action;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.Root;
import io.quarkiverse.mcp.server.SamplingResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * The {@code inputResponses} collected from the {@code tasks/update} requests of a task.
 */
final class TaskInputResponses implements InputResponses {

    private final JsonObject inputResponses;

    TaskInputResponses(JsonObject inputResponses) {
        this.inputResponses = inputResponses;
    }

    @Override
    public boolean isEmpty() {
        return inputResponses.isEmpty();
    }

    @Override
    public boolean has(String key) {
        return inputResponses.containsKey(key);
    }

    @Override
    public ElicitationResponse getElicitationResponse(String key) {
        JsonObject response = inputResponses.getJsonObject(key);
        if (response == null) {
            return null;
        }
        Action action = Action.valueOf(response.getString("action").toUpperCase());
        JsonObject content = response.getJsonObject("content");
        return new ElicitationResponse(action, new ContentImpl(content != null ? content : new JsonObject()),
                new MetaImpl(response.getJsonObject("_meta")));
    }

    @Override
    public SamplingResponse getSamplingResponse(String key) {
        JsonObject response = inputResponses.getJsonObject(key);
        if (response == null) {
            return null;
        }
        Role role = Role.valueOf(response.getString("role").toUpperCase());
        return new SamplingResponse(parseContent(response.getJsonObject("content")), response.getString("model"), role,
                response.getString("stopReason"), new MetaImpl(response.getJsonObject("_meta")));
    }

    @Override
    public List<Root> getRootsResponse(String key) {
        JsonObject response = inputResponses.getJsonObject(key);
        if (response == null) {
            return null;
        }
        JsonArray roots = response.getJsonArray("roots");
        if (roots == null) {
            return List.of();
        }
        List<Root> list = new ArrayList<>(roots.size());
        for (int i = 0; i < roots.size(); i++) {
            JsonObject root = roots.getJsonObject(i);
            if (root != null) {
                list.add(new Root(root.getString("name"), root.getString("uri")));
            }
        }
        return list;
    }

    private static Content parseContent(JsonObject content) {
        Content.Type type = Content.Type.valueOf(content.getString("type").toUpperCase());
        return switch (type) {
            case TEXT -> new TextContent(content.getString("text"));
            case IMAGE -> new ImageContent(content.getString("data"), content.getString("mimeType"));
            case AUDIO -> new AudioContent(content.getString("data"), content.getString("mimeType"));
            default -> throw new IllegalArgumentException("Unsupported sampling content type: " + type);
        };
    }

    private static final class ContentImpl implements ElicitationResponse.Content {

        private final JsonObject json;

        ContentImpl(JsonObject json) {
            this.json = json;
        }

        @Override
        public Boolean getBoolean(String key) {
            return json.getBoolean(key);
        }

        @Override
        public String getString(String key) {
            return json.getString(key);
        }

        @Override
        public List<String> getStrings(String key) {
            JsonArray value = json.getJsonArray(key);
            return value != null ? value.stream().map(Object::toString).toList() : null;
        }

        @Override
        public Integer getInteger(String key) {
            return json.getInteger(key);
        }

        @Override
        public Number getNumber(String key) {
            return json.getNumber(key);
        }

        @Override
        public Map<String, Object> asMap() {
            return json.getMap();
        }

    }

    private static final class MetaImpl implements Meta {

        private final JsonObject meta;

        MetaImpl(JsonObject meta) {
            this.meta = meta;
        }

        @Override
        public Object getValue(MetaKey key) {
            return meta != null ? meta.getValue(key.toString()) : null;
        }

        @Override
        public JsonObject asJsonObject() {
            // Return a copy since the JsonObject is mutable
            return meta != null ? meta.copy() : new JsonObject();
        }

    }

}
