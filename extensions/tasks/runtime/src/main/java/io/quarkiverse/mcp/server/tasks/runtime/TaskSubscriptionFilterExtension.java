package io.quarkiverse.mcp.server.tasks.runtime;

import java.util.HashSet;
import java.util.Set;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.runtime.McpRequest;
import io.quarkiverse.mcp.server.runtime.SubscriptionFilterExtension;

/**
 * The {@code taskIds} filter of {@code subscriptions/listen}; routes the {@code notifications/tasks} notifications.
 */
@Singleton
public class TaskSubscriptionFilterExtension implements SubscriptionFilterExtension {

    private final TaskManagerImpl manager;

    TaskSubscriptionFilterExtension(TaskManagerImpl manager) {
        this.manager = manager;
    }

    @Override
    public String filterName() {
        return "taskIds";
    }

    @Override
    public String notificationMethod() {
        return TaskManagerImpl.NOTIFICATIONS_TASKS;
    }

    @Override
    public Set<String> accept(Set<String> taskIds, McpRequest mcpRequest) {
        // Task status notifications require the tasks extension capability
        if (!TasksExtension.supportsTasks(mcpRequest)) {
            throw TasksExtension.missingCapability();
        }
        // Only agree to notify about the tasks that actually exist
        Set<String> known = new HashSet<>();
        for (String taskId : taskIds) {
            if (manager.exists(taskId, mcpRequest.serverName())) {
                known.add(taskId);
            }
        }
        return known;
    }

}
