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
     * Keep in mind that console logging is still automatically redirected to the standard error. You will need to set the
     * `quarkus.log.console.stderr` to `false` to suppress this behavior.
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
