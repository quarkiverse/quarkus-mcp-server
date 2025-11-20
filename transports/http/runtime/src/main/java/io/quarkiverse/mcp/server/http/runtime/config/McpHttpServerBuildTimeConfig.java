package io.quarkiverse.mcp.server.http.runtime.config;

import io.smallrye.config.WithDefault;

public interface McpHttpServerBuildTimeConfig {

    /**
     * HTTP transport configuration.
     */
    Http http();

    public interface Http {

        /**
         * The MCP endpoint (as defined in the specification `2025-03-26`) is exposed at `\{rootPath}`. By default, it's `/mcp`.
         *
         * The SSE endpoint (as defined in the specification `2024-11-05`) is exposed at `\{rootPath}/sse`. By default, it's
         * `/mcp/sse`.
         *
         * @asciidoclet
         */
        @WithDefault("/mcp")
        String rootPath();

        /**
         * The SSE endpoint (as defined in the specification `2024-11-05`) sends a message endpoint as the first event to
         * the client. And the client should use this endpoint for all subsequent requests.
         */
        MessageEndpoint messageEndpoint();

        public interface MessageEndpoint {

            /**
             * If set to true then the query params from the initial HTTP request should be included in the message endpoint.
             */
            @WithDefault("false")
            boolean includeQueryParams();

        }

    }

}
