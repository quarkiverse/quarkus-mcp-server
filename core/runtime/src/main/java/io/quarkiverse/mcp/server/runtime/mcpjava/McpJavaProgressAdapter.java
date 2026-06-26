package io.quarkiverse.mcp.server.runtime.mcpjava;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressNotification;
import org.mcpjava.server.progress.ProgressToken;
import org.mcpjava.server.progress.ProgressTracker;

import io.quarkiverse.mcp.server.runtime.ArgumentProviders;
import io.quarkiverse.mcp.server.runtime.ProgressImpl;

public class McpJavaProgressAdapter implements Progress {

    private final io.quarkiverse.mcp.server.Progress delegate;

    McpJavaProgressAdapter(io.quarkiverse.mcp.server.Progress delegate) {
        this.delegate = delegate;
    }

    public static McpJavaProgressAdapter from(ArgumentProviders argProviders) {
        return new McpJavaProgressAdapter(ProgressImpl.from(argProviders));
    }

    @Override
    public Optional<ProgressToken> token() {
        return delegate.token().map(ProgressTokenAdapter::new);
    }

    @Override
    public ProgressNotification.Builder notificationBuilder() {
        return new NotificationBuilderAdapter(delegate.notificationBuilder());
    }

    @Override
    public ProgressTracker.Builder trackerBuilder() {
        return new TrackerBuilderAdapter(delegate.trackerBuilder());
    }

    static class ProgressTokenAdapter implements ProgressToken {

        private final io.quarkiverse.mcp.server.ProgressToken delegate;

        ProgressTokenAdapter(io.quarkiverse.mcp.server.ProgressToken delegate) {
            this.delegate = delegate;
        }

        @Override
        public Type type() {
            return Type.valueOf(delegate.type().name());
        }

        @Override
        public Number asInteger() {
            return delegate.asInteger();
        }

        @Override
        public String asString() {
            return delegate.asString();
        }
    }

    static class NotificationAdapter implements ProgressNotification {

        private final io.quarkiverse.mcp.server.ProgressNotification delegate;

        NotificationAdapter(io.quarkiverse.mcp.server.ProgressNotification delegate) {
            this.delegate = delegate;
        }

        @Override
        public ProgressToken token() {
            return new ProgressTokenAdapter(delegate.token());
        }

        @Override
        public Optional<BigDecimal> total() {
            return Optional.ofNullable(delegate.total());
        }

        @Override
        public BigDecimal progress() {
            return delegate.progress();
        }

        @Override
        public Optional<String> message() {
            return Optional.ofNullable(delegate.message());
        }

        @Override
        public void sendAndForget() {
            delegate.sendAndForget();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T send() {
            return (T) delegate.send();
        }

        @Override
        public Map<String, Object> metadata() {
            return Map.of();
        }
    }

    static class NotificationBuilderAdapter implements ProgressNotification.Builder {

        private final io.quarkiverse.mcp.server.ProgressNotification.Builder delegate;

        NotificationBuilderAdapter(io.quarkiverse.mcp.server.ProgressNotification.Builder delegate) {
            this.delegate = delegate;
        }

        @Override
        public ProgressNotification.Builder setProgress(long progress) {
            delegate.setProgress(progress);
            return this;
        }

        @Override
        public ProgressNotification.Builder setProgress(double progress) {
            delegate.setProgress(progress);
            return this;
        }

        @Override
        public ProgressNotification.Builder setTotal(long total) {
            delegate.setTotal(total);
            return this;
        }

        @Override
        public ProgressNotification.Builder setTotal(double total) {
            delegate.setTotal(total);
            return this;
        }

        @Override
        public ProgressNotification.Builder setMessage(String message) {
            delegate.setMessage(message);
            return this;
        }

        @Override
        public ProgressNotification build() {
            return new NotificationAdapter(delegate.build());
        }

        @Override
        public ProgressNotification.Builder putMetadata(String key, Object value) {
            return this;
        }

        @Override
        public ProgressNotification.Builder setMetadata(Map<String, Object> metadata) {
            return this;
        }
    }

    static class TrackerAdapter implements ProgressTracker {

        private final io.quarkiverse.mcp.server.ProgressTracker delegate;

        TrackerAdapter(io.quarkiverse.mcp.server.ProgressTracker delegate) {
            this.delegate = delegate;
        }

        @Override
        public ProgressToken token() {
            return new ProgressTokenAdapter(delegate.token());
        }

        @Override
        public void advanceAndForget(BigDecimal value) {
            delegate.advanceAndForget(value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T advance(BigDecimal value) {
            return (T) delegate.advance(value);
        }

        @Override
        public BigDecimal progress() {
            return delegate.progress();
        }

        @Override
        public Optional<BigDecimal> total() {
            return Optional.ofNullable(delegate.total());
        }

        @Override
        public BigDecimal step() {
            return delegate.step();
        }
    }

    static class TrackerBuilderAdapter implements ProgressTracker.Builder {

        private final io.quarkiverse.mcp.server.ProgressTracker.Builder delegate;

        TrackerBuilderAdapter(io.quarkiverse.mcp.server.ProgressTracker.Builder delegate) {
            this.delegate = delegate;
        }

        @Override
        public ProgressTracker.Builder setTotal(long total) {
            delegate.setTotal(total);
            return this;
        }

        @Override
        public ProgressTracker.Builder setTotal(double total) {
            delegate.setTotal(total);
            return this;
        }

        @Override
        public ProgressTracker.Builder setDefaultStep(long step) {
            delegate.setDefaultStep(step);
            return this;
        }

        @Override
        public ProgressTracker.Builder setDefaultStep(double step) {
            delegate.setDefaultStep(step);
            return this;
        }

        @Override
        public ProgressTracker.Builder setMessageBuilder(Function<BigDecimal, String> messageBuilder) {
            delegate.setMessageBuilder(messageBuilder);
            return this;
        }

        @Override
        public ProgressTracker build() {
            return new TrackerAdapter(delegate.build());
        }
    }
}
