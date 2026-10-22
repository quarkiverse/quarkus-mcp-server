package io.quarkiverse.mcp.server.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.tasks.TaskManager.TaskInfo;
import io.vertx.core.json.JsonObject;

class CreateTaskExceptionTest {

    @Test
    void testNoStackTrace() {
        // CreateTaskException is a control-flow signal (the task is already created and running); it should skip the
        // costly native stack walk
        CreateTaskException e = new CreateTaskException(new TestTaskInfo(), new JsonObject());

        assertEquals(0, e.getStackTrace().length, "CreateTaskException should not fill in a stack trace");
    }

    // Minimal stub - CreateTaskException only reads id() during construction
    static final class TestTaskInfo implements TaskInfo {

        @Override
        public String id() {
            return "test-task";
        }

        @Override
        public String serverName() {
            return null;
        }

        @Override
        public String toolName() {
            return null;
        }

        @Override
        public TaskStatus status() {
            return null;
        }

        @Override
        public String statusMessage() {
            return null;
        }

        @Override
        public Instant createdAt() {
            return null;
        }

        @Override
        public Instant lastUpdatedAt() {
            return null;
        }

        @Override
        public Optional<Duration> ttl() {
            return Optional.empty();
        }

        @Override
        public Duration pollInterval() {
            return null;
        }
    }
}
