package io.quarkiverse.mcp.server.runtime.config;

import java.util.OptionalInt;

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

    }
}
