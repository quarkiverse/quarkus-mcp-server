package io.quarkiverse.mcp.server.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface McpServerBuildTimeConfig {

    /**
     * If set to `true` then the `@dev.langchain4j.agent.tool.Tool` and `@dev.langchain4j.agent.tool.P` annotations from
     * LangChain4j can be used instead of `@Tool`/`@ToolArg`.
     *
     * @asciidoclet
     */
    @WithDefault("true")
    boolean supportLangchain4jAnnotations();

}
