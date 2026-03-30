package io.quarkiverse.mcp.server;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Controls tool selection behavior for sampling requests.
 *
 * @param mode the selection mode (must not be {@code null})
 */
public record ToolChoice(Mode mode) {

    public ToolChoice {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
    }

    public enum Mode {
        NONE,
        REQUIRED,
        AUTO;

        @JsonValue
        public String value() {
            return name().toLowerCase();
        }
    }

}
