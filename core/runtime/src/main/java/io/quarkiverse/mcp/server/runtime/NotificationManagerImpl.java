package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.NotificationManager;
import io.quarkiverse.mcp.server.NotificationManager.NotificationInfo;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@Singleton
public class NotificationManagerImpl extends FeatureManagerBase<Void, NotificationInfo> implements NotificationManager {

    // key = notification type + "::" + name
    final ConcurrentMap<String, NotificationInfo> notifications;

    NotificationManagerImpl(McpMetadata metadata, Vertx vertx, ObjectMapper mapper, ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation, ResponseHandlers responseHandlers) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.notifications = new ConcurrentHashMap<>();
        for (FeatureMetadata<Void> notification : metadata.notifications()) {
            NotificationMethod notificationMethod = new NotificationMethod(notification);
            this.notifications.put(key(notificationMethod), notificationMethod);
        }
    }

    @Override
    Stream<NotificationInfo> infos() {
        return notifications.values().stream();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<Void> getInvoker(String id, McpRequest mcpRequest) {
        NotificationInfo init = notifications.get(id);
        if (init instanceof FeatureInvoker fi
                && matches(init, mcpRequest)) {
            return fi;
        }
        return null;
    }

    @Override
    public NotificationInfo getNotification(Type type, String name) {
        return notifications.get(key(type, name));
    }

    @Override
    public NotificationDefinition newNotification(String name) {
        return new NotificationDefinitionImpl(name);
    }

    @Override
    public boolean removeNotification(Predicate<NotificationInfo> filter) {
        return notifications.entrySet().removeIf(e -> filter.test(e.getValue()));
    }

    IllegalArgumentException notificationAlreadyExists(Type type, String name) {
        return new IllegalArgumentException("A " + type + " notification with name [" + name + "] already exits");
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Invalid notification name: " + id, JsonRPC.INVALID_PARAMS);
    }

    class NotificationMethod extends FeatureMetadataInvoker<Void> implements NotificationManager.NotificationInfo {

        private final Notification.Type type;

        private NotificationMethod(FeatureMetadata<Void> metadata) {
            super(metadata);
            this.type = Notification.Type
                    .valueOf(metadata.info().description());
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public String name() {
            return metadata.info().name();
        }

        @Override
        public String description() {
            return metadata.info().description();
        }

        @Override
        public String serverName() {
            return metadata.info().serverName();
        }

        @Override
        public boolean isMethod() {
            return true;
        }

        @Override
        public JsonObject asJson() {
            throw new UnsupportedOperationException();
        }

    }

    class NotificationDefinitionImpl
            extends
            FeatureManagerBase.FeatureDefinitionBase<NotificationInfo, NotificationArguments, Void, NotificationDefinitionImpl>
            implements NotificationManager.NotificationDefinition {

        private Notification.Type type;

        private NotificationDefinitionImpl(String name) {
            super(name);
        }

        @Override
        public NotificationDefinition setType(Type type) {
            this.type = type;
            return this;
        }

        @Override
        public NotificationInfo register() {
            validate(false);
            if (type == null) {
                throw new IllegalStateException("Type must be set");
            }
            NotificationDefinitionInfo ret = new NotificationDefinitionInfo(name, description, serverName, fun, asyncFun,
                    runOnVirtualThread, type);
            String key = key(ret);

            NotificationInfo existing = notifications.putIfAbsent(key, ret);
            if (existing != null) {
                throw notificationAlreadyExists(type, name);
            }
            return ret;
        }
    }

    class NotificationDefinitionInfo extends FeatureManagerBase.FeatureDefinitionInfoBase<NotificationArguments, Void>
            implements NotificationManager.NotificationInfo {

        private final Notification.Type type;

        private NotificationDefinitionInfo(String name, String description, String serverName,
                Function<NotificationArguments, Void> fun,
                Function<NotificationArguments, Uni<Void>> asyncFun, boolean runOnVirtualThread, Notification.Type type) {
            super(name, description, serverName, fun, asyncFun, runOnVirtualThread);
            this.type = type;
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public JsonObject asJson() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected NotificationArguments createArguments(ArgumentProviders argumentProviders) {
            return new NotificationArguments(argumentProviders.connection(),
                    log(Feature.NOTIFICATION.toString().toLowerCase() + ":" + name, name, argumentProviders),
                    RootsImpl.from(argumentProviders),
                    SamplingImpl.from(argumentProviders));
        }

    }

    public String key(NotificationInfo info) {
        return key(info.type(), info.name());
    }

    private String key(Type type, String name) {
        return Objects.requireNonNull(type) + "::" + Objects.requireNonNull(name);
    }

}
