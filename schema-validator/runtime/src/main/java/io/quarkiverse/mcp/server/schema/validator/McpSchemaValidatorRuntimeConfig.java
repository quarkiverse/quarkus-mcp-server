package io.quarkiverse.mcp.server.schema.validator;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.schema-validator")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpSchemaValidatorRuntimeConfig {

    /**
     * Tool calls validation.
     */
    Tools tools();

    interface Tools {

        /**
         * What to do when the arguments of a tool call do not conform to the tool's {@code inputSchema}.
         * <p>
         * If set to {@code fail}, the tool is not called and a tool error ({@code isError: true}) that describes the
         * violations is returned, so that the model can correct the arguments.
         */
        @WithDefault("fail")
        Validation input();

        /**
         * What to do when the {@code structuredContent} of a successful tool result does not conform to the tool's
         * {@code outputSchema}, or is missing although the tool declares one.
         * <p>
         * If set to {@code fail}, the result is replaced with a tool error ({@code isError: true}) that describes the
         * violations.
         */
        @WithDefault("fail")
        Validation output();

    }

    enum Validation {
        /**
         * A tool error that describes the violations is returned and a warning is logged.
         */
        FAIL,
        /**
         * A warning is logged; the tool call proceeds unchanged.
         */
        LOG,
        /**
         * No validation is performed.
         */
        OFF
    }

}
