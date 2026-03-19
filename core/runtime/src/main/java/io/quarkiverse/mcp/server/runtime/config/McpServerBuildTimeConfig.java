package io.quarkiverse.mcp.server.runtime.config;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public interface McpServerBuildTimeConfig {

    /**
     * Tools config.
     */
    Tools tools();

    public interface Tools {

        /**
         * Maximum length of a tool name.
         */
        OptionalInt nameMaxLength();

        /**
         * All tool names must match the specified pattern.
         * <p>
         * The names of tools that are defined declaratively with annotations are validated at build time.
         * The names of tools that are added programmatically are validated at runtime.
         * <p>
         * Typically, the pattern {@code ^[A-Za-z0-9_.-]{1,128}$} follows the spec recommendations.
         *
         * @see io.quarkiverse.mcp.server.Tool#SPEC_NAME_PATTERN
         */
        Optional<Pattern> namePattern();

    }
}
