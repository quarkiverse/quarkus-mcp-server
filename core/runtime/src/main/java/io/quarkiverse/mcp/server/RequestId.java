package io.quarkiverse.mcp.server;

/**
 * Represents a JSON-RPC request identifier.
 * <p>
 * Per the JSON-RPC 2.0 specification, request identifiers must be either a {@link String} or an integer {@link Number}.
 *
 * @param value the identifier value, must be a {@link String} or a {@link Number} (must not be {@code null})
 */
public record RequestId(Object value) {

    public RequestId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (!(value instanceof Number) && !(value instanceof String)) {
            throw new IllegalArgumentException("value must be string or number");
        }
    }

    /**
     * @return {@code true} if the identifier is a {@link Number}
     */
    public boolean isNumber() {
        return value instanceof Number;
    }

    /**
     * @return {@code true} if the identifier is a {@link String}
     */
    public boolean isString() {
        return value instanceof String;
    }

    /**
     * @return the identifier value as an {@link Integer}
     * @throws IllegalArgumentException if the identifier is not a {@link Number}
     */
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

    /**
     * @return the string representation of the identifier value
     */
    public String asString() {
        return value.toString();
    }

}
