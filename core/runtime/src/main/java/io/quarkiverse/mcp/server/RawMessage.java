package io.quarkiverse.mcp.server;

import io.vertx.core.json.JsonObject;

/**
 * A raw message that represents an unprocessed MCP request or notification from an MCP client.
 * <p>
 * All feature methods can accept this class as a parameter. It will be automatically injected before the
 * method is invoked.
 */
public interface RawMessage {

    JsonObject asJsonObject();

    String asString();

}
