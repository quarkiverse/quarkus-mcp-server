package io.quarkiverse.mcp.server.sse.deployment;

import java.util.List;

import io.quarkiverse.mcp.server.sse.runtime.McpServerEndpoints;
import io.quarkiverse.mcp.server.sse.runtime.McpServerEndpoints.McpServerEndpoint;
import io.quarkus.builder.item.SimpleBuildItem;

final class McpServerEndpointsBuildItem extends SimpleBuildItem {

    private final List<McpServerEndpoint> endpoints;

    McpServerEndpointsBuildItem(List<McpServerEndpoint> endpoints) {
        this.endpoints = endpoints;
    }

    List<McpServerEndpoints.McpServerEndpoint> getEndpoints() {
        return endpoints;
    }

}
