package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public abstract class ComponentManager {

    @Inject
    Vertx vertx;

    protected <R> Future<R> execute(ExecutionModel executionModel, Callable<Uni<R>> action) {
        Promise<R> ret = Promise.promise();
        ActivateRequestContext<R> activate = new ActivateRequestContext<>(action);

        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        VertxContextSafetyToggle.setContextSafe(context, true);

        if (executionModel == ExecutionModel.VIRTUAL_THREAD) {
            // While counter-intuitive, we switch to a safe context, so that context is captured and attached
            // to the virtual thread.
            context.runOnContext(new Handler<Void>() {
                @Override
                public void handle(Void event) {
                    VirtualThreadsRecorder.getCurrent().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                activate.call().subscribe().with(ret::complete, ret::fail);
                            } catch (Exception e) {
                                ret.fail(e);
                            }
                        }
                    });
                }
            });
        } else if (executionModel == ExecutionModel.WORKER_THREAD) {
            context.executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        activate.call().subscribe().with(ret::complete, ret::fail);
                    } catch (Exception e) {
                        ret.fail(e);
                    }
                    return null;
                }
            }, false);
        } else {
            // Event loop
            context.runOnContext(new Handler<Void>() {
                @Override
                public void handle(Void event) {
                    try {
                        activate.call().subscribe().with(ret::complete, ret::fail);
                    } catch (Exception e) {
                        ret.fail(e);
                    }
                }
            });
        }
        return ret.future();
    }

    private class ActivateRequestContext<T> implements Callable<Uni<T>> {

        private final Callable<Uni<T>> delegate;

        private ActivateRequestContext(Callable<Uni<T>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Uni<T> call() throws Exception {
            ManagedContext requestContext = Arc.container().requestContext();
            if (requestContext.isActive()) {
                return delegate.call();
            } else {
                requestContext.activate();
                try {
                    return delegate.call().eventually(requestContext::terminate);
                } catch (Throwable e) {
                    requestContext.terminate();
                    throw e;
                }
            }
        }

    }

}
