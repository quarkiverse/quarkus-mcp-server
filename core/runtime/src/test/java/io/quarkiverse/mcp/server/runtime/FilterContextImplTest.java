package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.MetaKey;
import io.vertx.core.json.JsonObject;

public class FilterContextImplTest {

    @Test
    public void testIllegalArguments() {
        assertThrows(NullPointerException.class, () -> FilterContextImpl.of(McpMethod.TOOLS_CALL, new JsonObject(), null));
        FilterContextImpl filterContext = FilterContextImpl.of(McpMethod.TOOLS_CALL, new JsonObject(), new McpRequest() {

            @Override
            public String serverName() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Sender sender() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SecuritySupport securitySupport() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object json() {
                return new JsonObject();
            }

            @Override
            public ContextSupport contextSupport() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void prepareTracing(McpTracing mcpTracing, McpMethod method, JsonObject message,
                    InitialRequest.Transport transport) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setTracingErrorResponse(boolean toolError, Integer jsonRpcErrorCode, String errorMessage) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void endTracing(Throwable error) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void contextStart() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void contextEnd(Throwable error) {
                throw new UnsupportedOperationException();
            }

            @Override
            public McpConnectionBase connection() {
                return null;
            }

            @Override
            public String protocolVersion() {
                throw new UnsupportedOperationException();
            }

        });
        assertNull(filterContext.connection());
        assertNull(filterContext.meta().getValue(MetaKey.of("foo")));
    }

}
