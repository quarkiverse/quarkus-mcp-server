package io.quarkiverse.mcp.server.tasks;

/**
 * The status of a task as defined by the <a href="https://modelcontextprotocol.io/extensions/tasks/overview">MCP Tasks</a>
 * extension.
 *
 * @see Task
 * @see TaskContext
 * @see TaskManager.TaskInfo
 */
public enum TaskStatus {

    /**
     * The request is currently being processed.
     */
    WORKING("working"),
    /**
     * The server needs input from the client; the {@code tasks/get} response includes the outstanding {@code inputRequests}.
     */
    INPUT_REQUIRED("input_required"),
    /**
     * The request completed and the result is available. This includes tool calls that returned {@code isError: true}.
     */
    COMPLETED("completed"),
    /**
     * The request failed due to a JSON-RPC error during execution.
     */
    FAILED("failed"),
    /**
     * The request was cancelled before completion.
     */
    CANCELLED("cancelled");

    private final String jsonValue;

    TaskStatus(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /**
     * @return the value used on the wire, e.g. {@code input_required}
     */
    public String jsonValue() {
        return jsonValue;
    }

    /**
     * @return {@code true} for {@link #COMPLETED}, {@link #FAILED} and {@link #CANCELLED}
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * @param value the wire value
     * @return the matching status, or {@code null}
     */
    public static TaskStatus from(String value) {
        if (value == null) {
            return null;
        }
        for (TaskStatus status : values()) {
            if (status.jsonValue.equals(value)) {
                return status;
            }
        }
        return null;
    }

}
