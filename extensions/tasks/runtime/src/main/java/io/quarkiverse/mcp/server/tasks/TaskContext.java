package io.quarkiverse.mcp.server.tasks;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.Sampling;

/**
 * Represents the task a {@linkplain Tasks.TaskDefinition task handler} is executed as.
 * <p>
 * The context makes it possible to:
 * <ul>
 * <li>report progress to the client via the {@linkplain #setStatusMessage(String) status message},</li>
 * <li>request input from the client (elicitation, sampling or roots) while the task is running - see
 * {@link #inputRequestBuilder()},</li>
 * <li>observe a cancellation requested by the client via {@code tasks/cancel} - see {@link #cancellation()}.</li>
 * </ul>
 *
 * @see Tasks
 * @see TaskManager
 */
public interface TaskContext {

    /**
     * @return the task id
     */
    String id();

    /**
     * @return the current status of the task
     */
    TaskStatus status();

    /**
     * @return the current status message, or {@code null}
     */
    String statusMessage();

    /**
     * Sets the message describing the current state of the task; it is included in the {@code tasks/get} responses.
     *
     * @param statusMessage the status message, may be {@code null}
     */
    void setStatusMessage(String statusMessage);

    /**
     * Returns the cancellation of this task. A {@code tasks/cancel} request moves the task to the
     * {@link TaskStatus#CANCELLED} status immediately; the handler is not forced to stop but it should check the
     * cancellation and skip the remaining work, e.g. via {@link Cancellation#skipProcessingIfCancelled()}.
     *
     * @return the cancellation of this task
     */
    Cancellation cancellation();

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
     * @see TaskInputRequest
     */
    TaskInputRequest.Builder inputRequestBuilder();

}
