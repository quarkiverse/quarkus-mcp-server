package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.function.Function;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ContentEncoder;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.ToolResponseEncoder;
import io.quarkiverse.mcp.server.runtime.ResultMappers.Result;
import io.quarkus.arc.All;
import io.smallrye.mutiny.Uni;

@Singleton
public class ToolEncoderResultMapper extends ListEncoderResultMapper<Content, ContentEncoder<?>, ToolResponse> {

    @All
    List<ToolResponseEncoder<?>> toolResponseEncoders;

    final Function<Result<Uni<Object>>, Uni<ToolResponse>> uni;

    private ToolEncoderResultMapper() {
        this.uni = new EncoderMapper<Uni<Object>, ToolResponse>() {

            @Override
            public Uni<ToolResponse> apply(Result<Uni<Object>> r) {
                return r.value().chain(o -> ToolEncoderResultMapper.this.apply(new Result<Object>(o, r.serverName())));
            }
        };
    }

    @Override
    public Uni<ToolResponse> apply(Result<Object> r) {
        Uni<ToolResponse> ret = null;
        ToolResponse toolResponse = convertContainer(r.value());
        if (toolResponse != null) {
            ret = Uni.createFrom().item(toolResponse);
        } else {
            ret = super.apply(r);
        }
        return ret;
    }

    @Override
    public Function<Result<Uni<Object>>, Uni<ToolResponse>> uni() {
        return uni;
    }

    @Override
    protected ToolResponse toResponse(List<Content> content) {
        return ToolResponse.success(content);
    }

    @Override
    protected ToolResponse convertContainer(Object obj) {
        Class<?> type = obj.getClass();
        for (ToolResponseEncoder<?> encoder : toolResponseEncoders) {
            if (encoder.supports(type)) {
                ToolResponse encoded;
                try {
                    encoded = encoder.encode(cast(obj));
                } catch (Exception e) {
                    throw new McpException("Unable to encode object of type " + type + " with " + encoder.getClass().getName(),
                            JsonRpcErrorCodes.INTERNAL_ERROR);
                }
                return encoded;
            }
        }
        return null;
    }

}
