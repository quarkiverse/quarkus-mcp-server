package io.quarkiverse.mcp.server.tasks.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.FeatureArgumentProvider;
import io.quarkiverse.mcp.server.FeatureManager.RequestFeatureArguments;
import io.quarkiverse.mcp.server.tasks.Tasks;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.Vertx;

/**
 * Supplies the {@link Tasks} parameter of a tool method.
 */
@Singleton
public class TasksArgumentProvider implements FeatureArgumentProvider<Tasks> {

    private final TaskManagerImpl manager;
    private final Vertx vertx;
    private final CurrentIdentityAssociation identityAssociation;

    TasksArgumentProvider(TaskManagerImpl manager, Vertx vertx, Instance<CurrentIdentityAssociation> identityAssociation) {
        this.manager = manager;
        this.vertx = vertx;
        this.identityAssociation = identityAssociation.isResolvable() ? identityAssociation.get() : null;
    }

    @Override
    public Tasks provide(RequestFeatureArguments arguments) {
        return new TasksImpl(manager, vertx, identityAssociation, arguments, currentIdentity());
    }

    /**
     * @return the identity of the current request, captured so that it can be propagated to the task handler; or
     *         {@code null}
     */
    private SecurityIdentity currentIdentity() {
        if (identityAssociation == null) {
            return null;
        }
        try {
            return identityAssociation.getIdentity();
        } catch (RuntimeException e) {
            return null;
        }
    }

}
