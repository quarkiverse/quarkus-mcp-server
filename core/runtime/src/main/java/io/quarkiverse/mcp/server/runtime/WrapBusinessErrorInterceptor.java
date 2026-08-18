package io.quarkiverse.mcp.server.runtime;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.InputRequiredException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.UrlElicitationRequiredException;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.smallrye.mutiny.Uni;

@Priority(Interceptor.Priority.LIBRARY_BEFORE)
@Interceptor
@WrapBusinessError
public class WrapBusinessErrorInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext context) throws Exception {
        Object ret;
        try {
            ret = context.proceed();
        } catch (Throwable t) {
            throw sneakyThrow(wrapIfNecessary(t, context));
        }
        if (ret instanceof Uni<?> uni) {
            return uni.onFailure().transform(t -> wrapIfNecessary(t, context));
        } else {
            return ret;
        }
    }

    private Throwable wrapIfNecessary(Throwable t, InvocationContext context) {
        if (isControlFlowException(t)) {
            return t;
        }

        if (context.getMethod().isAnnotationPresent(Tool.class) && matches(t, context)) {
            return new ToolCallException(t);
        }
        return t;
    }

    private boolean matches(Throwable t, InvocationContext context) {
        WrapBusinessError businessError = context.getInterceptorBinding(WrapBusinessError.class);
        for (Class<? extends Throwable> e : businessError.unless()) {
            if (e.isAssignableFrom(t.getClass())) {
                return false;
            }
        }
        for (Class<? extends Throwable> e : businessError.value()) {
            if (e.isAssignableFrom(t.getClass())) {
                return true;
            }
        }
        return false;
    }

    private boolean isControlFlowException(Throwable t) {
        return t instanceof ToolCallException
                || t instanceof Cancellation.OperationCancellationException
                || t instanceof org.mcpjava.server.Cancellation.OperationCancelledException
                || t instanceof InputRequiredException
                || t instanceof UrlElicitationRequiredException;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

}
