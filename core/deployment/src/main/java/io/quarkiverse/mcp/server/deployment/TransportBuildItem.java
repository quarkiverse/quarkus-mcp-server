package io.quarkiverse.mcp.server.deployment;

import java.util.List;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A transport present in the application.
 * <p>
 * Every transport extension should produce exactly one instance of this build item. It makes it possible to detect
 * whether multiple transports are used in the same application.
 */
public final class TransportBuildItem extends MultiBuildItem {

    public static final String STDIO = "stdio";
    public static final String HTTP = "http";
    public static final String WEBSOCKET = "websocket";

    /**
     * @param transports
     * @param name
     * @return {@code true} if the given transport is the only one present
     */
    public static boolean isOnlyTransport(List<TransportBuildItem> transports, String name) {
        return !transports.isEmpty() && transports.stream().allMatch(t -> t.getName().equals(name));
    }

    private final String name;

    public TransportBuildItem(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getName() {
        return name;
    }

}
