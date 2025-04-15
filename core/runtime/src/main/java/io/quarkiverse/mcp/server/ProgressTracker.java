package io.quarkiverse.mcp.server;

import java.math.BigDecimal;
import java.util.function.Function;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.Uni;

/**
 * Tracks progress updates and sends notifications.
 * <p>
 * This construct is thread-safe and may be used concurrently.
 */
public interface ProgressTracker {

    /**
     * @return the original progress token
     */
    ProgressToken token();

    /**
     * Advance the progress by the specified value and send a {@code notifications/progress} message to the client without
     * waiting for the result.
     * <p>
     * The message can be sent asynchronously, e.g. if this method is invoked on an event loop.
     *
     * @throws IllegalStateException if the total has been set and the progress exceeds it
     */
    void advanceAndForget(BigDecimal value);

    /**
     * Advance the progress by the specified value and send a {@code notifications/progress} message to the client
     * asynchronously. Returns a failed {@link Uni} if the total has been set and the progress exceeds it.
     *
     * @return a new {@link Uni} that completes with a {@code null} item
     */
    @CheckReturnValue
    Uni<Void> advance(BigDecimal value);

    /**
     * Advance the progress and send a {@code notifications/progress} message to the client without waiting for the result.
     * <p>
     * The message can be sent asynchronously, e.g. if this method is invoked on an event loop.
     *
     * @throws IllegalStateException if the total has been set and the progress exceeds it
     */
    default void advanceAndForget() {
        advanceAndForget(step());
    }

    /**
     * Advance the progress by the specified value and send a {@code notifications/progress} message to the client without
     * waiting for the result.
     * <p>
     * The message can be sent asynchronously, e.g. if this method is invoked on an event loop.
     *
     * @throws IllegalStateException if the total has been set and the progress exceeds it
     */
    default void advanceAndForget(long value) {
        advanceAndForget(new BigDecimal(value));
    }

    /**
     * Advance the progress by the specified value and send a {@code notifications/progress} message to the client without
     * waiting for the result.
     * <p>
     * The message can be sent asynchronously, e.g. if this method is invoked on an event loop.
     *
     * @throws IllegalStateException if the total has been set and the progress exceeds it
     */
    default void advanceAndForget(double value) {
        advanceAndForget(new BigDecimal(value));
    }

    /**
     * @return the current progress value (not {@code null})
     */
    BigDecimal progress();

    /**
     * @return the total value (may be {@code null})
     */
    BigDecimal total();

    /**
     * @return the default step (not {@code null})
     */
    BigDecimal step();

    /**
     * Convenient progress tracker builder.
     */
    public interface Builder {

        /**
         * The expected total value.
         *
         * @param total
         * @return self
         */
        Builder setTotal(long total);

        /**
         * The expected total value.
         *
         * @param total
         * @return self
         */
        Builder setTotal(double total);

        /**
         * The default step is used for {@link ProgressTracker#advanceAndForget()} method.
         *
         * @param step
         * @return self
         * @see ProgressTracker#advanceAndForget()
         */
        Builder setDefaultStep(long step);

        /**
         * The default step is used for {@link ProgressTracker#advanceAndForget()} method.
         *
         * @param step
         * @return self
         * @see ProgressTracker#advanceAndForget()
         */
        Builder setDefaultStep(double step);

        /**
         * The message builder accepts the current progress value and produces a relevant notification message.
         *
         * @param messageBuilder
         * @return self
         */
        Builder setMessageBuilder(Function<BigDecimal, String> messageBuilder);

        /**
         * @return a new tracker
         */
        ProgressTracker build();

    }

}