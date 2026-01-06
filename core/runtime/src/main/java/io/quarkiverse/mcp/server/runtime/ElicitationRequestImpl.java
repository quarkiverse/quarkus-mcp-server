package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.ElicitationResponse.Action;
import io.quarkiverse.mcp.server.ElicitationResponse.Content;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ElicitationRequestImpl implements ElicitationRequest {

    private static final Logger LOG = Logger.getLogger(ElicitationRequestImpl.class);

    private final String message;
    private final Map<String, PrimitiveSchema> requestedSchema;

    private final Sender sender;
    private final ResponseHandlers responseHandlers;
    private final Duration timeout;

    ElicitationRequestImpl(String message, Map<String, PrimitiveSchema> requestedSchema, Sender sender,
            ResponseHandlers responseHandlers, Duration timeout) {
        this.message = message;
        this.requestedSchema = Map.copyOf(requestedSchema);
        this.sender = sender;
        this.responseHandlers = responseHandlers;
        this.timeout = timeout;
    }

    @JsonProperty
    @Override
    public String message() {
        return message;
    }

    @Override
    public Map<String, PrimitiveSchema> requestedSchema() {
        return requestedSchema;
    }

    @Override
    public Uni<ElicitationResponse> send() {
        AtomicLong id = new AtomicLong();
        Uni<ElicitationResponse> ret = Uni.createFrom().completionStage(() -> {
            CompletableFuture<ElicitationResponse> future = new CompletableFuture<ElicitationResponse>();
            Long requestId = responseHandlers.newRequest(m -> {
                JsonObject result = m.getJsonObject("result");
                if (result == null) {
                    throw new IllegalStateException("Invalid elicitation response: " + m);
                }
                Action action = Action.valueOf(result.getString("action").toUpperCase());
                JsonObject content = result.getJsonObject("content");
                future.complete(new ElicitationResponse(action, new ContentImpl(content), MetaImpl.from(result)));
            });
            id.set(requestId);
            JsonObject properties = new JsonObject();
            JsonObject schema = new JsonObject().put("type", "object").put("properties", properties);
            JsonArray required = new JsonArray();
            for (Entry<String, PrimitiveSchema> e : requestedSchema.entrySet()) {
                if (e.getValue().required()) {
                    required.add(e.getKey());
                }
                properties.put(e.getKey(), e.getValue().asJson());
            }
            schema.put("required", required);
            JsonObject params = new JsonObject().put("message", message).put("requestedSchema", schema);
            sender.send(Messages.newRequest(id, McpMessageHandler.ELICITATION_CREATE, params));
            return future;
        });
        if (!timeout.isNegative() && !timeout.isZero()) {
            ret = ret.ifNoItem()
                    .after(timeout).fail()
                    .onFailure(TimeoutException.class).invoke(te -> {
                        long requestId = id.get();
                        if (requestId != 0 && responseHandlers.remove(requestId)) {
                            LOG.debugf("Response handler for %s removed due to timeout", requestId);
                        }
                    });
        }
        return ret;
    }

    static class ContentImpl implements Content {

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
            if (value != null) {
                return value.stream().map(Object::toString).toList();
            }
            return null;
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

}
