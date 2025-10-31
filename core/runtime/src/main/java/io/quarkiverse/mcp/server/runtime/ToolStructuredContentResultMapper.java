package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.function.Function;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.ResultMappers.Result;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.smallrye.mutiny.Uni;

@Singleton
public class ToolStructuredContentResultMapper implements Function<Result<Object>, Uni<ToolResponse>> {

    private static final Logger LOG = Logger.getLogger(ToolStructuredContentResultMapper.class);

    final Function<Result<Uni<Object>>, Uni<ToolResponse>> uni;

    private final McpServersRuntimeConfig config;
    private final ObjectMapper mapper;

    public ToolStructuredContentResultMapper(McpServersRuntimeConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.uni = new Function<ResultMappers.Result<Uni<Object>>, Uni<ToolResponse>>() {
            @Override
            public Uni<ToolResponse> apply(Result<Uni<Object>> r) {
                return r.value().chain(o -> ToolStructuredContentResultMapper.this.apply(new Result<>(o, r.serverName())));
            }
        };
    }

    public Function<Result<Uni<Object>>, Uni<ToolResponse>> uni() {
        return uni;
    }

    @Override
    public Uni<ToolResponse> apply(Result<Object> r) {
        boolean compatMode = true;
        McpServerRuntimeConfig serverConfig = config.servers().get(r.serverName());
        if (serverConfig != null) {
            compatMode = serverConfig.tools().structuredContent().compatibilityMode();
        }
        ToolResponse toolResponse;
        if (compatMode) {
            List<Content> content = List.of();
            try {
                content = List.of(new TextContent(mapper.writeValueAsString(r.value())));
            } catch (JsonProcessingException e) {
                LOG.errorf(e, "Unable to serialize the structured content");
            }
            toolResponse = new ToolResponse(false, content, r.value(), null);
        } else {
            toolResponse = ToolResponse.structuredSuccess(r.value());
        }
        return Uni.createFrom().item(toolResponse);
    }

}
