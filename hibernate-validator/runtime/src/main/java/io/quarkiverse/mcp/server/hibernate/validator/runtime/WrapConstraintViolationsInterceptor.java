package io.quarkiverse.mcp.server.hibernate.validator.runtime;

import java.lang.reflect.Method;
import java.util.Arrays;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.validation.ConstraintViolationException;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter;
import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter.FeatureContext;
import io.quarkiverse.mcp.server.runtime.Feature;

/**
 * Wraps a business method and transforms {@link ConstraintViolationException} to another exception.
 *
 * @see ConstraintViolationConverter
 */
@WrapConstraintViolations
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER + 100) // MethodValidationInterceptor has priority 4800
public class WrapConstraintViolationsInterceptor {

    @Inject
    ConstraintViolationConverter converter;

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        try {
            return context.proceed();
        } catch (ConstraintViolationException e) {
            throw converter.convert(e, new FeatureContext(getFeature(context), getServerName(context)));
        }
    }

    private String getServerName(InvocationContext context) {
        String serverName = McpServer.DEFAULT;
        Method m = context.getMethod();
        McpServer serverAnnotation = m.getAnnotation(McpServer.class);
        if (serverAnnotation != null) {
            serverName = serverAnnotation.value();
        } else {
            serverAnnotation = m.getDeclaringClass().getAnnotation(McpServer.class);
            if (serverAnnotation != null) {
                serverName = serverAnnotation.value();
            }
        }
        return serverName;
    }

    private Feature getFeature(InvocationContext context) {
        Method m = context.getMethod();
        if (m.isAnnotationPresent(Tool.class) || Arrays.stream(m.getDeclaredAnnotations())
                .anyMatch(a -> a.annotationType().getName().equals("dev.langchain4j.agent.tool.Tool"))) {
            return Feature.TOOL;
        } else if (m.isAnnotationPresent(Prompt.class)) {
            return Feature.PROMPT;
        } else if (m.isAnnotationPresent(Resource.class)) {
            return Feature.RESOURCE;
        } else if (m.isAnnotationPresent(ResourceTemplate.class)) {
            return Feature.RESOURCE_TEMPLATE;
        }
        throw new IllegalStateException("Unsupported feature on: " + m);
    }

}
