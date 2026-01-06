package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.ElicitationRequest.BooleanSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.EnumSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.MultiSelectEnumSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.NumberSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;

/**
 * A response to an {@link ElicitationRequest}.
 *
 * @param action (must not be {@code null})
 * @param content (must not be {@code null})
 * @param meta (must not be {@code null})
 */
public record ElicitationResponse(Action action, Content content, Meta meta) {

    public ElicitationResponse {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (meta == null) {
            throw new IllegalArgumentException("meta must not be null");
        }
    }

    public boolean actionAccepted() {
        return action == Action.ACCEPT;
    }

    public interface Content {

        /**
         * @param key
         * @return the value or {@code null}
         * @see BooleanSchema
         */
        Boolean getBoolean(String key);

        /**
         *
         * @param key
         * @return the value or {@code null}
         * @see StringSchema
         * @see EnumSchema
         */
        String getString(String key);

        /**
         *
         * @param key
         * @return the value or {@code null}
         * @see MultiSelectEnumSchema
         */
        List<String> getStrings(String key);

        /**
         *
         * @param key
         * @return the value or {@code null}
         * @see NumberSchema
         */
        Integer getInteger(String key);

        /**
         *
         * @param key
         * @return the value or {@code null}
         * @see NumberSchema
         */
        Number getNumber(String key);

        Map<String, Object> asMap();
    }

    public enum Action {
        ACCEPT,
        DECLINE,
        CANCEL
    }

}
