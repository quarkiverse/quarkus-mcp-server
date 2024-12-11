package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public abstract class ComponentManager {

    @Inject
    Vertx vertx;

    // TODO request context activation
    // TODO vertx duplicated context
    Future<Object> doExecute(ExecutionModel executionModel, Callable<Object> action) {
        Promise<Object> ret = Promise.promise();
        if (executionModel == ExecutionModel.VIRTUAL_THREAD) {
            VirtualThreadsRecorder.getCurrent().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Object result = action.call();
                        ret.complete(result);
                    } catch (Exception e) {
                        ret.fail(e);
                    }
                }
            });
        } else if (executionModel == ExecutionModel.WORKER_THREAD) {
            vertx.executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        Object result = action.call();
                        ret.complete(result);
                    } catch (Exception e) {
                        ret.fail(e);
                    }
                    return null;
                }
            }, false);
        } else {
            // Event loop
            try {
                Object result = action.call();
                ret.complete(result);
            } catch (Exception e) {
                ret.fail(e);
            }
        }
        return ret.future();
    }

}
