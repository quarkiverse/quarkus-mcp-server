package io.quarkiverse.mcp.server;

import java.util.List;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.Uni;

/**
 * If an MCP client supports the {@code roots} capability, then the server can obtain the list of root objects.
 *
 * @see Notification.Type#ROOTS_LIST_CHANGED
 */
public interface Roots {

    /**
     * @return {@code true} if the client supports the {@code roots} capability, {@code false} otherwise
     */
    boolean isSupported();

    /**
     * Send a {@code roots/list} message.
     *
     * @throws IllegalStateException if the client does not support the {@code roots} capability
     * @return a new {@link Uni} completed with the list of roots
     */
    @CheckReturnValue
    Uni<List<Root>> list();

    /**
     * Send a {@code roots/list} message and wait for the result.
     * <p>
     * Note that this method will block until the client sends the response.
     *
     * @throws IllegalStateException if the client does not support the {@code roots} capability
     * @return the list of roots
     */
    default List<Root> listAndAwait() {
        return list().await().indefinitely();
    }

}
