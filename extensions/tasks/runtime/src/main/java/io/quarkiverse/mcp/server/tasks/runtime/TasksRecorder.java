package io.quarkiverse.mcp.server.tasks.runtime;

import java.time.Duration;
import java.util.Set;

import io.quarkiverse.mcp.server.tasks.TaskOptions;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class TasksRecorder {

    /**
     * Registers the task options of a tool annotated with {@code @Task}.
     *
     * @param ttlMs the TTL in milliseconds, or {@code null} to use the default
     * @param pollIntervalMs the polling interval in milliseconds, or {@code null} to use the default
     */
    public void registerTaskOptions(String toolName, Set<String> serverNames, boolean required, Long ttlMs,
            Long pollIntervalMs) {
        TaskOptions options = new TaskOptions(required, ttlMs != null ? Duration.ofMillis(ttlMs) : null,
                pollIntervalMs != null ? Duration.ofMillis(pollIntervalMs) : null);
        TaskManagerImpl manager = Arc.container().instance(TaskManagerImpl.class).get();
        for (String serverName : serverNames) {
            manager.setTaskOptions(toolName, serverName, options);
        }
    }

}
