package io.quarkiverse.mcp.server.test.resources.templates;

import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;

@AlwaysError
@Interceptor
public class AlwaysErrorInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext context) {
        throw new McpException("Always error!", JsonRpcErrorCodes.INTERNAL_ERROR);
    }

}
