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
             * not `initialize`. A new MCP session is created for each request. The formal capability negotiation and protocol
             * version agreement is skipped. However, the client can optionally provide its info and capabilities through the
             * `_meta` fields of the request params: `io.modelcontextprotocol/clientInfo` and
             * `io.modelcontextprotocol/clientCapabilities`. The protocol version can be specified with the
             * `Mcp-Protocol-Version` header.
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

            /**
             * If set to `true` then SSE initialization is performed lazily, only when an SSE-dependent API
             * (such as `Progress`, `McpLog`, `Sampling`, `Roots`, or `Elicitation`) is actually used during
             * request processing.
             *
             * If set to `false` then SSE initialization is performed eagerly, based on the declared parameters
             * of the feature method. This was the default behavior before Quarkus MCP Server 1.13.
             *
             * @asciidoclet
             */
            @WithDefault("true")
            boolean lazySseInit();

        }

    }

}
