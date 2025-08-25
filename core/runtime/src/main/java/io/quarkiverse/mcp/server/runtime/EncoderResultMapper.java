package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.function.Function;

import io.quarkiverse.mcp.server.Encoder;
import io.quarkiverse.mcp.server.runtime.ResultMappers.Result;
import io.quarkus.arc.All;
import io.smallrye.mutiny.Uni;

/**
 * Result mapper based on {@link Encoder}.
 *
 * @param <ENCODED> The type of the value encoded by the encoder
 * @param <ENCODER> The type of the injected encoder
 * @param <RESPONSE> The type of the serialized response
 */
abstract class EncoderResultMapper<ENCODED, ENCODER extends Encoder<?, ENCODED>, RESPONSE>
        implements EncoderMapper<Object, RESPONSE> {

    @All
    List<ENCODER> encoders;

    final Function<Result<Uni<Object>>, Uni<RESPONSE>> uni;

    protected EncoderResultMapper() {
        this.uni = new EncoderMapper<Uni<Object>, RESPONSE>() {

            @Override
            public Uni<RESPONSE> apply(Result<Uni<Object>> r) {
                return r.value().map(o -> toResponse(EncoderResultMapper.this.convert(o)));
            }
        };
    }

    @Override
    public Uni<RESPONSE> apply(Result<Object> r) {
        return Uni.createFrom().item(toResponse(convert(r.value())));
    }

    public Function<Result<Uni<Object>>, Uni<RESPONSE>> uni() {
        return uni;
    }

    protected abstract RESPONSE toResponse(ENCODED encoded);

    protected ENCODED convert(Object obj) {
        Class<?> type = obj.getClass();
        for (ENCODER encoder : encoders) {
            if (encoder.supports(type)) {
                ENCODED encoded;
                try {
                    encoded = encoder.encode(cast(obj));
                } catch (Exception e) {
                    throw new McpException("Unable to encode object of type " + type + " with " + encoder.getClass().getName(),
                            e, JsonRPC.INTERNAL_ERROR);
                }
                return encoded;
            }
        }
        return encoderNotFound(obj);
    }

    protected ENCODED encoderNotFound(Object obj) {
        throw new McpException("No encoder found for " + obj.getClass(), JsonRPC.INTERNAL_ERROR);
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(Object obj) {
        return (T) obj;
    }

}
