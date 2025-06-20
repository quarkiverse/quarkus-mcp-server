package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolsPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.tools.page-size", "3");

    @Inject
    ToolManager manager;

    @Test
    public void testTools() {
        int loop = 8;
        for (int i = 1; i <= loop; i++) {
            String name = i + "";
            manager.newTool(name)
                    .setDescription(name)
                    .setHandler(
                            args -> ToolResponse.success("Result " + name))
                    .register();
        }

        Instant lastCreatedAt = Instant.EPOCH;
        for (ToolInfo info : manager) {
            assertTrue(info.createdAt().isAfter(lastCreatedAt));
            lastCreatedAt = info.createdAt();
        }

        McpSseTestClient client = McpAssured.newConnectedSseClient();
        AtomicReference<String> cursor = new AtomicReference<>();

        client.when()
                .toolsList(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("1", page.tools().get(0).name());
                    assertEquals("2", page.tools().get(1).name());
                    assertEquals("3", page.tools().get(2).name());
                })
                .thenAssertResults();

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    cursor.set(page.nextCursor());
                    assertEquals(3, page.size());
                    assertEquals("4", page.tools().get(0).name());
                    assertEquals("5", page.tools().get(1).name());
                    assertEquals("6", page.tools().get(2).name());
                })
                .send()
                .thenAssertResults();

        client.when()
                .toolsList()
                .withCursor(cursor.get())
                .withAssert(page -> {
                    assertEquals(2, page.size());
                    assertEquals("7", page.tools().get(0).name());
                    assertEquals("8", page.tools().get(1).name());
                })
                .send()
                .thenAssertResults();
    }

}
