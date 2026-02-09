package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.ResourceContents.Type;
import io.quarkiverse.mcp.server.TextResourceContents;

public class ResourceContentsTest {

    @Test
    public void testBlobResourceContents() {
        assertEquals("uri must not be null",
                assertThrows(IllegalArgumentException.class, () -> BlobResourceContents.create(null, "foo")).getMessage());
        assertEquals("blob must not be null",
                assertThrows(IllegalArgumentException.class, () -> BlobResourceContents.create("foo", (String) null))
                        .getMessage());
        assertEquals("blob must not be null",
                assertThrows(IllegalArgumentException.class, () -> BlobResourceContents.create("foo", (byte[]) null))
                        .getMessage());
        BlobResourceContents blob = new BlobResourceContents("uri", "blob", "mime", Map.of());
        assertEquals(Type.BLOB, blob.type());
        assertEquals("YmFyYmFy", BlobResourceContents.create("bar", "barbar".getBytes()).blob());
    }

    @Test
    public void testTextResourceContents() {
        assertEquals("uri must not be null",
                assertThrows(IllegalArgumentException.class, () -> TextResourceContents.create(null, null)).getMessage());
        assertEquals("text must not be null",
                assertThrows(IllegalArgumentException.class, () -> TextResourceContents.create("foo", null))
                        .getMessage());
        TextResourceContents text = new TextResourceContents("uri", "blob", "mime", Map.of());
        assertEquals(Type.TEXT, text.type());
    }
}
