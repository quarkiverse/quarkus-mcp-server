package io.quarkiverse.mcp.server.sse.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.sse")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpSseRuntimeConfig {

    /**
     * Traffic logging config.
     */
    TrafficLogging trafficLogging();

    public interface TrafficLogging {

        /**
         * If set to true then JSON messages received/sent are logged.
         */
        @WithDefault("false")
        public boolean enabled();

        /**
         * The number of characters of a text message which will be logged if traffic logging is enabled.
         */
        @WithDefault("100")
        public int textLimit();
    }

}
