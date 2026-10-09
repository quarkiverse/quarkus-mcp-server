package io.quarkiverse.mcp.server.tasks.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger(TasksArgumentProvider.class);

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
     * Captures the identity of the current request so that it can be propagated to the task handler.
     * <p>
     * Note that the identity may not be resolvable at this point if proactive authentication is disabled; in that case an
     * error is logged and the task handler is executed without a {@code SecurityIdentity}.
     *
     * @return the identity of the current request, or {@code null}
     */
    private SecurityIdentity currentIdentity() {
        if (identityAssociation == null) {
            return null;
        }
        try {
            return identityAssociation.getIdentity();
        } catch (RuntimeException e) {
            LOG.errorf(e,
                    "Unable to resolve the SecurityIdentity of the current request - the task handler will be executed without an identity;"
                            + " note that the identity may not be available if proactive authentication is disabled");
            return null;
        }
    }

}
