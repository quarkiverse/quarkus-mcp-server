package io.quarkiverse.mcp.server.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface McpBuildTimeConfig {

    /**
     * The root path.
     *
     * @asciidoclet
     */
    @WithDefault("/mcp")
    String rootPath();

}
