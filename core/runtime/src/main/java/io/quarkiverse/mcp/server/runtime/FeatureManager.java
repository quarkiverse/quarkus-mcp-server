package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.invoke.Invoker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider;
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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class FeatureManager<R> {

    final Vertx vertx;

    final ObjectMapper mapper;

    final ConcurrentMap<String, McpLogImpl> logs;

    protected FeatureManager(Vertx vertx, ObjectMapper mapper) {
        this.vertx = vertx;
        this.mapper = mapper;
        this.logs = new ConcurrentHashMap<>();
    }

    public Future<R> execute(String id, ArgumentProviders argProviders) throws McpException {
        FeatureMetadata<R> metadata = getMetadata(id);
        if (metadata == null) {
            throw notFound(id);
        }
        Invoker<Object, Object> invoker = metadata.invoker();
        Object[] arguments = prepareArguments(metadata, argProviders);
        return execute(metadata.executionModel(), new Callable<Uni<R>>() {
            @Override
            public Uni<R> call() throws Exception {
                try {
                    return metadata.resultMapper().apply(invoker.invoke(null, arguments));
                } catch (Throwable e) {
                    return Uni.createFrom().failure(e);
                }
            }
        });
    }

    public abstract List<FeatureMetadata<R>> list();

    public boolean isEmpty() {
        return list().isEmpty();
    }

    @SuppressWarnings("unchecked")
    protected Object[] prepareArguments(FeatureMetadata<?> metadata, ArgumentProviders argProviders) throws McpException {
        if (metadata.info().arguments().isEmpty()) {
            return new Object[0];
        }
        Object[] ret = new Object[metadata.info().arguments().size()];
        int idx = 0;
        for (FeatureArgument arg : metadata.info().arguments()) {
            if (arg.provider() == Provider.MCP_CONNECTION) {
                ret[idx] = argProviders.connection();
            } else if (arg.provider() == Provider.REQUEST_ID) {
                ret[idx] = new RequestId(argProviders.requestId());
            } else if (arg.provider() == Provider.MCP_LOG) {
                ret[idx] = logs.computeIfAbsent(logKey(metadata),
                        key -> new McpLogImpl(argProviders.connection()::logLevel, metadata.info().declaringClassName(), key,
                                argProviders.responder()));
            } else {
                Object val = argProviders.getArg(arg.name());
                if (val == null && arg.required()) {
                    throw new McpException("Missing required argument: " + arg.name(), JsonRPC.INVALID_PARAMS);
                }
                if (val instanceof Map map) {
                    // json object
                    JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                    try {
                        ret[idx] = mapper.readValue(new JsonObject(map).encode(), javaType);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (val instanceof List list) {
                    // json array
                    JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                    try {
                        ret[idx] = mapper.readValue(new JsonArray(list).encode(), javaType);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                } else {
                    if (arg.type() instanceof Class clazz && clazz.isEnum()) {
                        ret[idx] = Enum.valueOf(clazz, val.toString());
                    } else {
                        ret[idx] = val;
                    }
                }
            }
            idx++;
        }
        return ret;
    }

    protected abstract FeatureMetadata<R> getMetadata(String id);

    protected abstract McpException notFound(String id);

    protected Future<R> execute(ExecutionModel executionModel, Callable<Uni<R>> action) {
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
                            } catch (Throwable e) {
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
                    } catch (Throwable e) {
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
                    } catch (Throwable e) {
                        ret.fail(e);
                    }
                }
            });
        }
        return ret.future();
    }

    private String logKey(FeatureMetadata<?> metadata) {
        return metadata.feature().toString().toLowerCase() + ":" + metadata.info().name();
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
