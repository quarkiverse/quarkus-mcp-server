package io.quarkiverse.mcp.server;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Can be used to determine if an MCP client requested a cancellation of an in-progress request.
 * <p>
 * {@link Tool}, {@link Prompt}, {@link Resource}, {@link ResourceTemplate}, {@link CompletePrompt} and
 * {@link CompleteResourceTemplate} methods can accept this class as a parameter. It will be automatically injected before the
 * method is invoked.
 *
 * @see #check()
 */
public interface Cancellation {

    /**
     * Perform the check.
     * <p>
     * A feature method should throw an {@link OperationCancellationException} if cancellation was requested by the client.
     *
     * @return the result
     * @see Result#isRequested()
     * @see OperationCancellationException
     */
    Result check();

    /**
     * Registers an action that is executed when a cancellation of the current MCP request is requested by the client. If
     * cancellation was already requested, the action is executed immediately. Multiple actions can be registered.
     * <p>
     * The consumer receives the optional cancellation reason provided by the client.
     *
     * @param action the action to execute on cancellation, must not be {@code null}
     * @throws IllegalArgumentException if the action is {@code null}
     */
    void onCancelled(Consumer<Optional<String>> action);

    /**
     * Perform the check and if cancellation is requested then skip the processing, i.e. throw
     * {@link OperationCancellationException}.
     *
     * @throws OperationCancellationException
     */
    default void skipProcessingIfCancelled() {
        if (check().isRequested()) {
            throw new OperationCancellationException();
        }
    }

    /**
     *
     * @param isRequested {@code true} if a client wants to cancel an in-progress request
     * @param reason an optional reason for cancellation
     */
    record Result(boolean isRequested, Optional<String> reason) {
    }

    /**
     * Exception indicating that the result of an MCP request cannot be returned because the request
     * was cancelled by the client.
     */
    class OperationCancellationException extends RuntimeException {

        private static final long serialVersionUID = 1L;

    }

}
