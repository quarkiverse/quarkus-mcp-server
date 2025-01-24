package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ContentEncoder;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.ToolResponseEncoder;
import io.quarkus.arc.All;
import io.smallrye.mutiny.Uni;

@Singleton
public class ToolEncoderResultMapper extends ListEncoderResultMapper<Content, ContentEncoder<?>, ToolResponse> {

    @All
    List<ToolResponseEncoder<?>> toolResponseEncoders;

    final EncoderMapper<Uni<Object>, ToolResponse> uni;

    private ToolEncoderResultMapper() {
        this.uni = new EncoderMapper<Uni<Object>, ToolResponse>() {

            @Override
            public Uni<ToolResponse> apply(Uni<Object> uni) {
                return uni.chain(o -> ToolEncoderResultMapper.this.apply(o));
            }
        };
    }

    @Override
    public Uni<ToolResponse> apply(Object obj) {
        Uni<ToolResponse> ret = null;
        ToolResponse toolResponse = convertToolResponse(obj);
        if (toolResponse != null) {
            ret = Uni.createFrom().item(toolResponse);
        } else {
            ret = super.apply(obj);
        }
        return ret;
    }

    @Override
    public EncoderMapper<Uni<Object>, ToolResponse> uni() {
        return uni;
    }

    @Override
    protected ToolResponse toResponse(List<Content> content) {
        return ToolResponse.success(content);
    }

    private ToolResponse convertToolResponse(Object obj) {
        Class<?> type = obj.getClass();
        for (ToolResponseEncoder<?> encoder : toolResponseEncoders) {
            if (encoder.supports(type)) {
                ToolResponse encoded;
                try {
                    encoded = encoder.encode(cast(obj));
                } catch (Exception e) {
                    throw new McpException("Unable to encode object of type " + type + " with " + encoder.getClass().getName(),
                            JsonRPC.INTERNAL_ERROR);
                }
                return encoded;
            }
        }
        return null;
    }
}
