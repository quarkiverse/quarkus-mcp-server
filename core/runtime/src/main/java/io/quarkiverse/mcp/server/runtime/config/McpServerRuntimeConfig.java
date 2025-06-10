package io.quarkiverse.mcp.server.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.smallrye.config.WithDefault;

public interface McpServerRuntimeConfig {

    /**
     * The server info is included in the response to an `initialize` request.
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

    /**
     * Resources config.
     */
    Resources resources();

    /**
     * Resource templates config.
     */
    ResourceTemplates resourceTemplates();

    /**
     * Tools config.
     */
    Tools tools();

    /**
     * Prompts config.
     */
    Prompts prompts();

    /**
     * Sampling config.
     */
    Sampling sampling();

    /**
     * Roots config.
     */
    Roots roots();

    /**
     * Dev mode config.
     */
    DevMode devMode();

    public interface TrafficLogging {

        /**
         * If set to `true` then JSON messages received/sent are logged.
         *
         * @asciidoclet
         */
        @WithDefault("false")
        public boolean enabled();

        /**
         * The number of characters of a text message which will be logged if traffic logging is enabled.
         *
         * @asciidoclet
         */
        @WithDefault("200")
        public int textLimit();
    }

    public interface ServerInfo {

        /**
         * The name of the server is included in the response to an `initialize` request. By default, the value of the
         * `quarkus.application.name` config property is used.
         *
         * @asciidoclet
         */
        Optional<String> name();

        /**
         * The version of the server is included in the response to an `initialize` request. By default, the value of the
         * `quarkus.application.version` config property is used.
         *
         * @asciidoclet
         */
        Optional<String> version();

    }

    public interface ClientLogging {

        /**
         * The default log level.
         *
         * @asciidoclet
         */
        @WithDefault("INFO")
        public LogLevel defaultLevel();

    }

    public interface DevMode {

        /**
         * If set to `true` then if an MCP client attempts to reconnect an SSE connection but does not reinitialize properly,
         * the server will perform a "dummy" initialization; capability negotiation and protocol version agreement is skipped.
         *
         * @asciidoclet
         */
        @WithDefault("true")
        public boolean dummyInit();

    }

    public interface Resources {

        /**
         * If the number of resources exceeds the page size then pagination is enabled and the given page size is used.
         */
        @WithDefault("50")
        int pageSize();

    }

    public interface ResourceTemplates {

        /**
         * If the number of resource templates exceeds the page size then pagination is enabled and the given page size is used.
         */
        @WithDefault("50")
        int pageSize();

    }

    public interface Prompts {

        /**
         * If the number of prompts exceeds the page size then pagination is enabled and the given page size is used.
         */
        @WithDefault("50")
        int pageSize();

    }

    public interface Tools {

        /**
         * If the number of tools exceeds the page size then pagination is enabled and the given page size is used.
         */
        @WithDefault("50")
        int pageSize();

    }

    public interface Sampling {

        /**
         * The default timeout for a sampling request. Negative and zero durations imply no timeout.
         */
        @WithDefault("60s")
        Duration defaultTimeout();

    }

    public interface Roots {

        /**
         * The default timeout to list roots. Negative and zero durations imply no timeout.
         */
        @WithDefault("60s")
        Duration defaultTimeout();
    }

}
