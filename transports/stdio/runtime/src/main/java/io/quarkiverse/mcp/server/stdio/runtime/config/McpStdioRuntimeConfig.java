package io.quarkiverse.mcp.server.stdio.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.stdio")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpStdioRuntimeConfig {

    /**
     * If set to `false` then the stdio transport is completely disabled, i.e. the application does not read/write messages
     * from/to the standard input/output.
     *
     * By default, console logging is automatically redirected to the standard error. The redirection is not applied if the
     * stdio transport is disabled.
     *
     * @asciidoclet
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * If set to `true` then the standard output stream is set to "null" when the app is started.
     *
     * @asciidoclet
     */
    @WithDefault("true")
    boolean nullSystemOut();

}
