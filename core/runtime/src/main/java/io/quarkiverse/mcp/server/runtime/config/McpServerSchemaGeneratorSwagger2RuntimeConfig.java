package io.quarkiverse.mcp.server.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.schema-generator.swagger2")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface McpServerSchemaGeneratorSwagger2RuntimeConfig {

    /**
     * Whether to use the SchemaGenerator's Swagger 2 Module.
     * If this module is not present as a dependency, this module won't be enabled.
     */
    @WithDefault("true")
    boolean enabled();
}
