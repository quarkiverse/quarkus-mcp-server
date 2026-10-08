package io.quarkiverse.mcp.server.runtime;

import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;

/**
 * CDI state owned by one invocation. Close on the context where it was activated so that lifecycle
 * observers can access that invocation's request scoped beans.
 */
final class McpRequestContext implements AutoCloseable {

    private final ManagedContext context;
    private final ContextState ownedState;

    McpRequestContext(ManagedContext context, ContextState ownedState) {
        this.context = context;
        this.ownedState = ownedState;
    }

    @Override
    public void close() {
        if (ownedState != null) {
            context.destroy(ownedState);
        }
    }
}
