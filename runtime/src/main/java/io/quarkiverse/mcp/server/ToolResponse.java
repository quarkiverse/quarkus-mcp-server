package io.quarkiverse.mcp.server;

import java.util.Arrays;
import java.util.List;

public record ToolResponse(boolean isError, List<Content> content) {

    public static ToolResponse success(Content... content) {
        return new ToolResponse(false, Arrays.asList(content));
    }

    public static ToolResponse success(List<Content> content) {
        return new ToolResponse(false, content);
    }

}
