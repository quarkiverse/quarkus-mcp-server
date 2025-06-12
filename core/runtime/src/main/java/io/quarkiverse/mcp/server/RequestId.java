package io.quarkiverse.mcp.server;

/**
 * "Requests MUST include a string or integer ID."
 *
 * @param value (must not be {@code null})
 */
public record RequestId(Object value) {

    public RequestId {
        if (value == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
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
