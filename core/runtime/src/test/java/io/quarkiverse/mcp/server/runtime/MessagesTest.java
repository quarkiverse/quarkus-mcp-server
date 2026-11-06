package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MessagesTest {

    @Test
    public void testGetValidId() {
        assertEquals("foo", Messages.getValidId(new JsonObject().put("id", "foo")));
        assertEquals(1, Messages.getValidId(new JsonObject().put("id", 1)));
        assertEquals(1L, Messages.getValidId(new JsonObject().put("id", 1L)));
        assertNull(Messages.getValidId(new JsonObject()));
        assertNull(Messages.getValidId(new JsonObject().putNull("id")));
        assertNull(Messages.getValidId(new JsonObject().put("id", true)));
        assertNull(Messages.getValidId(new JsonObject().put("id", new JsonObject().put("foo", 1))));
        assertNull(Messages.getValidId(new JsonObject().put("id", new JsonArray().add(1))));
    }

}
