package io.quarkiverse.mcp.server;

import java.time.Instant;
import java.util.Map;
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
         * Registers the resulting info and sends notifications to all connected clients.
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
