package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.MetaKey;
import io.vertx.core.json.JsonObject;

public class McpMessageHandlerTest {

    @Test
    public void testIsStateless() {
        assertThrows(IllegalArgumentException.class, () -> McpMessageHandler.isStateless(null));
        assertFalse(McpMessageHandler.isStateless("2024-11-05"));
        assertFalse(McpMessageHandler.isStateless("2025-03-26"));
        assertFalse(McpMessageHandler.isStateless("2025-06-18"));
        assertFalse(McpMessageHandler.isStateless("2025-11-25"));
        assertTrue(McpMessageHandler.isStateless("2026-07-28"));
        assertTrue(McpMessageHandler.isStateless("2027-01-15"));
        assertTrue(McpMessageHandler.isStateless("2030-12-31"));
    }

    @Test
    public void testValidateStatelessMetaNullMeta() {
        McpException e = assertThrows(McpException.class, () -> McpMessageHandler.validateStatelessMeta(null));
        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.getJsonRpcErrorCode());
    }

    @Test
    public void testValidateStatelessMetaEmptyMeta() {
        McpException e = assertThrows(McpException.class,
                () -> McpMessageHandler.validateStatelessMeta(new JsonObject()));
        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.getJsonRpcErrorCode());
        assertTrue(e.getMessage().contains(MetaKey.PROTOCOL_VERSION.toString()));
        assertTrue(e.getMessage().contains(MetaKey.CLIENT_INFO.toString()));
        assertTrue(e.getMessage().contains(MetaKey.CLIENT_CAPABILITIES.toString()));
    }

    @Test
    public void testValidateStatelessMetaMissingClientInfo() {
        JsonObject meta = new JsonObject()
                .put(MetaKey.PROTOCOL_VERSION.toString(), "2026-07-28")
                .put(MetaKey.CLIENT_CAPABILITIES.toString(), new JsonObject());
        McpException e = assertThrows(McpException.class, () -> McpMessageHandler.validateStatelessMeta(meta));
        assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.getJsonRpcErrorCode());
        assertTrue(e.getMessage().contains(MetaKey.CLIENT_INFO.toString()));
        assertFalse(e.getMessage().contains(MetaKey.PROTOCOL_VERSION.toString()));
    }

    @Test
    public void testValidateStatelessMetaValid() {
        JsonObject meta = new JsonObject()
                .put(MetaKey.PROTOCOL_VERSION.toString(), "2026-07-28")
                .put(MetaKey.CLIENT_INFO.toString(), new JsonObject().put("name", "test").put("version", "1.0"))
                .put(MetaKey.CLIENT_CAPABILITIES.toString(), new JsonObject());
        McpMessageHandler.validateStatelessMeta(meta);
    }

}
