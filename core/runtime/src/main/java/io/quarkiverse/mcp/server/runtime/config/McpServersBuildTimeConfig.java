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
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface McpServersBuildTimeConfig {

    /**
     * If set to `true` then the `@dev.langchain4j.agent.tool.Tool` and `@dev.langchain4j.agent.tool.P` annotations from
     * LangChain4j can be used instead of `@Tool`/`@ToolArg`.
     *
     * @asciidoclet
     */
    @WithDefault("true")
    boolean supportLangchain4jAnnotations();

    /**
     * Server configurations.
     */
    @ConfigDocMapKey("server-name")
    @WithParentName
    @WithDefaults
    @WithUnnamedKey(McpServer.DEFAULT)
    Map<String, McpServerBuildTimeConfig> servers();

}
