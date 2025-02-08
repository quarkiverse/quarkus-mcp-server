package io.quarkiverse.mcp.server;

import java.util.List;

public record CompletionResponse(List<String> values, Integer total, Boolean hasMore) {

    public static CompletionResponse create(List<String> values) {
        return new CompletionResponse(values, values.size(), false);
    }

}
