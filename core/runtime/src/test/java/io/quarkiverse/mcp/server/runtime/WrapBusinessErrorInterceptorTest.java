package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.InputRequiredException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.UrlElicitationRequiredException;
import io.quarkiverse.mcp.server.WrapBusinessError;

class WrapBusinessErrorInterceptorTest {

    private WrapBusinessErrorInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WrapBusinessErrorInterceptor();
    }

    @Test
    void testToolCallExceptionNotWrapped() throws Exception {
        ToolCallException original = new ToolCallException("Already a tool error");

        Throwable result = invokeWrapIfNecessary(original, TestTools.class.getMethod("toolWithWrapBusinessError"));

        assertSame(original, result, "ToolCallException should not be wrapped");
    }

    @Test
    void testOperationCancellationExceptionNotWrapped() throws Exception {
        Cancellation.OperationCancellationException original = new Cancellation.OperationCancellationException();

        Throwable result = invokeWrapIfNecessary(original, TestTools.class.getMethod("toolWithWrapBusinessError"));

        assertSame(original, result, "OperationCancellationException should not be wrapped");
    }

    @Test
    void testMcpJavaOperationCancelledExceptionNotWrapped() throws Exception {
        org.mcpjava.server.Cancellation.OperationCancelledException original = new org.mcpjava.server.Cancellation.OperationCancelledException();

        Throwable result = invokeWrapIfNecessary(original, TestTools.class.getMethod("toolWithWrapBusinessError"));

        assertSame(original, result, "mcpjava OperationCancelledException should not be wrapped");
    }

    @Test
    void testInputRequiredExceptionNotWrapped() throws Exception {
        InputRequiredException original = createInputRequiredException();

        Throwable result = invokeWrapIfNecessary(original, TestTools.class.getMethod("toolWithWrapBusinessError"));

        assertSame(original, result, "InputRequiredException should not be wrapped");
    }

    @Test
    void testUrlElicitationRequiredExceptionNotWrapped() throws Exception {
        UrlElicitationRequiredException original = createUrlElicitationRequiredException();

        Throwable result = invokeWrapIfNecessary(original, TestTools.class.getMethod("toolWithWrapBusinessError"));

        assertSame(original, result, "UrlElicitationRequiredException should not be wrapped");
    }

    @Test
    void testRegularExceptionIsWrapped() throws Exception {
        IllegalArgumentException original = new IllegalArgumentException("Business error");

        Throwable result = invokeWrapIfNecessary(original, TestTools.class.getMethod("toolWithWrapBusinessError"));

        assertNotSame(original, result, "Regular exception should be wrapped");
        assertInstanceOf(ToolCallException.class, result, "Should be wrapped in ToolCallException");
        assertEquals(original, result.getCause(), "Original exception should be the cause");
    }

    @Test
    void testControlFlowExceptionNotWrappedEvenWhenMatching() throws Exception {
        Cancellation.OperationCancellationException cancellation = new Cancellation.OperationCancellationException();

        Throwable result = invokeWrapIfNecessary(cancellation, TestTools.class.getMethod("toolWithWrapBusinessError"));

        assertSame(cancellation, result,
                "Control-flow exception should not be wrapped even when matching @WrapBusinessError filter");
    }

    @Test
    void testNonToolMethodDoesNotWrap() throws Exception {
        IllegalArgumentException original = new IllegalArgumentException("Error");

        Throwable result = invokeWrapIfNecessary(original, TestTools.class.getMethod("nonToolMethod"));

        assertSame(original, result, "Non-@Tool method should not wrap exceptions");
    }

    private InputRequiredException createInputRequiredException() throws Exception {
        Method builderMethod = InputRequiredException.class.getDeclaredMethod("builder");
        builderMethod.setAccessible(true);
        Object builder = builderMethod.invoke(null);

        Method addRootsMethod = builder.getClass().getMethod("addRootsRequest", String.class);
        addRootsMethod.invoke(builder, "test-roots");

        Method buildMethod = builder.getClass().getMethod("build");
        return (InputRequiredException) buildMethod.invoke(builder);
    }

    private UrlElicitationRequiredException createUrlElicitationRequiredException() throws Exception {
        Method builderMethod = UrlElicitationRequiredException.class.getDeclaredMethod("builder");
        builderMethod.setAccessible(true);
        Object builder = builderMethod.invoke(null);

        Method setMessageMethod = builder.getClass().getMethod("setMessage", String.class);
        setMessageMethod.invoke(builder, "URL elicitation required");

        Method addElicitationMethod = builder.getClass().getMethod("addElicitation", String.class, String.class);
        addElicitationMethod.invoke(builder, "https://example.com/auth", "Auth needed");

        Method buildMethod = builder.getClass().getMethod("build");
        return (UrlElicitationRequiredException) buildMethod.invoke(builder);
    }

    private Throwable invokeWrapIfNecessary(Throwable t, Method toolMethod) throws Exception {
        InvocationContext context = new TestInvocationContext(toolMethod);

        Method method = WrapBusinessErrorInterceptor.class.getDeclaredMethod(
                "wrapIfNecessary", Throwable.class, InvocationContext.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(interceptor, t, context);
    }

    private static class TestInvocationContext implements InvocationContext {
        private final Method method;

        TestInvocationContext(Method method) {
            this.method = method;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public <T extends Annotation> T getInterceptorBinding(Class<T> annotationClass) {
            return method.getAnnotation(annotationClass);
        }

        @Override
        public Object getTarget() {
            return null;
        }

        @Override
        public Object getTimer() {
            return null;
        }

        @Override
        public Object[] getParameters() {
            return new Object[0];
        }

        @Override
        public void setParameters(Object[] params) {
        }

        @Override
        public java.util.Map<String, Object> getContextData() {
            return null;
        }

        @Override
        public Object proceed() throws Exception {
            return null;
        }

        @Override
        public <T extends Annotation> java.util.Set<T> getInterceptorBindings(Class<T> annotationClass) {
            return null;
        }

        @Override
        public java.lang.reflect.Constructor<?> getConstructor() {
            return null;
        }
    }

    public static class TestTools {

        @Tool
        @WrapBusinessError(Exception.class)
        public void toolWithWrapBusinessError() {
            // Tool method
        }

        @WrapBusinessError(Exception.class)
        public void nonToolMethod() {
            // Not a Tool method
        }
    }
}
