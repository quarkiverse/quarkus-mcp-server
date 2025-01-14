package io.quarkiverse.mcp.server.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.mcp.server")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface McpBuildTimeConfig {

    /**
     * If multiple transports extensions are on the classpath, select which one is used
     */
    Optional<String> transport();
}
