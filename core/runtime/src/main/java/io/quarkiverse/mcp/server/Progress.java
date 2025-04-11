package io.quarkiverse.mcp.server;

import java.util.Optional;

/**
 * If an MCP client specifies the progress token in a request, then the server can send progress notification messages back to
 * the client.
 * <p>
 * {@link Tool}, {@link Prompt}, {@link Resource}, {@link ResourceTemplate}, {@link CompletePrompt} and
 * {@link CompleteResourceTemplate} methods can accept this class as a parameter. It will be automatically injected before the
 * method is invoked.
 */
public interface Progress {

    /**
     *
     * @return the progress token if present
     */
    Optional<ProgressToken> token();

    /**
     * {@link ProgressNotification} can be be used to send a notification message.
     *
     * @throws IllegalStateException if the token is not present
     * @return a new notification builder
     */
    ProgressNotification.Builder notificationBuilder();

    /**
     * {@link ProgressTracker} is a stateful thread-safe construct that can be be used to update the progress status and send
     * notification messages in one step.
     *
     * @throws IllegalStateException if the token is not present
     * @return a new tracker builder
     */
    ProgressTracker.Builder trackerBuilder();

}
