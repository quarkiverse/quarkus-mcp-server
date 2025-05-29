package io.quarkiverse.mcp.server.test.tools.annotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolAnnotations;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolAnnotationsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Inject
    ToolManager manager;

    @Test
    public void testAnnotations() {
        ToolManager.ToolInfo alpha = manager.getTool("alpha");
        assertTrue(alpha.annotations().isPresent());
        assertEquals("Alpha tool", alpha.annotations().get().title());
        assertTrue(alpha.annotations().get().readOnlyHint());
        assertFalse(alpha.annotations().get().destructiveHint());
        assertFalse(alpha.annotations().get().idempotentHint());
        assertTrue(alpha.annotations().get().openWorldHint());

        ToolManager.ToolInfo bravo = manager.getTool("bravo");
        assertTrue(bravo.annotations().isPresent());
        assertEquals("Bravo tool", bravo.annotations().get().title());
        assertFalse(bravo.annotations().get().readOnlyHint());
        assertFalse(bravo.annotations().get().destructiveHint());
        assertFalse(bravo.annotations().get().idempotentHint());
        assertFalse(bravo.annotations().get().openWorldHint());

        ToolManager.ToolInfo charlie = manager.getTool("charlie");
        assertTrue(charlie.annotations().isEmpty());

        initClient();

        JsonObject toolListMessage = newMessage("tools/list");
        send(toolListMessage);

        JsonObject toolListResponse = waitForLastResponse();

        JsonObject toolListResult = assertResultResponse(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(3, tools.size());

        assertTool(tools, "alpha", null, null, annotations -> {
            assertEquals("Alpha tool", annotations.getString("title"));
            assertTrue(annotations.getBoolean("readOnlyHint"));
            assertFalse(annotations.getBoolean("destructiveHint"));
            assertFalse(annotations.getBoolean("idempotentHint"));
            assertTrue(annotations.getBoolean("openWorldHint"));
        });

        assertTool(tools, "bravo", null, null, annotations -> {
            assertEquals("Bravo tool", annotations.getString("title"));
            assertFalse(annotations.getBoolean("readOnlyHint"));
            assertFalse(annotations.getBoolean("destructiveHint"));
            assertFalse(annotations.getBoolean("idempotentHint"));
            assertFalse(annotations.getBoolean("openWorldHint"));

        });

        assertTool(tools, "charlie", null, null, annotations -> {
            assertNull(annotations);
        });
    }

    public record MyArg(int price, List<String> names) {
    }

    public static class MyTools {

        @Inject
        ToolManager manager;

        @Startup
        void start() {
            manager.newTool("bravo")
                    .setDescription("Bravo tool")
                    .setAnnotations(new ToolAnnotations("Bravo tool", false, false, false, false))
                    .setHandler(args -> ToolResponse.success("oki"))
                    .register();
        }

        @Tool(annotations = @Annotations(title = "Alpha tool", readOnlyHint = true, destructiveHint = false))
        String alpha() {
            return "ok";
        }

        @Tool
        String charlie() {
            return "nok";
        }

    }

}
