package io.quarkiverse.mcp.server.runtime;

import java.math.BigDecimal;
import java.util.Objects;

import io.quarkiverse.mcp.server.ProgressNotification;
import io.quarkiverse.mcp.server.ProgressToken;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class ProgressNotificationImpl implements ProgressNotification {

    private final Sender sender;
    private final ProgressToken token;
    private final BigDecimal total;
    private final BigDecimal progress;
    private final String message;

    public ProgressNotificationImpl(Sender sender, ProgressToken token, BigDecimal total, BigDecimal progress,
            String message) {
        this.sender = Objects.requireNonNull(sender);
        this.token = Objects.requireNonNull(token);
        this.progress = Objects.requireNonNull(progress);
        this.total = total;
        this.message = message;
    }

    @Override
    public ProgressToken token() {
        return token;
    }

    @Override
    public BigDecimal total() {
        return total;
    }

    @Override
    public BigDecimal progress() {
        return progress;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public void sendAndForget() {
        doSend();
    }

    @Override
    public Uni<Void> send() {
        return Uni.createFrom().completionStage(() -> doSend().toCompletionStage());
    }

    private Future<Void> doSend() {
        JsonObject notification = new JsonObject()
                .put("progressToken", token.value());
        putDecimal(notification, "progress", progress);
        if (total != null) {
            putDecimal(notification, "total", total);
        }
        if (message != null) {
            notification.put("message", message);
        }
        return sender.send(Messages.newNotification(McpMessageHandler.NOTIFICATIONS_PROGRESS,
                notification));
    }

    private void putDecimal(JsonObject obj, String name, BigDecimal value) {
        if (value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            obj.put(name, value);
        } else {
            obj.put(name, value.doubleValue());
        }
    }

}
