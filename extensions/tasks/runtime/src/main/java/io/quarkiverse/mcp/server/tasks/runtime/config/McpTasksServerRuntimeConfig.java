package io.quarkiverse.mcp.server.tasks.runtime.config;

import java.time.Duration;

import io.smallrye.config.WithDefault;

public interface McpTasksServerRuntimeConfig {

    /**
     * Tasks config.
     */
    Tasks tasks();

    interface Tasks {

        /**
         * The default time-to-live of a task created for a task-augmented tool, measured from its creation. Once elapsed,
         * the task may be discarded, including its result. Negative and zero durations imply an unlimited time-to-live.
         * It can be overridden per tool with {@code @Task#ttl()}.
         */
        @WithDefault("1h")
        Duration defaultTtl();

        /**
         * The default polling interval suggested to clients polling a task. It must be positive. It can be overridden per
         * tool with {@code @Task#pollInterval()}.
         */
        @WithDefault("5s")
        Duration defaultPollInterval();
    }

}
