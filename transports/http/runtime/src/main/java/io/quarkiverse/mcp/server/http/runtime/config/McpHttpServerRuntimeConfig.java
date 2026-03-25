package io.quarkiverse.mcp.server.http.runtime.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

public interface McpHttpServerRuntimeConfig {

    /**
     * HTTP transport configuration.
     */
    Http http();

    public interface Http {

        /**
         * Enable DNS rebinding protection for localhost servers.
         */
        @WithName("dns-rebinding-check.enabled")
        @WithDefault("true")
        boolean dnsRebindingCheckEnabled();

        /**
         * Streamable HTTP transport configuration.
         */
        Streamable streamable();

        public interface Streamable {

            /**
             * If set to `true` then the server performs an automatic initialization when the first message from the client is
             * not `initialize`. A new MCP session is created for each request. The capability negotiation and protocol version
             * agreement is completely skipped, so the auto-initialized client supports no capabilities.
             *
             * Auto-initialization can be used to simulate stateless communication. However, it's not efficient and some
             * features may not work properly.
             *
             * This config property will be deprecated once the stateless mode is codified in the MCP specification.
             *
             * @asciidoclet
             */
            @WithDefault("false")
            boolean autoInit();

        }

    }

}
