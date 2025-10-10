package io.quarkiverse.mcp.server.websocket.runtime.config;

import java.util.Map;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;
import io.smallrye.config.WithUnnamedKey;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.mcp.server")
public interface McpWebSocketServersBuildTimeConfig {

    /**
     * WebSocket server configurations.
     */
    @ConfigDocMapKey("server-name")
    @WithParentName
    @WithDefaults
    @WithUnnamedKey(McpServer.DEFAULT)
    Map<String, McpWebSocketServerBuildTimeConfig> servers();

}
