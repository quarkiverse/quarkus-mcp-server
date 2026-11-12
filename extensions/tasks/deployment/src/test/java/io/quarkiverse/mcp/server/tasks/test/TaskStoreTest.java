package io.quarkiverse.mcp.server.tasks.test;

import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.TASKS_CAPABILITY;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.awaitStatus;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.callToolAsTask;
import static io.quarkiverse.mcp.server.tasks.test.TaskTestSupport.taskRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.tasks.TaskManager;
import io.quarkiverse.mcp.server.tasks.TaskStatus;
import io.quarkiverse.mcp.server.tasks.Tasks;
import io.quarkiverse.mcp.server.tasks.spi.TaskRecord;
import io.quarkiverse.mcp.server.tasks.spi.TaskStore;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Tests a custom {@link TaskStore}. The store keeps the tasks as JSON documents and completes asynchronously. The
 * modifications made directly in the store simulate another server instance that shares the store.
 */
public class TaskStoreTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = defaultConfig()
            .overrideConfigKey("quarkus.mcp.server.tasks.default-poll-interval", "100ms")
            .withApplicationRoot(root -> root.addClasses(MyTools.class, JsonTaskStore.class, TaskTestSupport.class));

    @Inject
    TaskManager taskManager;

    @Inject
    TaskStore taskStore;

    @Inject
    JsonTaskStore jsonTaskStore;

    @Test
    public void testCustomStore() {
        assertTrue(taskStore instanceof JsonTaskStore);
        try (var client = newClient()) {
            // Blocking tool
            String taskId = callToolAsTask(client, "simple");
            JsonObject completed = awaitStatus(client, taskId, true, TaskStatus.COMPLETED);
            assertEquals("simple:" + taskId,
                    completed.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
            JsonObject stored = jsonTaskStore.document(taskId);
            assertNotNull(stored);
            assertEquals(TaskStatus.COMPLETED.jsonValue(), stored.getString("status"));
            assertEquals("Done", stored.getString("statusMessage"));

            // Non-blocking tool
            String asyncTaskId = callToolAsTask(client, "simpleAsync");
            awaitStatus(client, asyncTaskId, true, TaskStatus.COMPLETED);

            // TaskManager
            TaskManager.TaskInfo info = taskManager.getTask(taskId).await().indefinitely();
            assertEquals("simple", info.toolName());
            assertEquals(TaskStatus.COMPLETED, info.status());
            List<String> ids = new ArrayList<>();
            taskManager.getTasks().await().indefinitely().forEach(t -> ids.add(t.id()));
            assertTrue(ids.contains(taskId));
            assertTrue(ids.contains(asyncTaskId));
        }
    }

    @Test
    public void testCancelledByAnotherInstance() throws InterruptedException {
        try (var client = newClient()) {
            MyTools.reset();
            String taskId = callToolAsTask(client, "cancellable");
            assertTrue(MyTools.STARTED.await(5, TimeUnit.SECONDS));

            // Another instance cancels the task, i.e. the local task is not notified directly
            remoteUpdate(taskId, r -> r.toBuilder()
                    .status(TaskStatus.CANCELLED)
                    .statusMessage("Cancelled remotely")
                    .cancellationReason("Cancelled remotely"));

            // The executing instance observes the cancellation within the poll interval
            assertTrue(MyTools.CANCELLED.await(5, TimeUnit.SECONDS));
            assertEquals(Optional.of("Cancelled remotely"), MyTools.REASON.get());
            assertTrue(MyTools.FINISHED.await(5, TimeUnit.SECONDS));
            JsonObject cancelled = awaitStatus(client, taskId, true, TaskStatus.CANCELLED);
            assertEquals("Cancelled remotely", cancelled.getString("statusMessage"));
            assertFalse(cancelled.containsKey("result"));
        }
    }

    @Test
    public void testInputResponsesFromAnotherInstance() {
        try (var client = newClient()) {
            String taskId = callToolAsTask(client, "listRoots");
            JsonObject inputRequired = awaitStatus(client, taskId, true, TaskStatus.INPUT_REQUIRED);
            assertEquals("roots/list",
                    inputRequired.getJsonObject("inputRequests").getJsonObject("roots").getString("method"));

            // Another instance receives the tasks/update request
            remoteUpdate(taskId, r -> r.toBuilder()
                    .status(TaskStatus.WORKING)
                    .outstandingInputRequestKeys(List.of())
                    .inputResponses(new JsonObject().put("roots", new JsonObject().put("roots",
                            new JsonArray().add(new JsonObject().put("uri", "file:///remote").put("name", "remote"))))));

            JsonObject completed = awaitStatus(client, taskId, true, TaskStatus.COMPLETED);
            assertEquals("roots:file:///remote",
                    completed.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
        }
    }

    @Test
    public void testConcurrentModification() throws InterruptedException {
        try (var client = newClient()) {
            MyTools.reset();
            String taskId = callToolAsTask(client, "cancellable");
            assertTrue(MyTools.STARTED.await(5, TimeUnit.SECONDS));

            // The next three replace attempts detect a concurrent modification
            jsonTaskStore.conflicts.set(3);
            client.when()
                    .message(taskRequest(client, "tasks/cancel", taskId, true))
                    .withAssert(response -> assertNotNull(response.getJsonObject("result")))
                    .send()
                    .thenAssertResults();
            assertEquals(0, jsonTaskStore.conflicts.get());
            assertTrue(MyTools.CANCELLED.await(5, TimeUnit.SECONDS));
            JsonObject cancelled = awaitStatus(client, taskId, true, TaskStatus.CANCELLED);
            assertEquals("Cancellation requested by the client", cancelled.getString("statusMessage"));
        }
    }

    @Test
    public void testCancelSingleRead() {
        try (var client = newClient()) {
            MyTools.reset();
            // The handler is blocked and does not access the store in the meantime
            String taskId = callToolAsTask(client, "gated");
            assertTrue(awaitLatch(MyTools.STARTED));
            try {
                // The task obtained by tasks/cancel is not fetched again
                int getsBefore = jsonTaskStore.gets.get();
                client.when()
                        .message(taskRequest(client, "tasks/cancel", taskId, true))
                        .withAssert(response -> assertNotNull(response.getJsonObject("result")))
                        .send()
                        .thenAssertResults();
                assertEquals(1, jsonTaskStore.gets.get() - getsBefore);
            } finally {
                MyTools.GATE.countDown();
            }
            awaitStatus(client, taskId, true, TaskStatus.CANCELLED);
        }
    }

    @Test
    public void testCancelRemovedTask() throws InterruptedException {
        try (var client = newClient()) {
            MyTools.reset();
            String taskId = callToolAsTask(client, "cancellable");
            assertTrue(MyTools.STARTED.await(5, TimeUnit.SECONDS));

            // The task is removed by another instance after it was obtained by tasks/cancel
            jsonTaskStore.deleteBeforeReplace.set(true);
            client.when()
                    .message(taskRequest(client, "tasks/cancel", taskId, true))
                    .withErrorAssert(e -> {
                        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.code());
                        assertTrue(e.message().endsWith("Task not found"), e.message());
                    })
                    .send()
                    .thenAssertResults();
            // The executing instance observes the removal within the poll interval
            assertTrue(MyTools.CANCELLED.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testFinalStateRetried() {
        try (var client = newClient()) {
            MyTools.reset();
            String taskId = callToolAsTask(client, "gated");
            assertTrue(awaitLatch(MyTools.STARTED));

            // The first two attempts to store the result fail
            jsonTaskStore.replaceFailures.set(2);
            MyTools.GATE.countDown();
            JsonObject completed = awaitStatus(client, taskId, true, TaskStatus.COMPLETED);
            assertEquals("gated",
                    completed.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
            assertEquals(0, jsonTaskStore.replaceFailures.get());
        }
    }

    @Test
    public void testFinalStateFallback() {
        try (var client = newClient()) {
            MyTools.reset();
            String taskId = callToolAsTask(client, "gated");
            assertTrue(awaitLatch(MyTools.STARTED));

            // The result can never be stored - the task is marked as failed
            jsonTaskStore.rejectCompleted = true;
            try {
                MyTools.GATE.countDown();
                JsonObject failed = awaitStatus(client, taskId, true, TaskStatus.FAILED);
                assertEquals("Internal error", failed.getString("statusMessage"));
            } finally {
                jsonTaskStore.rejectCompleted = false;
            }
        }
    }

    @Test
    public void testRunningTasksFetchedInBatch() {
        try (var client = newClient()) {
            // Both tasks wait for input
            String taskId1 = callToolAsTask(client, "listRoots");
            String taskId2 = callToolAsTask(client, "listRoots");
            awaitStatus(client, taskId1, true, TaskStatus.INPUT_REQUIRED);
            awaitStatus(client, taskId2, true, TaskStatus.INPUT_REQUIRED);

            // The running tasks are checked periodically via TaskStore#getAll(); eventually both in a single batch
            jsonTaskStore.batches.clear();
            long deadline = System.currentTimeMillis() + 5_000;
            while (jsonTaskStore.batches.stream().noneMatch(b -> b.containsAll(List.of(taskId1, taskId2)))) {
                if (System.currentTimeMillis() > deadline) {
                    throw new AssertionError("Tasks not fetched in a single batch: " + jsonTaskStore.batches);
                }
                sleep(50);
            }

            // Another instance cancels both tasks
            for (String taskId : List.of(taskId1, taskId2)) {
                remoteUpdate(taskId, r -> r.toBuilder()
                        .status(TaskStatus.CANCELLED)
                        .statusMessage("Cancelled remotely")
                        .cancellationReason("Cancelled remotely"));
            }
            awaitStatus(client, taskId1, true, TaskStatus.CANCELLED);
            awaitStatus(client, taskId2, true, TaskStatus.CANCELLED);

            // Terminal tasks are not checked anymore
            sleep(300);
            jsonTaskStore.batches.clear();
            sleep(300);
            assertTrue(jsonTaskStore.batches.stream().noneMatch(b -> b.contains(taskId1) || b.contains(taskId2)),
                    jsonTaskStore.batches::toString);
        }
    }

    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private McpStreamableTestClient newClient() {
        return McpAssured.newStreamableClient()
                .setStateless()
                .setAdditionalHeaders(TaskTestSupport::taskHeaders)
                .setClientCapabilities(TASKS_CAPABILITY)
                .build()
                .connect();
    }

    /**
     * Simulates a state transition applied by another server instance.
     */
    private void remoteUpdate(String taskId, Function<TaskRecord, TaskRecord.Builder> transition) {
        for (int i = 0; i < 10; i++) {
            TaskRecord current = taskStore.get(taskId).await().indefinitely();
            assertNotNull(current);
            TaskRecord updated = transition.apply(current)
                    .version(current.version() + 1)
                    .lastUpdatedAt(Instant.now())
                    .build();
            if (taskStore.replace(current, updated).await().indefinitely()) {
                return;
            }
        }
        throw new AssertionError("Unable to update task " + taskId);
    }

    /**
     * Keeps the tasks as JSON documents; all operations complete asynchronously on a worker thread.
     */
    @Singleton
    public static class JsonTaskStore implements TaskStore {

        private final ConcurrentMap<String, JsonObject> documents = new ConcurrentHashMap<>();

        // The number of replace attempts that should detect a concurrent modification
        final AtomicInteger conflicts = new AtomicInteger();

        // The number of replace attempts that should fail, e.g. due to a short outage of the store
        final AtomicInteger replaceFailures = new AtomicInteger();

        // If true, the transition to the completed status always fails
        volatile boolean rejectCompleted;

        // If true, the task is removed right before the next replace attempt, e.g. by another server instance
        final AtomicBoolean deleteBeforeReplace = new AtomicBoolean();

        // The number of get() invocations
        final AtomicInteger gets = new AtomicInteger();

        // The ids passed to getAll()
        final List<Set<String>> batches = new CopyOnWriteArrayList<>();

        JsonObject document(String taskId) {
            JsonObject document = documents.get(taskId);
            return document != null ? document.copy() : null;
        }

        @Override
        public Uni<Void> create(TaskRecord record) {
            return async(() -> {
                if (documents.putIfAbsent(record.id(), record.toJson()) != null) {
                    throw new IllegalStateException("Task already exists: " + record.id());
                }
                return null;
            });
        }

        @Override
        public Uni<TaskRecord> get(String taskId) {
            gets.incrementAndGet();
            return async(() -> {
                JsonObject document = documents.get(taskId);
                return document != null ? TaskRecord.fromJson(document) : null;
            });
        }

        @Override
        public Uni<Iterable<TaskRecord>> getAll(Set<String> taskIds) {
            batches.add(Set.copyOf(taskIds));
            return async(() -> {
                List<TaskRecord> ret = new ArrayList<>();
                for (String taskId : taskIds) {
                    JsonObject document = documents.get(taskId);
                    if (document != null) {
                        ret.add(TaskRecord.fromJson(document));
                    }
                }
                return ret;
            });
        }

        @Override
        public Uni<Boolean> replace(TaskRecord expected, TaskRecord updated) {
            return async(() -> {
                if (replaceFailures.getAndUpdate(c -> c > 0 ? c - 1 : 0) > 0) {
                    throw new IllegalStateException("Store unavailable");
                }
                if (rejectCompleted && updated.status() == TaskStatus.COMPLETED) {
                    throw new IllegalStateException("Completed status rejected");
                }
                if (deleteBeforeReplace.compareAndSet(true, false)) {
                    documents.remove(expected.id());
                }
                AtomicBoolean replaced = new AtomicBoolean();
                documents.computeIfPresent(expected.id(), (id, current) -> {
                    if (current.getLong("version") != expected.version()) {
                        return current;
                    }
                    if (conflicts.getAndUpdate(c -> c > 0 ? c - 1 : 0) > 0) {
                        // Simulate a concurrent modification
                        return current.copy().put("version", expected.version() + 1);
                    }
                    replaced.set(true);
                    return updated.toJson();
                });
                return replaced.get();
            });
        }

        @Override
        public Uni<Boolean> delete(String taskId) {
            return async(() -> documents.remove(taskId) != null);
        }

        @Override
        public Uni<Iterable<TaskRecord>> list(String serverName) {
            return async(() -> documents.values().stream()
                    .map(TaskRecord::fromJson)
                    .filter(r -> serverName == null || r.serverName().equals(serverName))
                    .toList());
        }

        @Override
        public Uni<Void> removeExpired(Instant now) {
            return async(() -> {
                documents.values().removeIf(d -> TaskRecord.fromJson(d).isExpired(now));
                return null;
            });
        }

        private static <T> Uni<T> async(Supplier<T> supplier) {
            return Uni.createFrom().item(supplier).emitOn(Infrastructure.getDefaultWorkerPool());
        }

    }

    public static class MyTools {

        static volatile CountDownLatch STARTED;
        static volatile CountDownLatch CANCELLED;
        static volatile CountDownLatch FINISHED;
        static volatile CountDownLatch GATE;
        static final AtomicReference<Optional<String>> REASON = new AtomicReference<>();

        static void reset() {
            STARTED = new CountDownLatch(1);
            CANCELLED = new CountDownLatch(1);
            FINISHED = new CountDownLatch(1);
            GATE = new CountDownLatch(1);
            REASON.set(null);
        }

        @Tool(description = "A task that waits until the gate is open")
        String gated(Tasks tasks) {
            throw tasks.newTask().setHandler(task -> {
                STARTED.countDown();
                if (!awaitLatch(GATE)) {
                    throw new IllegalStateException("Gate not open");
                }
                return ToolResponse.success("gated");
            }, false).createAndAwait();
        }

        @Tool(description = "A simple task")
        String simple(Tasks tasks) {
            throw tasks.newTask().setHandler(task -> {
                task.setStatusMessageAndAwait("Done");
                // The message is stored once the Uni completes, even if the store is asynchronous
                assertEquals("Done", task.statusMessage());
                return ToolResponse.success("simple:" + task.id());
            }, false).createAndAwait();
        }

        @Tool(description = "A simple non-blocking task")
        Uni<String> simpleAsync(Tasks tasks) {
            return tasks.newTask()
                    .setAsyncHandler(task -> Uni.createFrom().item(ToolResponse.success("async:" + task.id())))
                    .create();
        }

        @Tool(description = "A cancellable task")
        String cancellable(Tasks tasks) {
            throw tasks.newTask().setHandler(task -> {
                task.cancellation().onCancelled(reason -> {
                    REASON.set(reason);
                    CANCELLED.countDown();
                });
                STARTED.countDown();
                long deadline = System.currentTimeMillis() + 10_000;
                while (!task.cancellation().check().isRequested() && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
                FINISHED.countDown();
                return ToolResponse.success("finished");
            }, false).createAndAwait();
        }

        @Tool(description = "A task that requests the roots")
        String listRoots(Tasks tasks) {
            throw tasks.newTask().setHandler(task -> {
                InputResponses responses = task.inputRequestBuilder()
                        .addRootsRequest("roots")
                        .build()
                        .sendAndAwait();
                return ToolResponse.success("roots:" + responses.getRootsResponse("roots").get(0).uri());
            }, false).createAndAwait();
        }

    }

}
