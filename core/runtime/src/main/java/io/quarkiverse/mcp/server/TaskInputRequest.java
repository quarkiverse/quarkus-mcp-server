package io.quarkiverse.mcp.server;

import java.util.Map;

import io.quarkiverse.mcp.server.InputRequiredException.InputRequestEntry;
import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.Uni;

/**
 * A request for input from the client, sent while a {@linkplain Task task-augmented} tool is executed as a task.
 * <p>
 * When the request is {@linkplain #send() sent}, the task moves to the {@link TaskStatus#INPUT_REQUIRED} status and the
 * outstanding {@code inputRequests} are included in the {@code tasks/get} responses. The client provides the responses via
 * the {@code inputResponses} of one or more {@code tasks/update} requests; once all outstanding requests are fulfilled, the
 * task moves back to {@link TaskStatus#WORKING} and the returned {@link Uni} completes with the {@link InputResponses}.
 * <p>
 * Each request key must be unique over the lifetime of the task.
 *
 * @see TaskContext#inputRequestBuilder()
 */
public interface TaskInputRequest {

    /**
     * @return the input requests keyed by server-assigned identifiers
     */
    Map<String, InputRequestEntry> inputRequests();

    /**
     * Sends the request, i.e. moves the task to the {@link TaskStatus#INPUT_REQUIRED} status.
     * <p>
     * The returned {@link Uni} completes when the client fulfills all the outstanding input requests via {@code tasks/update}.
     * It fails with {@link Cancellation.OperationCancellationException} if the task is cancelled in the meantime.
     *
     * @return a new {@link Uni} that completes with the input responses
     * @throws IllegalArgumentException if a request key was already used during the lifetime of the task
     * @throws IllegalStateException if the task is in a terminal status or another input request is pending
     */
    @CheckReturnValue
    Uni<InputResponses> send();

    /**
     * Sends the request and blocks until the client fulfills all the outstanding input requests. It must not be called on an
     * event loop thread.
     *
     * @return the input responses
     * @see #send()
     */
    default InputResponses sendAndAwait() {
        return send().await().indefinitely();
    }

    /**
     * @see TaskContext#inputRequestBuilder()
     */
    interface Builder {

        /**
         * Adds an elicitation (form mode) input request.
         *
         * @param key the server-assigned identifier
         * @param request the elicitation request built via {@link Elicitation#requestBuilder()}
         * @return self
         */
        Builder addElicitationRequest(String key, ElicitationRequest request);

        /**
         * Adds an elicitation (URL mode) input request.
         *
         * @param key the server-assigned identifier
         * @param request the URL elicitation request built via {@link Elicitation#urlRequestBuilder()}
         * @return self
         */
        Builder addUrlElicitationRequest(String key, UrlElicitationRequest request);

        /**
         * Adds a sampling input request.
         *
         * @param key the server-assigned identifier
         * @param request the sampling request built via {@link Sampling#requestBuilder()}
         * @return self
         */
        Builder addSamplingRequest(String key, SamplingRequest request);

        /**
         * Adds a {@code roots/list} input request.
         *
         * @param key the server-assigned identifier
         * @return self
         */
        Builder addRootsRequest(String key);

        /**
         * @return a new input request
         * @throws IllegalStateException if no input request was added
         */
        TaskInputRequest build();

    }

}
