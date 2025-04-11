package io.quarkiverse.mcp.server.runtime;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.quarkiverse.mcp.server.ProgressToken;
import io.quarkiverse.mcp.server.ProgressTracker;
import io.smallrye.mutiny.Uni;

class ProgressTrackerImpl implements ProgressTracker {

    private final Sender sender;
    private final ProgressToken token;
    private final BigDecimal total;
    private final BigDecimal step;
    private final Function<BigDecimal, String> messageBuilder;
    private final AtomicReference<BigDecimal> val = new AtomicReference<>(BigDecimal.ZERO);

    public ProgressTrackerImpl(Sender sender, ProgressToken token, BigDecimal total, BigDecimal step,
            Function<BigDecimal, String> messageBuilder) {
        this.sender = sender;
        this.token = token;
        this.total = total;
        this.step = Objects.requireNonNullElse(step, BigDecimal.ONE);
        this.messageBuilder = messageBuilder;
    }

    @Override
    public ProgressToken token() {
        return token;
    }

    @Override
    public Uni<Void> advanceAsync(BigDecimal value) {
        Objects.requireNonNull(value);
        return Uni.createFrom().deferred(() -> {
            BigDecimal progress = val.updateAndGet(p -> {
                BigDecimal next = p.add(value);
                if (total != null && next.compareTo(total) > 0) {
                    throw new IllegalStateException("Progress exceeded the total");
                }
                return next;
            });
            String message = null;
            if (messageBuilder != null) {
                message = messageBuilder.apply(progress);
            }
            return new ProgressNotificationImpl(sender, token, total, progress, message).sendAsync();
        });
    }

    @Override
    public void advance(BigDecimal value) {
        Objects.requireNonNull(value);
        BigDecimal progress = val.updateAndGet(p -> {
            BigDecimal next = p.add(value);
            if (total != null && next.compareTo(total) > 0) {
                throw new IllegalStateException("Progress exceeded the total");
            }
            return next;
        });
        String message = null;
        if (messageBuilder != null) {
            message = messageBuilder.apply(progress);
        }
        new ProgressNotificationImpl(sender, token, total, progress, message).sendAndForget();
    }

    @Override
    public BigDecimal progress() {
        return val.get();
    }

    @Override
    public BigDecimal total() {
        return total;
    }

    @Override
    public BigDecimal step() {
        return step;
    }

}
