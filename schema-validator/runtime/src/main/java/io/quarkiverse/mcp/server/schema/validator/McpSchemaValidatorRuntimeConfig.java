package io.quarkiverse.mcp.server.schema.validator;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.schema-validator")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpSchemaValidatorRuntimeConfig {

    /**
     * What to do when the {@code structuredContent} of a successful tool result does not conform to the tool's
     * {@code outputSchema}, or is missing although the tool declares one.
     */
    @WithDefault("fail")
    ToolOutputValidation toolOutput();

    /**
     * What to do when the arguments of a tool call do not conform to the tool's {@code inputSchema}.
     */
    @WithDefault("fail")
    ToolInputValidation toolInput();

    enum ToolInputValidation {
        /**
         * The tool is not called; a tool error ({@code isError: true}) that describes the violations is returned, so that
         * the model can correct the arguments, and a warning is logged.
         */
        FAIL,
        /**
         * A warning is logged and the tool is called with the arguments unchanged.
         */
        LOG,
        /**
         * Tool arguments are not validated.
         */
        OFF
    }

    enum ToolOutputValidation {
        /**
         * The result is replaced with a tool error ({@code isError: true}) that describes the violations, and a warning is
         * logged.
         */
        FAIL,
        /**
         * A warning is logged and the result is sent unchanged.
         */
        LOG,
        /**
         * Tool results are not validated.
         */
        OFF
    }

}
