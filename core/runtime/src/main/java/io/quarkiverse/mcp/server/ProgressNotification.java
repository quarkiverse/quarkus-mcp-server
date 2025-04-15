package io.quarkiverse.mcp.server;

import java.math.BigDecimal;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.Uni;

/**
 * Represents a {@code notifications/progress} message.
 */
public interface ProgressNotification {

    /**
     * @return the original progress token
     */
    ProgressToken token();

    /**
     * @return the total value
     */
    BigDecimal total();

    /**
     * @return the progress value
     */
    BigDecimal progress();

    /**
     * @return the message
     */
    String message();

    /**
     * Send the message to the client without waiting for the result.
     * <p>
     * The message can be sent asynchronously, e.g. if this method is invoked on an event loop.
     */
    void sendAndForget();

    /**
     * Send the message asynchronously to the client.
     *
     * @return a new {@link Uni} that completes with a {@code null} item
     */
    @CheckReturnValue
    Uni<Void> send();

    /**
     * Convenient progress notification builder.
     */
    public interface Builder {

        Builder setProgress(long total);

        Builder setProgress(double total);

        Builder setTotal(long total);

        Builder setTotal(double total);

        Builder setMessage(String message);

        ProgressNotification build();
    }

}