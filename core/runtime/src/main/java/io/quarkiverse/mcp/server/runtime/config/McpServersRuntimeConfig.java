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
    @WithDefault("fail")
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

    /**
     * The strategy used to select the client connections that receive the automatic
     * {@code notifications/*_list_changed} notification sent when a feature (tool, prompt, resource or resource template) is
     * registered or removed programmatically at runtime.
     */
    @WithDefault("matching-server")
    AutoListChangedStrategy autoListChangedStrategy();

    enum AutoListChangedStrategy {
        /**
         * Only connections whose server name matches the server(s) the affected feature is registered for are notified.
         */
        MATCHING_SERVER,
        /**
         * All open connections are notified, regardless of the server they are connected to. This was the behavior in versions
         * 2.0.0 and earlier.
         */
        ALL,
    }

}
