package io.quarkiverse.mcp.server.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Map utilities.
 */
final class Maps {

    private Maps() {
    }

    /**
     * Returns an unmodifiable copy of the supplied map.
     * <p>
     * Unlike {@link Map#copyOf(Map)}, this method accepts {@code null} values; a JSON {@code null} is a valid argument
     * value and LLM clients often send it for optional parameters. The iteration order of the original map is preserved.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param map the map to copy, must not be {@code null}
     * @return an unmodifiable copy that permits {@code null} values
     */
    static <K, V> Map<K, V> copyOfAllowingNullValues(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
