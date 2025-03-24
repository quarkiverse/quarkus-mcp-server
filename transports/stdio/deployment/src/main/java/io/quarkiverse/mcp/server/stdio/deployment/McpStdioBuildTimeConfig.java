package io.quarkiverse.mcp.server.stdio.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.stdio")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface McpStdioBuildTimeConfig {

    /**
     * Flag to specify whether the MCP server should be automatically initialized.
     * This can be useful in case where the MCP server should be conditionally
     * started. For example: from a CLI that provides multiple commands including
     * one for starting the MCP server.
     */
    @WithDefault("true")
    boolean initializationEnabled();

}
