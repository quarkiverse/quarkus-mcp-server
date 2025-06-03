package io.quarkiverse.mcp.server.runtime.config;

import java.util.Map;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;
import io.smallrye.config.WithUnnamedKey;

@ConfigMapping(prefix = "quarkus.mcp.server")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpServersRuntimeConfig {

    /**
     * Server configurations.
     */
    @ConfigDocMapKey("server-name")
    @WithParentName
    @WithDefaults
    @WithUnnamedKey(McpServer.DEFAULT)
    Map<String, McpServerRuntimeConfig> servers();

    /**
     * The strategy used when server features, such as tools, prompts, and resources, reference an non-existent server name.
     */
    @WithDefault("FAIL")
    InvalidServerNameStrategy invalidServerNameStrategy();

    enum InvalidServerNameStrategy {
        /**
         * Application fails at startup.
         */
        FAIL,
        /**
         * Features are ignored.
         */
        IGNORE,
    }

}
