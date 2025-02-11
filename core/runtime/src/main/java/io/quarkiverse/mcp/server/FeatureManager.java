package io.quarkiverse.mcp.server;

import java.util.function.Function;

import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.runtime.ExecutionModel;
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

        String name();

        String description();

        /**
         * @return {@code true} if backed by a business method of a CDI bean, {@code false} otherwise
         */
        boolean isMethod();

        @Override
        default int compareTo(FeatureInfo o) {
            return name().compareTo(o.name());
        }

        JsonObject asJson();
    }

    interface FeatureDefinition<INFO extends FeatureInfo, ARGUMENTS, RESPONSE, THIS extends FeatureDefinition<INFO, ARGUMENTS, RESPONSE, THIS>> {

        /**
         *
         * @param description
         * @return self
         */
        THIS setDescription(String description);

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
         * Registers the resulting info and sends notifications to all connected clients.
         *
         * @return the info
         */
        INFO register();
    }

}
