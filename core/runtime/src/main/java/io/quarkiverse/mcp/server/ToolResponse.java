package io.quarkiverse.mcp.server;

import java.util.Arrays;
import java.util.List;

public record ToolResponse(boolean isError, List<? extends Content> content) {

    @SafeVarargs
    public static <C extends Content> ToolResponse success(C... content) {
        return new ToolResponse(false, Arrays.asList(content));
    }

    public static <C extends Content> ToolResponse success(List<C> content) {
        return new ToolResponse(false, content);
    }

}
