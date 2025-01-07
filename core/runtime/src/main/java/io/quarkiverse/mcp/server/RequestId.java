package io.quarkiverse.mcp.server;

import java.util.Objects;

/**
 * "Requests MUST include a string or integer ID."
 */
public record RequestId(Object value) {

    public RequestId(Object value) {
        this.value = Objects.requireNonNull(value);
    }

    public Integer asInteger() {
        if (value instanceof Number number) {
            if (number instanceof Integer) {
                return (Integer) number;
            } else {
                return number.intValue();
            }
        } else {
            throw new IllegalArgumentException("Request id is not a number");
        }
    }

    public String asString() {
        return value.toString();
    }

}
