package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Composite key for feature storage, consisting of a feature name (or URI for resources) and a server name.
 * <p>
 * A feature bound to multiple servers is stored under multiple keys, one per server, all pointing to the same info instance.
 */
public record FeatureKey(String name, String serverName) {

    public FeatureKey {
        Objects.requireNonNull(name);
        Objects.requireNonNull(serverName);
    }

    static List<FeatureKey> list(String name, Set<String> serverNames) {
        List<FeatureKey> keys = new ArrayList<>(serverNames.size());
        for (String sn : serverNames) {
            keys.add(new FeatureKey(name, sn));
        }
        return keys;
    }

}
