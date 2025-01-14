package io.quarkiverse.mcp.server;

import java.util.List;

public record CompletionResponse(List<String> values, Integer total, Boolean hasMore) {

}
