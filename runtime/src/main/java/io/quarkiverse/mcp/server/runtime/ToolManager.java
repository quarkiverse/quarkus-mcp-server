package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.ToolResponse;
import io.vertx.core.Vertx;

@Singleton
public class ToolManager extends FeatureManager<ToolResponse> {

    final Map<String, FeatureMetadata<ToolResponse>> tools;

    ToolManager(McpMetadata metadata, Vertx vertx, ObjectMapper mapper) {
        super(vertx, mapper);
        this.tools = metadata.tools().stream().collect(Collectors.toMap(m -> m.info().name(), Function.identity()));
    }

    @Override
    protected FeatureMetadata<ToolResponse> getMetadata(String name) {
        return tools.get(name);
    }

    public List<FeatureMethodInfo> list() {
        return tools.values().stream().map(FeatureMetadata::info).toList();
    }

}
