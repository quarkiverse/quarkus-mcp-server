package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ResourceLink;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolUseContent;

public class ContentTest {

    @Test
    public void testAs() {
        TextContent text = new TextContent("foo").asText();
        assertEquals("Not an image",
                assertThrows(IllegalArgumentException.class, () -> text.asImage()).getMessage());
        assertEquals("Not a resource",
                assertThrows(IllegalArgumentException.class, () -> text.asResource()).getMessage());
        assertEquals("Not a resource link",
                assertThrows(IllegalArgumentException.class, () -> text.asResourceLink()).getMessage());
        assertEquals("Not an audio",
                assertThrows(IllegalArgumentException.class, () -> text.asAudio()).getMessage());
        assertEquals("Not a tool use",
                assertThrows(IllegalArgumentException.class, () -> text.asToolUse()).getMessage());
        ResourceLink link = new ResourceLink("file://", "foo");
        assertEquals("Not a text",
                assertThrows(IllegalArgumentException.class, () -> link.asText()).getMessage());
        ToolUseContent toolUse = new ToolUseContent("call_1", "weather", java.util.Map.of(), null);
        assertEquals("Not a tool result",
                assertThrows(IllegalArgumentException.class, () -> toolUse.asToolResult()).getMessage());
    }

}
