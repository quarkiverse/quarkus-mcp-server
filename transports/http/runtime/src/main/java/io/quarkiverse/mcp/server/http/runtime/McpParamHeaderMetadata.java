package io.quarkiverse.mcp.server.http.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkiverse.mcp.server.runtime.FeatureKey;

/**
 * Holds the mapping from tool parameters annotated with {@link io.quarkiverse.mcp.server.http.McpParamHeader @McpParamHeader}
 * to their corresponding HTTP header names.
 * <p>
 * Build-time entries are populated via the recorder. Runtime entries (for programmatically registered tools) are added/removed
 * via {@link #register(FeatureKey, Map)} and {@link #remove(FeatureKey)}.
 */
public class McpParamHeaderMetadata {

    private final ConcurrentHashMap<FeatureKey, Map<String, String>> toolHeaders;

    McpParamHeaderMetadata(Map<FeatureKey, Map<String, String>> toolHeaders) {
        this.toolHeaders = new ConcurrentHashMap<>(toolHeaders);
    }

    /**
     * @return the header mapping for the given tool, or {@code null} if none
     */
    public Map<String, String> getHeaders(FeatureKey key) {
        return toolHeaders.get(key);
    }

    public boolean isEmpty() {
        return toolHeaders.isEmpty();
    }

    public void register(FeatureKey key, Map<String, String> headers) {
        toolHeaders.put(key, Map.copyOf(headers));
    }

    public void remove(FeatureKey key) {
        toolHeaders.remove(key);
    }

}
