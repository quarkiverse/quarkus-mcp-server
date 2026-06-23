package io.quarkiverse.mcp.server;

import io.vertx.core.json.JsonObject;

/**
 * A raw MCP message (request, response, or notification).
 * <p>
 * All feature methods can accept this class as a parameter. It will be automatically injected before the
 * method is invoked.
 */
public interface RawMessage {

    /**
     * Returns the message as a {@link JsonObject}.
     * <p>
     * Note that the returned object is a deep copy of the original message and this method may be expensive for large
     * messages.
     *
     * @return a copy of the message as a {@link JsonObject}
     */
    JsonObject asJsonObject();

    /**
     * Returns the message as a JSON-encoded string.
     * <p>
     * Note that the message is encoded on each invocation and this method may be expensive for large messages.
     *
     * @return the message as a JSON-encoded string
     */
    String asString();

    /**
     * Returns the JSON-RPC {@code id} of the message, or {@code null} if the message is a notification.
     *
     * @return the request id, or {@code null} for notifications
     */
    RequestId id();

    /**
     * Returns the JSON-RPC {@code method} of the message, or {@code null} if the message is a response.
     *
     * @return the method name, or {@code null} for responses
     */
    String method();

}
