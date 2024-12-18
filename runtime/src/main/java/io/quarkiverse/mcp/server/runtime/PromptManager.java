package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.PromptResponse;
import io.vertx.core.Vertx;

@Singleton
public class PromptManager extends FeatureManager<PromptResponse> {

    final Map<String, FeatureMetadata<PromptResponse>> prompts;

    PromptManager(McpMetadata metadata, Vertx vertx, ObjectMapper mapper) {
        super(vertx, mapper);
        this.prompts = metadata.prompts().stream().collect(Collectors.toMap(m -> m.info().name(), Function.identity()));
    }

    @Override
    protected FeatureMetadata<PromptResponse> getMetadata(String name) {
        return prompts.get(name);
    }

    public List<FeatureMetadata<PromptResponse>> list() {
        return prompts.values().stream().sorted().toList();
    }
}
