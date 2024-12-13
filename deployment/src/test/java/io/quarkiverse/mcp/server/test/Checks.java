package io.quarkiverse.mcp.server.test;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpConnection.Status;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.BlockingOperationControl;
import io.smallrye.common.vertx.VertxContext;

public class Checks {

    public static void checkRequestContext() {
        if (!Arc.container().requestContext().isActive()) {
            throw new IllegalStateException("Request context not active");
        }
    }

    public static void checkExecutionModel(boolean blocking) {
        if (BlockingOperationControl.isBlockingAllowed() && !blocking) {
            throw new IllegalStateException("Invalid execution model");
        }
    }

    public static void checkDuplicatedContext() {
        if (!VertxContext.isOnDuplicatedContext()) {
            throw new IllegalStateException("Not on duplicated context");
        }
    }

    public static void checkRequestId(RequestId id) {
        if (id == null || id.asInteger() < 1) {
            throw new IllegalStateException("Invalid request id: " + id);
        }
    }

    public static void checkMcpConnection(McpConnection connection) {
        if (connection == null || connection.status() != Status.IN_OPERATION) {
            throw new IllegalStateException("Invalid connection: " + connection);
        }
    }
}
