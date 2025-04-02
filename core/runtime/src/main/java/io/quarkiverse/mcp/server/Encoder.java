package io.quarkiverse.mcp.server;

/**
 * @param <TYPE> The type to be encoded
 * @param <ENCODED> The resulting type of encoding
 */
public interface Encoder<TYPE, ENCODED> {

    /**
     *
     * @param runtimeType The runtime class of an object that should be encoded, must not be {@code null}
     * @return {@code true} if this encoder can encode the provided type, {@code false} otherwise
     */
    boolean supports(Class<?> runtimeType);

    /**
     *
     * @param value
     * @return the encoded value
     */
    ENCODED encode(TYPE value);

}
