package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import io.quarkiverse.mcp.server.Encoder;
import io.smallrye.mutiny.Uni;

/**
 * Unlike {@link EncoderResultMapper} this mapper can also process {@code List<X>} and {@code Uni<List<X>>}.
 *
 * @param <ENCODED> The type of the value encoded by the encoder
 * @param <ENCODER> The type of the injected encoder
 * @param <RESPONSE> The type of the serialized response
 */
abstract class ListEncoderResultMapper<ENCODED, ENCODER extends Encoder<?, ENCODED>, RESPONSE>
        extends EncoderResultMapper<ENCODED, Encoder<?, ENCODED>, RESPONSE> {

    final EncoderMapper<List<Object>, RESPONSE> list;
    final EncoderMapper<Uni<List<Object>>, RESPONSE> uniList;

    protected ListEncoderResultMapper() {
        this.list = new EncoderMapper<List<Object>, RESPONSE>() {

            @Override
            public Uni<RESPONSE> apply(List<Object> list) {
                return Uni.createFrom().item(ListEncoderResultMapper.this.convertList(list));
            }
        };
        this.uniList = new EncoderMapper<Uni<List<Object>>, RESPONSE>() {

            @Override
            public Uni<RESPONSE> apply(Uni<List<Object>> uni) {
                return uni.map(
                        list -> ListEncoderResultMapper.this.convertList(list));
            }
        };
    }

    public EncoderMapper<List<Object>, RESPONSE> list() {
        if (list == null) {
            throw new UnsupportedOperationException();
        }
        return list;
    }

    public EncoderMapper<Uni<List<Object>>, RESPONSE> uniList() {
        if (uniList == null) {
            throw new UnsupportedOperationException();
        }
        return uniList;
    }

    protected RESPONSE convertList(List<Object> list) {
        RESPONSE container = convertContainer(list);
        if (container != null) {
            return container;
        }
        return toResponse(list.stream().map(ListEncoderResultMapper.this::convert).toList());
    }

    protected RESPONSE convertContainer(Object obj) {
        return null;
    }

    @Override
    protected RESPONSE toResponse(ENCODED encoded) {
        return toResponse(List.of(encoded));
    }

    protected abstract RESPONSE toResponse(List<ENCODED> encoded);

}
