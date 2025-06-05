package io.quarkiverse.mcp.server.sse.runtime.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

public interface McpSseServerBuildTimeConfig {

    /**
     * The MCP endpoint (as defined in the specification `2025-03-26`) is exposed at `\{rootPath}`. By default, it's `/mcp`.
     *
     * The SSE endpoint (as defined in the specification `2024-11-05`) is exposed at `\{rootPath}/sse`. By default, it's
     * `/mcp/sse`.
     *
     * @asciidoclet
     */
    @WithName("sse.root-path")
    @WithDefault("/mcp")
    String rootPath();

}
