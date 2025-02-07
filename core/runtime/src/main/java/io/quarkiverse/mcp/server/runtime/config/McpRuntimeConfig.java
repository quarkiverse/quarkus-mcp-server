package io.quarkiverse.mcp.server.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpRuntimeConfig {

    /**
     * The server info is included in the response to an `initialize` request as defined by the
     * https://spec.modelcontextprotocol.io/specification/2024-11-05/basic/lifecycle/#initialization[spec].
     *
     * @asciidoclet
     */
    ServerInfo serverInfo();

    /**
     * Traffic logging config.
     */
    TrafficLogging trafficLogging();

    /**
     * Client logging config.
     */
    ClientLogging clientLogging();

    /**
     * The interval after which, when set, the server sends a ping message to the connected client automatically.
     * <p>
     * Ping messages are not sent automatically by default.
     */
    Optional<Duration> autoPingInterval();

    public interface TrafficLogging {

        /**
         * If set to true then JSON messages received/sent are logged.
         */
        @WithDefault("false")
        public boolean enabled();

        /**
         * The number of characters of a text message which will be logged if traffic logging is enabled.
         */
        @WithDefault("200")
        public int textLimit();
    }

    public interface ServerInfo {

        /**
         * The name of the server is included in the response to an `initialize` request as defined by the
         * https://spec.modelcontextprotocol.io/specification/2024-11-05/basic/lifecycle/#initialization[spec].
         * By default, the value of the `quarkus.application.name` config property is used.
         *
         * @asciidoclet
         */
        Optional<String> name();

        /**
         * The version of the server is included in the response to an `initialize` request as defined by the
         * https://spec.modelcontextprotocol.io/specification/2024-11-05/basic/lifecycle/#initialization[spec].
         * By default, the value of the `quarkus.application.version` config property is used.
         *
         * @asciidoclet
         */
        Optional<String> version();

    }

    public interface ClientLogging {

        /**
         * The default log level.
         */
        @WithDefault("INFO")
        public LogLevel defaultLevel();

    }

}
