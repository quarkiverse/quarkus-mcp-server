package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.vertx.core.Vertx;

public class ResourceTemplateCompleteManager extends FeatureManager<CompletionResponse> {

    // key = resource template name + "_" + argument name
    final Map<String, FeatureMetadata<CompletionResponse>> completions;

    protected ResourceTemplateCompleteManager(McpMetadata metadata, Vertx vertx, ObjectMapper mapper) {
        super(vertx, mapper);
        this.completions = metadata.resourceTemplateCompletions().stream()
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
        return new McpException("Resource template completion does not exist: " + id, JsonRPC.INVALID_PARAMS);
    }

}
