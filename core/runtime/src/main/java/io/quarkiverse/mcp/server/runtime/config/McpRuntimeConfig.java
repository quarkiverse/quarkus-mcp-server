package io.quarkiverse.mcp.server.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.mcp.server")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpRuntimeConfig {

    /**
     * The server info is included in the response to an `initialize` request as defined by the
     * https://spec.modelcontextprotocol.io/specification/basic/lifecycle/#initialization[spec].
     *
     * @asciidoclet
     */
    ServerInfo serverInfo();

    public interface ServerInfo {

        /**
         * The name of the server is included in the response to an `initialize` request as defined by the
         * https://spec.modelcontextprotocol.io/specification/basic/lifecycle/#initialization[spec].
         * By default, the value of the `quarkus.application.name` config property is used.
         *
         * @asciidoclet
         */
        Optional<String> name();

        /**
         * The version of the server is included in the response to an `initialize` request as defined by the
         * https://spec.modelcontextprotocol.io/specification/basic/lifecycle/#initialization[spec].
         * By default, the value of the `quarkus.application.version` config property is used.
         *
         * @asciidoclet
         */
        Optional<String> version();

    }

}
