package io.quarkiverse.mcp.server.tasks.spi;

import java.time.Instant;
import java.util.Set;

import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.Uni;

/**
 * Persists the state of the tasks created via {@link io.quarkiverse.mcp.server.tasks.Tasks}.
 * <p>
 * By default, the tasks are kept in memory. An application may provide a custom implementation as a CDI bean (e.g.
 * {@code @Singleton}) to keep the tasks in a persistent/shared storage, such as Redis or a database. In that case, the tasks
 * survive a restart of the server instance and the {@code tasks/get}, {@code tasks/update} and {@code tasks/cancel} requests
 * can be served by any server instance that shares the storage.
 * <p>
 * The store only keeps the state of a task. The task handler is always executed by the server instance that created the task.
 * If the store is {@linkplain #isShared() shared}, the executing instance checks the store periodically - at the polling
 * interval of the task - so that a cancellation or input responses received by another instance are propagated to the
 * handler. The running tasks are fetched in batches via {@link #getAll(Set)}.
 * <p>
 * The state is represented by an immutable {@link TaskRecord}. The store may persist it in any form, e.g. as table columns,
 * hash fields or a JSON document; see {@link TaskRecord} for details.
 * <p>
 * All methods are asynchronous and must not block the caller thread unless blocking is allowed; they may be invoked on a
 * Vert.x event loop thread as well as on a worker thread. A blocking implementation (e.g. JDBC-based) can use
 * {@link io.quarkus.runtime.BlockingOperationControl#isBlockingAllowed()} to detect if blocking is allowed on the current
 * thread, and if not, offload the work to a worker thread via
 * {@link io.quarkus.vertx.VertxContextSupport#executeBlocking(java.util.concurrent.Callable)}. The check should be
 * performed when the returned {@link Uni} is subscribed, not when it's created:
 *
 * <pre>
 * &#64;Override
 * public Uni&lt;TaskRecord&gt; get(String taskId) {
 *     return blocking(() -&gt; findTaskInDatabase(taskId));
 * }
 *
 * &lt;T&gt; Uni&lt;T&gt; blocking(Callable&lt;T&gt; action) {
 *     return Uni.createFrom().deferred(() -&gt; {
 *         if (BlockingOperationControl.isBlockingAllowed()) {
 *             try {
 *                 return Uni.createFrom().item(action.call());
 *             } catch (Exception e) {
 *                 return Uni.createFrom().failure(e);
 *             }
 *         }
 *         return VertxContextSupport.executeBlocking(action);
 *     });
 * }
 * </pre>
 *
 * @see TaskRecord
 * @see TaskManager
 */
public interface TaskStore {

    /**
     * Stores a new task. The returned {@link Uni} must complete once the task is durably stored; the {@code CreateTaskResult}
     * is not sent to the client before that.
     *
     * @param record the new task
     * @return a new {@link Uni} that completes once the task is stored
     */
    @CheckReturnValue
    Uni<Void> create(TaskRecord record);

    /**
     * @param taskId the task id
     * @return a new {@link Uni} that completes with the task, or with {@code null} if no such task exists
     */
    @CheckReturnValue
    Uni<TaskRecord> get(String taskId);

    /**
     * Obtains multiple tasks in a single batch, e.g. via {@code SELECT ... WHERE id IN (...)} or Redis {@code MGET}.
     * <p>
     * If the store is {@linkplain #isShared() shared}, the executing server instance uses this method to check the state of its
     * running tasks periodically. The method is never invoked if the store is not shared; in that case, the implementation
     * may simply return a failed {@link Uni}, e.g. with an {@link UnsupportedOperationException}.
     *
     * @param taskIds the unmodifiable set of task ids, never empty
     * @return a new {@link Uni} that completes with the tasks that exist, in any order; a missing task is simply not included
     */
    @CheckReturnValue
    Uni<Iterable<TaskRecord>> getAll(Set<String> taskIds);

    /**
     * Atomically replaces the stored task with the updated one, if and only if the {@link TaskRecord#version()} of the stored
     * task equals the version of the {@code expected} task (compare-and-set).
     *
     * @param expected the task as previously obtained from this store
     * @param updated the updated task; its version is always greater than the version of {@code expected}
     * @return a new {@link Uni} that completes with {@code true} if the task was replaced, or with {@code false} if the stored
     *         task was modified in the meantime or does not exist anymore
     */
    @CheckReturnValue
    Uni<Boolean> replace(TaskRecord expected, TaskRecord updated);

    /**
     * @param taskId the task id
     * @return a new {@link Uni} that completes with {@code true} if the task was removed, or with {@code false} if no such task
     *         exists
     */
    @CheckReturnValue
    Uni<Boolean> delete(String taskId);

    /**
     * @param serverName the name of the server configuration, or {@code null} to list the tasks of all servers
     * @return a new {@link Uni} that completes with the tasks bound to the given server
     */
    @CheckReturnValue
    Uni<Iterable<TaskRecord>> list(String serverName);

    /**
     * Removes the tasks whose time-to-live elapsed. This method is invoked periodically. A store that removes the expired tasks
     * automatically (e.g. via a native TTL support) does not need to implement this method.
     *
     * @param now the current time
     * @return a new {@link Uni} that completes once the expired tasks are removed
     * @see TaskRecord#isExpired(Instant)
     */
    @CheckReturnValue
    default Uni<Void> removeExpired(Instant now) {
        return Uni.createFrom().voidItem();
    }

    /**
     * A shared store may be modified by multiple server instances. In that case, the executing server instance checks the
     * state of its running tasks periodically (see {@link #getAll(Set)}) so that the modifications made by other
     * instances are propagated to the task handlers.
     * <p>
     * A store that is never modified by another server instance (e.g. the default in-memory store) should return
     * {@code false} so that the periodic checks are skipped.
     *
     * @return {@code true} if the store may be modified by another server instance
     */
    default boolean isShared() {
        return true;
    }

}
