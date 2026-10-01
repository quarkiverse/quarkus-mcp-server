package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.ElicitationResponse.Action;
import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.Root;
import io.quarkiverse.mcp.server.SamplingResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class InputResponsesImpl implements InputResponses {

    private static final InputResponsesImpl EMPTY = new InputResponsesImpl(null);

    static InputResponsesImpl from(JsonObject params) {
        JsonObject inputResponses = params != null ? params.getJsonObject("inputResponses") : null;
        return inputResponses != null ? new InputResponsesImpl(inputResponses) : EMPTY;
    }

    private final JsonObject inputResponses;

    InputResponsesImpl(JsonObject inputResponses) {
        this.inputResponses = inputResponses;
    }

    @Override
    public boolean isEmpty() {
        return inputResponses == null || inputResponses.isEmpty();
    }

    @Override
    public boolean has(String key) {
        return inputResponses != null && inputResponses.containsKey(key);
    }

    @Override
    public ElicitationResponse getElicitationResponse(String key) {
        if (inputResponses == null) {
            return null;
        }
        JsonObject response = inputResponses.getJsonObject(key);
        if (response == null) {
            return null;
        }
        Action action = Action.valueOf(response.getString("action").toUpperCase());
        JsonObject content = response.getJsonObject("content");
        return new ElicitationResponse(action, new ElicitationRequestImpl.ContentImpl(content), MetaImpl.from(response));
    }

    @Override
    public SamplingResponse getSamplingResponse(String key) {
        if (inputResponses == null) {
            return null;
        }
        JsonObject response = inputResponses.getJsonObject(key);
        if (response == null) {
            return null;
        }
        String model = response.getString("model");
        Role role = Role.valueOf(response.getString("role").toUpperCase());
        Content content = Contents.parseContent(response.getJsonObject("content"));
        return new SamplingResponse(content, model, role, response.getString("stopReason"), MetaImpl.from(response));
    }

    @Override
    public List<Root> getRootsResponse(String key) {
        if (inputResponses == null) {
            return null;
        }
        JsonObject response = inputResponses.getJsonObject(key);
        if (response == null) {
            return null;
        }
        JsonArray roots = response.getJsonArray("roots");
        if (roots == null) {
            return List.of();
        }
        List<Root> list = new ArrayList<>(roots.size());
        for (Object root : roots) {
            if (root instanceof JsonObject jo) {
                list.add(new Root(jo.getString("name"), jo.getString("uri")));
            }
        }
        return list;
    }

}
