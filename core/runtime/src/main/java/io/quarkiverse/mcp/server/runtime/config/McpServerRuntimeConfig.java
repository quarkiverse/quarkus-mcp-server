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
     * Elicitation config.
     */
    Elicitation elicitation();

    /**
     * Dev mode config.
     */
    DevMode devMode();

    /**
     * The amount of time that a connection can be inactive. The connection might be automatically closed when the timeout
     * expires. Negative and zero durations imply no timeout.
     * <p>
     * The {@code stdio} transport disables this timeout by default.
     */
    @WithDefault("30m")
    Duration connectionIdleTimeout();

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

    interface ServerInfo {

        /**
         * The name of the server is included in the response to an `initialize` request.
         *
         * By default, the value of the `quarkus.application.name` config property is used.
         *
         * @asciidoclet
         */
        Optional<String> name();

        /**
         * The version of the server is included in the response to an `initialize` request.
         *
         * By default, the value of the `quarkus.application.version` config property is used.
         *
         * @asciidoclet
         */
        Optional<String> version();

        /**
         * The human-readable name of the server is included in the response to an `initialize` request.
         */
        Optional<String> title();

        /**
         * The instructions describing how to use the server and its features. These are hints for the clients.
         */
        Optional<String> instructions();

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
         * If the number of resources exceeds the page size then pagination is enabled and the given page size is used. The
         * pagination is disabled if set to a value less or equal to zero.
         */
        @WithDefault("50")
        int pageSize();

    }

    public interface ResourceTemplates {

        /**
         * If the number of resource templates exceeds the page size then pagination is enabled and the given page size is used.
         * The
         * pagination is disabled if set to a value less or equal to zero.
         */
        @WithDefault("50")
        int pageSize();

    }

    public interface Prompts {

        /**
         * If the number of prompts exceeds the page size then pagination is enabled and the given page size is used. The
         * pagination is disabled if set to a value less or equal to zero.
         */
        @WithDefault("50")
        int pageSize();

    }

    public interface Tools {

        /**
         * If the number of tools exceeds the page size then pagination is enabled and the given page size is used. The
         * pagination is disabled if set to a value less or equal to zero.
         */
        @WithDefault("50")
        int pageSize();

        /**
         * Structured content config.
         */
        StructuredContent structuredContent();

        /**
         * The strategy used for input validation errors.
         */
        @WithDefault("tool")
        InputValidationError inputValidationError();

        enum InputValidationError {

            /**
             * An input validation error results in a protocol error with JSON-RPC error code `-32602` (invalid params).
             *
             * @asciidoclet
             */
            PROTOCOL,

            /**
             * An input validation error is reported in a tool response with `isError: true`.
             *
             * This behavior is compliant with the specification.
             *
             * @asciidoclet
             */
            TOOL,
        }

    }

    public interface StructuredContent {

        /**
         * If set to `true` and a tool returns a structured content but no other content then the serialized JSON is
         * also automatically set as a `TextContent` for backwards compatibility.
         *
         * @asciidoclet
         */
        @WithDefault("false")
        boolean compatibilityMode();

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

    public interface Elicitation {

        /**
         * The default timeout for an elicitation request. Negative and zero durations imply no timeout.
         */
        @WithDefault("60s")
        Duration defaultTimeout();
    }

}
