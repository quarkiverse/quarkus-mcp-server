package io.quarkiverse.mcp.server.stdio.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.stdio")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpStdioRuntimeConfig {

    /**
     * If set to `true` then the standard output stream is set to "null" when the app is started.
     *
     * @asciidoclet
     */
    @WithDefault("true")
    boolean nullSystemOut();

}
