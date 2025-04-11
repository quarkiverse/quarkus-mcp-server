package io.quarkiverse.mcp.server;

/**
 * The progress token sent by the client.
 */
public record ProgressToken(Object value) {

    public ProgressToken {
        if (!(value instanceof String) && !(value instanceof Number)) {
            throw new IllegalArgumentException("Token must be a string or a number");
        }
    }

    public Type type() {
        return value instanceof String ? Type.STRING : Type.INTEGER;
    }

    public Number asInteger() {
        if (value instanceof Number number) {
            return number;
        } else {
            throw new IllegalArgumentException("Token is not a number");
        }
    }

    public String asString() {
        return value.toString();
    }

    public enum Type {
        INTEGER,
        STRING
    }
}