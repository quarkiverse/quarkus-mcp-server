package io.quarkiverse.mcp.server;

/**
 * Represents the task a {@linkplain Task task-augmented} tool is executed as.
 * <p>
 * A {@code TaskContext} parameter may be declared by any {@link Tool} method annotated with {@link Task}. The context makes
 * it possible to:
 * <ul>
 * <li>report progress to the client via the {@linkplain #setStatusMessage(String) status message},</li>
 * <li>request input from the client (elicitation, sampling or roots) while the task is running - see
 * {@link #inputRequestBuilder()}.</li>
 * </ul>
 * <p>
 * A task-augmented tool is executed synchronously if the calling client did not declare the tasks extension capability (and
 * {@link Task#required()} is {@code false}). In that case {@link #isTaskAugmented()} returns {@code false},
 * {@link #setStatusMessage(String)} is a no-op and {@link #inputRequestBuilder()} throws an {@link IllegalStateException}.
 * <p>
 * Client-initiated cancellation of a task ({@code tasks/cancel}) is observed via a {@link Cancellation} parameter, exactly as
 * for a synchronous tool call.
 *
 * @see Task
 * @see TaskManager
 */
public interface TaskContext {

    /**
     * @return {@code true} if the current tool call is executed as a task, {@code false} if it is executed synchronously
     */
    boolean isTaskAugmented();

    /**
     * @return the task id, or {@code null} if the tool is not executed as a task
     */
    String id();

    /**
     * @return the current status of the task, or {@code null} if the tool is not executed as a task
     */
    TaskStatus status();

    /**
     * @return the current status message, or {@code null}
     */
    String statusMessage();

    /**
     * Sets the message describing the current state of the task; it is included in the {@code tasks/get} responses and
     * {@code notifications/tasks} notifications. This is a no-op if the tool is not executed as a task.
     *
     * @param statusMessage the status message, may be {@code null}
     */
    void setStatusMessage(String statusMessage);

    /**
     * Returns a new builder of a request for input from the client. When the request is
     * {@linkplain TaskInputRequest#send() sent}, the task moves to the {@link TaskStatus#INPUT_REQUIRED} status and the
     * client fulfills the outstanding {@code inputRequests} with one or more {@code tasks/update} requests.
     * <p>
     * The individual elicitation and sampling requests are built via {@link Elicitation#requestBuilder()},
     * {@link Elicitation#urlRequestBuilder()} and {@link Sampling#requestBuilder()} respectively; they must not be
     * {@linkplain ElicitationRequest#send() sent} directly.
     *
     * @return a new builder
     * @throws IllegalStateException if the tool is not executed as a task
     * @see TaskInputRequest
     */
    TaskInputRequest.Builder inputRequestBuilder();

}
