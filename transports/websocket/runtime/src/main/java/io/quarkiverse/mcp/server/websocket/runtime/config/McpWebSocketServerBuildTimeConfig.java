package io.quarkiverse.mcp.server.websocket.runtime.config;

import io.smallrye.config.WithDefault;

public interface McpWebSocketServerBuildTimeConfig {

    /**
     * WebSocket configuration.
     */
    WebSocket websocket();

    public interface WebSocket {

        /**
         * Enable WebSocket transport.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * The WebSocket MCP endpoint is exposed at `\{endpointPath}`. By default, it's `/mcp/ws`.
         *
         * @asciidoclet
         */
        @WithDefault("/mcp/ws")
        String endpointPath();

    }

}
