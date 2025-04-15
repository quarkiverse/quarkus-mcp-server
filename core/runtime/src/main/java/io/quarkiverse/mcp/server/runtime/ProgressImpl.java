package io.quarkiverse.mcp.server.runtime;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.ProgressNotification;
import io.quarkiverse.mcp.server.ProgressToken;
import io.quarkiverse.mcp.server.ProgressTracker;

class ProgressImpl implements Progress {

    static ProgressImpl from(ArgumentProviders argProviders) {
        ProgressToken token = null;
        if (argProviders.progressToken() != null) {
            token = new ProgressToken(argProviders.progressToken());
        }
        return new ProgressImpl(token, argProviders.sender());
    }

    private final Optional<ProgressToken> token;

    private final Sender sender;

    ProgressImpl(ProgressToken token, Sender sender) {
        this.token = Optional.ofNullable(token);
        this.sender = sender;
    }

    @Override
    public Optional<ProgressToken> token() {
        return token;
    }

    @Override
    public ProgressNotification.Builder notificationBuilder() {
        if (token.isEmpty()) {
            throw new IllegalStateException("Token not present");
        }
        return new NotificationBuilder();
    }

    @Override
    public ProgressTracker.Builder trackerBuilder() {
        if (token.isEmpty()) {
            throw new IllegalStateException("Token not present");
        }
        return new TrackerBuilder();
    }

    class TrackerBuilder implements ProgressTracker.Builder {

        private BigDecimal total;
        private BigDecimal step;
        private Function<BigDecimal, String> messageBuilder;

        @Override
        public ProgressTracker.Builder setTotal(long total) {
            this.total = new BigDecimal(total);
            return this;
        }

        @Override
        public ProgressTracker.Builder setTotal(double total) {
            this.total = new BigDecimal(total);
            return this;
        }

        @Override
        public ProgressTracker.Builder setDefaultStep(long step) {
            this.step = new BigDecimal(step);
            return this;
        }

        @Override
        public ProgressTracker.Builder setDefaultStep(double step) {
            this.step = new BigDecimal(step);
            return this;
        }

        @Override
        public ProgressTracker.Builder setMessageBuilder(Function<BigDecimal, String> messageBuilder) {
            this.messageBuilder = messageBuilder;
            return this;
        }

        @Override
        public ProgressTracker build() {
            return new ProgressTrackerImpl(sender, token.get(), total, step, messageBuilder);
        }

    }

    class NotificationBuilder implements ProgressNotification.Builder {

        private BigDecimal total;
        private BigDecimal progress;
        private String message;

        @Override
        public ProgressNotification.Builder setProgress(long progress) {
            this.progress = new BigDecimal(progress);
            return this;
        }

        @Override
        public ProgressNotification.Builder setProgress(double progress) {
            this.progress = new BigDecimal(progress);
            return this;
        }

        @Override
        public ProgressNotification.Builder setTotal(long total) {
            this.total = new BigDecimal(total);
            return this;
        }

        @Override
        public ProgressNotification.Builder setTotal(double total) {
            this.total = new BigDecimal(total);
            return this;
        }

        @Override
        public ProgressNotification.Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        @Override
        public ProgressNotification build() {
            return new ProgressNotificationImpl(sender, token.get(), total, progress, message);
        }

    }

}
