package io.quarkiverse.mcp.server.runtime.config;

import java.util.Map;
import java.util.Optional;

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
     * If set to `true` then it's possible to bind a feature (such as tool or resource) to multiple server configurations.
     * If set to `false` then only a single binding is allowed.
     * <p>
     * If not set explicitly and an ambiguous binding pattern is detected (i.e. a feature method and its declaring class both
     * declare different {@code @McpServer} values), the build will fail with an error explaining the options.
     */
    Optional<Boolean> supportMultiServerBindings();

    /**
     * Server configurations.
     */
    @ConfigDocMapKey("server-name")
    @WithParentName
    @WithDefaults
    @WithUnnamedKey(McpServer.DEFAULT)
    Map<String, McpServerBuildTimeConfig> servers();

}
