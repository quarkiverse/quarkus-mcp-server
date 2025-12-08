package io.quarkiverse.mcp.server.http.runtime.config;

import io.smallrye.config.WithDefault;

public interface McpHttpServerRuntimeConfig {

    /**
     * HTTP transport configuration.
     */
    Http http();

    public interface Http {

        /**
         * Streamable HTTP transport configuration.
         */
        Streamable streamable();

        public interface Streamable {

            /**
             * If set to `true` then the server performs a dummy initialization when the first message from the client is not
             * `initialize`. A new MCP session is created for each request. The capability negotiation and protocol version
             * agreement is completely skipped, so the dummy client supports no capabilities.
             *
             * Dummy initialization can be used to simulate stateless communication. However, it's not efficient and some
             * features may not work properly.
             *
             * This config property will be deprecated once the stateless mode is codified in the MCP specification.
             *
             * @asciidoclet
             */
            @WithDefault("false")
            boolean dummyInit();

        }

    }

}
