package io.quarkiverse.mcp.server;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Response to a {@code tools/call} request from the client.
 *
 * @param isError {@code true} if the tool call ended in an error
 * @param content the list of content items (must not be {@code null})
 * @param _meta the optional metadata
 */
@JsonInclude(Include.NON_NULL)
public record ToolResponse(boolean isError, List<? extends Content> content, Map<MetaKey, Object> _meta) {

    /**
     * @param <C>
     * @param content
     * @return a successful response with the specified content items
     */
    @SafeVarargs
    public static <C extends Content> ToolResponse success(C... content) {
        return new ToolResponse(false, Arrays.asList(content));
    }

    /**
     * @param <C>
     * @param content
     * @return a successful response with the specified content items
     */
    public static <C extends Content> ToolResponse success(List<C> content) {
        return new ToolResponse(false, content);
    }

    /**
     * @param message
     * @return an unsuccessful response with single text content item
     */
    public static ToolResponse error(String message) {
        return new ToolResponse(true, List.of(new TextContent(message)));
    }

    /**
     * @param message
     * @return a successful response with single text content item
     */
    public static ToolResponse success(String message) {
        return new ToolResponse(false, List.of(new TextContent(message)));
    }

    public ToolResponse(boolean isError, List<? extends Content> content) {
        this(isError, content, null);
    }

    public ToolResponse {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }

}
