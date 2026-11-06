package io.quarkiverse.mcp.server.tasks.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.tasks.spi.TaskRecord;
import io.quarkiverse.mcp.server.tasks.spi.TaskStore;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;

/**
 * The default {@link TaskStore}; the tasks are kept in memory.
 */
@DefaultBean
@Singleton
public class InMemoryTaskStore implements TaskStore {

    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> create(TaskRecord record) {
        if (tasks.putIfAbsent(record.id(), record) != null) {
            return Uni.createFrom().failure(new IllegalStateException("Task already exists: " + record.id()));
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<TaskRecord> get(String taskId) {
        return Uni.createFrom().item(tasks.get(taskId));
    }

    @Override
    public Uni<Boolean> replace(TaskRecord expected, TaskRecord updated) {
        AtomicBoolean replaced = new AtomicBoolean();
        tasks.computeIfPresent(expected.id(), (id, current) -> {
            if (current.version() == expected.version()) {
                replaced.set(true);
                return updated;
            }
            return current;
        });
        return Uni.createFrom().item(replaced.get());
    }

    @Override
    public Uni<Boolean> delete(String taskId) {
        return Uni.createFrom().item(tasks.remove(taskId) != null);
    }

    @Override
    public Uni<Iterable<TaskRecord>> list(String serverName) {
        List<TaskRecord> ret = tasks.values().stream()
                .filter(r -> serverName == null || r.serverName().equals(serverName))
                .toList();
        return Uni.createFrom().item(ret);
    }

    @Override
    public Uni<Iterable<TaskRecord>> getAll(Collection<String> taskIds) {
        List<TaskRecord> ret = new ArrayList<>(taskIds.size());
        for (String taskId : taskIds) {
            TaskRecord record = tasks.get(taskId);
            if (record != null) {
                ret.add(record);
            }
        }
        return Uni.createFrom().item(ret);
    }

    @Override
    public Uni<Void> removeExpired(Instant now) {
        tasks.values().removeIf(r -> r.isExpired(now));
        return Uni.createFrom().voidItem();
    }

    @Override
    public boolean isShared() {
        // The tasks are only modified by this server instance, i.e. the running tasks are notified directly
        return false;
    }

}
