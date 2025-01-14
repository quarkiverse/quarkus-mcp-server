package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.vertx.core.Vertx;

public class PromptCompleteManager extends FeatureManager<CompletionResponse> {

    // key = prompt name + "_" + argument name
    final Map<String, FeatureMetadata<CompletionResponse>> completions;

    protected PromptCompleteManager(McpMetadata metadata, Vertx vertx, ObjectMapper mapper) {
        super(vertx, mapper);
        this.completions = metadata.promptCompletions().stream()
                .collect(Collectors.toMap(
                        m -> m.info().name() + "_"
                                + m.info().arguments().stream().filter(FeatureArgument::isParam).findFirst().orElseThrow()
                                        .name(),
                        Function.identity()));
    }

    @Override
    public List<FeatureMetadata<CompletionResponse>> list() {
        return completions.values().stream().sorted().toList();
    }

    @Override
    protected FeatureMetadata<CompletionResponse> getMetadata(String id) {
        return completions.get(id);
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Prompt completion does not exist: " + id, JsonRPC.INVALID_PARAMS);
    }

}
