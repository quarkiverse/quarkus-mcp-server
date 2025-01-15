package io.quarkiverse.mcp.server.sse.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.sse")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface McpSseBuildTimeConfig {

    /**
     * The SSE endpoint is exposed at `/{rootPath}/sse`. By default, it's `/mcp/sse`.
     *
     * @asciidoclet
     */
    @WithDefault("/mcp")
    String rootPath();

}
