package io.quarkiverse.mcp.server;

import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;

/**
 * Performs an initial check when an MCP client connection is initialized, i.e. before the server capabilities are sent back to
 * the client. If an initial check fails then the connection is not initialized successfully and the error message is sent back
 * to the client.
 * <p>
 * Implementations are CDI beans. Qualifiers are ignored. Multiple checks are sorted by
 * {@link io.quarkus.arc.InjectableBean#getPriority()} and executed sequentially. Higher priority is executed first.
 */
public interface InitialCheck {

    /**
     * Use the {@link VertxContextSupport#executeBlocking(java.util.concurrent.Callable)} if you need to execute some blocking
     * code in the check.
     *
     * @param initialRequest
     * @return the check result
     */
    Uni<CheckResult> perform(InitialRequest initialRequest);

    /**
     * @param error
     * @param message
     */
    record CheckResult(boolean error, String message) {

        public static final CheckResult SUCCESS = new CheckResult(false, null);

        @Deprecated
        public static Uni<CheckResult> successs() {
            return Uni.createFrom().item(SUCCESS);
        }

        public static Uni<CheckResult> success() {
            return Uni.createFrom().item(SUCCESS);
        }

        public static Uni<CheckResult> error(String message) {
            return Uni.createFrom().item(new CheckResult(true, message));
        }
    }
}
