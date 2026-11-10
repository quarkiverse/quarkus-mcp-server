package io.quarkiverse.mcp.server.test.tools.inputschema;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InputSchemaGenerator;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.InputSchema;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolSchemaCachePerServerTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, AlphaGenerator.class, BravoGenerator.class,
                            SharedGenerator.class))
            .overrideConfigKey("quarkus.mcp.server.support-multi-server-bindings", "true")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.tools.cache-schemas", "false");

    @Inject
    ToolManager toolManager;

    @Test
    public void testCachingIsConfiguredPerServer() {
        ToolInfo alpha = toolManager.getTool("alpha", DEFAULT);
        ToolInfo bravo = toolManager.getTool("bravo", "bravo");
        ToolInfo shared = toolManager.getTool("shared", DEFAULT);
        for (int i = 0; i < 3; i++) {
            assertNotNull(alpha.inputSchema());
            assertNotNull(bravo.inputSchema());
            assertNotNull(shared.inputSchema());
        }
        // Default server: caching enabled
        assertEquals(1, AlphaGenerator.COUNTER.get());
        // Server "bravo": caching disabled
        assertEquals(3, BravoGenerator.COUNTER.get());
        // Bound to both servers: caching disabled because it is disabled for "bravo"
        assertEquals(3, SharedGenerator.COUNTER.get());
    }

    public static class MyTools {

        @Tool(inputSchema = @InputSchema(generator = AlphaGenerator.class))
        String alpha(int size) {
            return "" + size;
        }

        @McpServer("bravo")
        @Tool(inputSchema = @InputSchema(generator = BravoGenerator.class))
        String bravo(int size) {
            return "" + size;
        }

        @McpServer(DEFAULT)
        @McpServer("bravo")
        @Tool(inputSchema = @InputSchema(generator = SharedGenerator.class))
        String shared(int size) {
            return "" + size;
        }

    }

    static JsonObject schema() {
        return new JsonObject()
                .put("type", "object")
                .put("properties", new JsonObject().put("size", new JsonObject().put("type", "integer")));
    }

    @Singleton
    public static class AlphaGenerator implements InputSchemaGenerator<JsonObject> {

        static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public JsonObject generate(ToolInfo tool) {
            COUNTER.incrementAndGet();
            return schema();
        }

    }

    @Singleton
    public static class BravoGenerator implements InputSchemaGenerator<JsonObject> {

        static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public JsonObject generate(ToolInfo tool) {
            COUNTER.incrementAndGet();
            return schema();
        }

    }

    @Singleton
    public static class SharedGenerator implements InputSchemaGenerator<JsonObject> {

        static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public JsonObject generate(ToolInfo tool) {
            COUNTER.incrementAndGet();
            return schema();
        }

    }

}
