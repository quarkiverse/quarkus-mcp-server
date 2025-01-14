package io.quarkiverse.mcp.server.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The selected MCP transport
 */
public final class SelectedTransportBuildItem extends SimpleBuildItem {

    private final String name;

    public SelectedTransportBuildItem(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
