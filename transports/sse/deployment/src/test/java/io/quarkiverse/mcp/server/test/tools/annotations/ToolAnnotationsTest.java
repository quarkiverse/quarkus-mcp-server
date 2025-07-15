package io.quarkiverse.mcp.server.test.tools.annotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

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

        McpSseTestClient client = McpAssured.newSseClient()
                .build()
                .connect();

        client.when().toolsList(page -> {
            assertEquals(3, page.tools().size());
            ToolInfo alphaTool = page.findByName("alpha");
            assertTrue(alphaTool.annotations().get().readOnlyHint());
            assertFalse(alphaTool.annotations().get().destructiveHint());
            assertFalse(alphaTool.annotations().get().idempotentHint());
            assertTrue(alphaTool.annotations().get().openWorldHint());

            ToolInfo bravoTool = page.findByName("bravo");
            assertFalse(bravoTool.annotations().get().readOnlyHint());
            assertFalse(bravoTool.annotations().get().destructiveHint());
            assertFalse(bravoTool.annotations().get().idempotentHint());
            assertFalse(bravoTool.annotations().get().openWorldHint());

            ToolInfo charlieTool = page.findByName("charlie");
            assertTrue(charlieTool.annotations().isEmpty());
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

        @Tool(annotations = @Annotations(readOnlyHint = true, destructiveHint = false))
        String withoutTitle() {
            return "ok";
        }

        @Tool
        String charlie() {
            return "nok";
        }

    }

}
