package io.quarkiverse.mcp.server;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

/**
 *
 * @param <INFO>
 */
public interface FeatureManager<INFO extends FeatureInfo> extends Iterable<INFO> {

    /**
     *
     */
    interface FeatureInfo extends Comparable<FeatureInfo> {

        /**
         * It is guaranteed that the name is unique for a specific feature within a server configuration.
         *
         * @return the name
         * @see #serverNames()
         */
        String name();

        String description();

        /**
         * @return the name of the respective server configuration
         * @see McpServer
         * @deprecated Use {@link #serverNames()} instead
         */
        @Deprecated(since = "1.11")
        default String serverName() {
            return serverNames().stream().sorted().findFirst().orElseThrow();
        }

        /**
         * @return the names of the associated server configurations
         * @see McpServer
         */
        Set<String> serverNames();

        /**
         * @return {@code true} if backed by a business method of a CDI bean, {@code false} otherwise
         */
        boolean isMethod();

        /**
         * @return the business method of a CDI bean, or an empty {@link Optional} for programmatically registered features
         * @see #isMethod()
         */
        default Optional<Method> method() {
            return Optional.empty();
        }

        /**
         * @return the timestamp when this feature was registered
         */
        Instant createdAt();

        @Override
        default int compareTo(FeatureInfo o) {
            // Sort by timestamp and name asc
            int result = createdAt().compareTo(o.createdAt());
            return result == 0 ? name().compareTo(o.name()) : result;
        }

        JsonObject asJson();

        /**
         * @return the execution model
         */
        ExecutionModel executionModel();

        /**
         * @return the transport hints
         */
        default Map<TransportHint, Object> transportHints() {
            return Map.of();
        }

    }

    /**
     * Allows transport hints to be configured on a feature definition.
     *
     * @param <THIS> self type for fluent API
     * @see TransportHint
     * @see FeatureInfo#transportHints()
     */
    interface TransportHintDefinition<THIS> {

        /**
         * Adds a hint that does not require a value.
         *
         * @param hint
         * @return self
         */
        THIS addHint(TransportHint hint);

        /**
         * Adds a hint with the given value.
         *
         * @param hint
         * @param value
         * @return self
         */
        THIS addHint(TransportHint hint, Object value);

    }

    interface FeatureDefinition<INFO extends FeatureInfo, ARGUMENTS extends FeatureArguments, RESPONSE, THIS extends FeatureDefinition<INFO, ARGUMENTS, RESPONSE, THIS>> {

        /**
         *
         * @param description
         * @return self
         */
        THIS setDescription(String description);

        /**
         *
         * @param serverName
         * @return self
         * @see McpServer
         */
        THIS setServerName(String serverName);

        /**
         *
         * @param serverNames
         * @return self
         * @see McpServer
         */
        THIS setServerNames(String... serverNames);

        /**
         *
         * @param fun
         * @return self
         * @see ExecutionModel#WORKER_THREAD
         */
        default THIS setHandler(Function<ARGUMENTS, RESPONSE> fun) {
            return setHandler(fun, false);
        }

        /**
         *
         * @param fun
         * @param runOnVirtualThread
         * @return self
         * @see ExecutionModel#WORKER_THREAD
         * @see ExecutionModel#VIRTUAL_THREAD
         */
        THIS setHandler(Function<ARGUMENTS, RESPONSE> fun, boolean runOnVirtualThread);

        /**
         *
         * @param fun
         * @return self
         * @see ExecutionModel#EVENT_LOOP
         */
        THIS setAsyncHandler(Function<ARGUMENTS, Uni<RESPONSE>> fun);

        /**
         * @param icons
         * @return self
         */
        THIS setIcons(Icon... icons);

        /**
         * By default, {@link #register()} sends an automatic {@code list_changed} notification to the clients connected to the
         * affected server(s), and the corresponding {@code remove} method of the feature manager does the same when this
         * feature is later removed.
         * <p>
         * If set to {@code false}, both of these automatic notifications are suppressed for this feature and the caller becomes
         * responsible for sending them, e.g. via {@link FeatureManager#notifyListChanged(java.util.function.Predicate)}. This
         * is
         * useful when a filter hides the feature for some connections, in which case an unconditional notification would be
         * wasted.
         *
         * @param value
         * @return self
         */
        THIS setNotifyListChanged(boolean value);

        /**
         * Registers the resulting info and, unless suppressed via {@link #setNotifyListChanged(boolean)}, sends a
         * {@code list_changed} notification to the clients connected to the affected server(s).
         *
         * @return the info
         */
        INFO register();
    }

    interface RequestFeatureArguments extends FeatureArguments {

        RequestId requestId();

        Progress progress();

        Cancellation cancellation();

    }

    interface FeatureArguments {

        McpConnection connection();

        McpLog log();

        Roots roots();

        Sampling sampling();

        Elicitation elicitation();

        RawMessage rawMessage();

        Meta meta();

    }

}
