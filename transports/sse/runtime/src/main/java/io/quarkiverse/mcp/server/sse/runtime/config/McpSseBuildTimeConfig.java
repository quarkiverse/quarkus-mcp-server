package io.quarkiverse.mcp.server.sse.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.sse")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface McpSseBuildTimeConfig {

    /**
     * The root path.
     *
     * @asciidoclet
     */
    @WithDefault("/mcp")
    String rootPath();

}
