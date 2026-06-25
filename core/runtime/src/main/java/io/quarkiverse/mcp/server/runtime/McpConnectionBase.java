package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.vertx.core.json.JsonObject;

public abstract class McpConnectionBase implements McpConnection, Sender {

    protected final String id;

    protected final AtomicReference<Status> status;

    protected volatile InitialRequest initializeRequest;

    protected volatile LogLevel logLevel;

    protected final TrafficListeners trafficListeners;

    protected final Optional<Duration> autoPingInterval;

    protected volatile long lastUsed;

    protected final long idleTimeout;

    protected final String serverName;

    protected final boolean transientConnection;

    private volatile List<Subscription> subscriptions;

    protected McpConnectionBase(String id, McpServerRuntimeConfig serverConfig, String serverName,
            TrafficListeners trafficListeners) {
        this(id, serverConfig, serverName, trafficListeners, false);
    }

    protected McpConnectionBase(String id, McpServerRuntimeConfig serverConfig, String serverName,
            TrafficListeners trafficListeners, boolean transientConnection) {
        this.id = id;
        this.status = new AtomicReference<>(Status.NEW);
        this.logLevel = serverConfig.clientLogging().defaultLevel();
        this.trafficListeners = trafficListeners;
        this.autoPingInterval = transientConnection ? Optional.empty() : serverConfig.autoPingInterval();
        this.lastUsed = System.currentTimeMillis();
        this.idleTimeout = transientConnection ? 0 : serverConfig.connectionIdleTimeout().toMillis();
        this.serverName = serverName;
        this.transientConnection = transientConnection;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Status status() {
        return status.get();
    }

    @Override
    public InitialRequest initialRequest() {
        return initializeRequest;
    }

    public boolean initialize(InitialRequest request) {
        if (status.compareAndSet(Status.NEW, Status.INITIALIZING)) {
            this.initializeRequest = request;
            return true;
        }
        return false;
    }

    public boolean close() {
        return status.compareAndSet(Status.IN_OPERATION, Status.CLOSED);
    }

    @Override
    public LogLevel logLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel level) {
        this.logLevel = level;
    }

    @Override
    public String serverName() {
        return serverName;
    }

    @Override
    public boolean isTransient() {
        return transientConnection;
    }

    public boolean setInitialized() {
        return status.compareAndSet(Status.INITIALIZING, Status.IN_OPERATION);
    }

    public Optional<Duration> autoPingInterval() {
        return autoPingInterval;
    }

    public McpConnectionBase touch() {
        this.lastUsed = System.currentTimeMillis();
        return this;
    }

    public boolean isIdleTimeoutExpired() {
        if (idleTimeout <= 0) {
            return false;
        }
        return System.currentTimeMillis() - lastUsed > idleTimeout;
    }

    protected void messageSent(JsonObject message) {
        trafficListeners.messageSent(message, this);
    }

    TrafficListeners trafficListeners() {
        return trafficListeners;
    }

    public void addSubscription(Subscription subscription) {
        List<Subscription> subs = this.subscriptions;
        if (subs == null) {
            synchronized (this) {
                subs = this.subscriptions;
                if (subs == null) {
                    subs = new CopyOnWriteArrayList<>();
                    this.subscriptions = subs;
                }
            }
        }
        subs.add(subscription);
    }

    public boolean removeSubscription(Object subscriptionId) {
        List<Subscription> subs = this.subscriptions;
        if (subs == null) {
            return false;
        }
        return subs.removeIf(s -> s.subscriptionId().equals(subscriptionId));
    }

    public boolean supportsSubscriptionsListen() {
        InitialRequest ir = initializeRequest;
        return ir != null && ir.protocolVersion() != null && ir.protocolVersion().isStateless();
    }

    /**
     * Delivers a notification to all matching subscriptions on this connection.
     * For each matching subscription, the notification is cloned, the {@code subscriptionId} is injected
     * into {@code _meta}, and the notification is delivered via {@link #deliverSubscriptionNotification}.
     *
     * @param notification the notification to deliver
     * @param resourceUri the resource URI (for {@code notifications/resources/updated}), or {@code null}
     */
    public void sendNotification(JsonObject notification, String resourceUri) {
        List<Subscription> subs = this.subscriptions;
        if (subs == null || subs.isEmpty()) {
            return;
        }
        String method = notification.getString("method");
        for (Subscription sub : subs) {
            if (sub.filter().matches(method, resourceUri)) {
                JsonObject clone = notification.copy();
                injectSubscriptionId(clone, sub.subscriptionId());
                deliverSubscriptionNotification(clone, sub);
            }
        }
    }

    protected void deliverSubscriptionNotification(JsonObject notification, Subscription subscription) {
        send(notification);
    }

    public static void injectSubscriptionId(JsonObject notification, Object subscriptionId) {
        JsonObject params = notification.getJsonObject("params");
        if (params == null) {
            params = new JsonObject();
            notification.put("params", params);
        }
        JsonObject meta = params.getJsonObject("_meta");
        if (meta == null) {
            meta = new JsonObject();
            params.put("_meta", meta);
        }
        meta.put(MetaKey.SUBSCRIPTION_ID.toString(), subscriptionId);
    }

    @Override
    public String toString() {
        return "McpConnection [id=" + id + ", status=" + status + "]";
    }

}
