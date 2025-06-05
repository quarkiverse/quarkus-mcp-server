package io.quarkiverse.mcp.server.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A discovered server configration name.
 * <p>
 * All transports that contribute to the server configuration should produce this build item for each server name found.
 */
public final class ServerNameBuildItem extends MultiBuildItem {

    private final String name;

    public ServerNameBuildItem(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
