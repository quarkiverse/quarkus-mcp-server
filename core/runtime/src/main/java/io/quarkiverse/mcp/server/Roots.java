package io.quarkiverse.mcp.server;

import java.util.List;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.TimeoutException;
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
     * <p>
     * If the client does not respond before the timeout expires then the returned {@code Uni} fails with
     * {@link TimeoutException}. The timeout is configured with the {@code quarkus.mcp.server.roots.default-timeout} config
     * property.
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
     * <p>
     * If the client does not respond before the timeout expires then a {@link TimeoutException} is thrown. The timeout is
     * configured with the {@code quarkus.mcp.server.roots.default-timeout} config property.
     *
     * @throws IllegalStateException if the client does not support the {@code roots} capability
     * @return the list of roots
     */
    default List<Root> listAndAwait() {
        return list().await().indefinitely();
    }

}
