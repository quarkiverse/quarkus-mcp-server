package io.quarkiverse.mcp.server;

/**
 * Filters are used to determine the set of visible/accessible tools for a specific MCP client.
 *
 * @see ToolFilter
 * @see PromptFilter
 * @see ResourceFilter
 * @see ResourceTemplateFilter
 */
public interface FilterContext {

    /**
     * @return the current method
     */
    McpMethod method();

    /**
     * @return the current connection (never {@code null})
     */
    McpConnection connection();

    /**
     * @return the {@code _meta} part of the message (never {@code null})
     */
    Meta meta();

    /**
     * @return the request id, or {@code null}
     */
    RequestId requestId();

}