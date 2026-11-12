package io.quarkiverse.mcp.server.test.tools.inputschema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InputSchemaGenerator;
import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.InputSchema;
import io.quarkiverse.mcp.server.Tool.OutputSchema;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolsPage;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.json.JsonObject;

public class ToolSchemaCacheTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = defaultConfig(5000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyPojo.class, CountingInputSchemaGenerator.class,
                            CountingOutputSchemaGenerator.class));

    @Inject
    ToolManager toolManager;

    @Test
    public void testSchemasAreGeneratedOnce() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        // The first request generates the schemas. Requests in one when() block are sent before any response is awaited,
        // so concurrent first calls could each generate them (which is allowed); hence a separate block.
        client.when()
                .toolsList(this::assertToolList)
                .thenAssertResults();
        assertEquals(1, CountingInputSchemaGenerator.COUNTER.get());
        assertEquals(1, CountingOutputSchemaGenerator.COUNTER.get());

        client.when()
                .toolsList(this::assertToolList)
                .toolsList(this::assertToolList)
                .thenAssertResults();
        assertEquals(1, CountingInputSchemaGenerator.COUNTER.get());
        assertEquals(1, CountingOutputSchemaGenerator.COUNTER.get());

        ToolInfo alpha = toolManager.getTool("alpha");
        Object inputSchema = alpha.inputSchema();
        assertNotNull(inputSchema);
        assertSame(inputSchema, alpha.inputSchema());
        Object outputSchema = alpha.outputSchema();
        assertNotNull(outputSchema);
        assertSame(outputSchema, alpha.outputSchema());
        assertEquals(1, CountingInputSchemaGenerator.COUNTER.get());
        assertEquals(1, CountingOutputSchemaGenerator.COUNTER.get());

        // A tool without structured content has no output schema
        assertNull(toolManager.getTool("bravo").outputSchema());
        assertNotNull(toolManager.getTool("bravo").inputSchema());
    }

    private void assertToolList(ToolsPage page) {
        assertEquals(2, page.tools().size());
        JsonObject alphaInput = page.findByName("alpha").inputSchema();
        assertEquals("integer", alphaInput.getJsonObject("properties").getJsonObject("size").getString("type"));
        JsonObject alphaOutput = page.findByName("alpha").outputSchema();
        assertEquals("integer", alphaOutput.getJsonObject("properties").getJsonObject("val").getString("type"));
    }

    public static class MyPojo {

        private int val;

        public int getVal() {
            return val;
        }

        public void setVal(int val) {
            this.val = val;
        }

    }

    public static class MyTools {

        @Tool(structuredContent = true, inputSchema = @InputSchema(generator = CountingInputSchemaGenerator.class), outputSchema = @OutputSchema(generator = CountingOutputSchemaGenerator.class))
        MyPojo alpha(int size) {
            MyPojo pojo = new MyPojo();
            pojo.setVal(size);
            return pojo;
        }

        @Tool
        String bravo(int size) {
            return "" + size;
        }

    }

    @Singleton
    public static class CountingInputSchemaGenerator implements InputSchemaGenerator<JsonObject> {

        static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public JsonObject generate(ToolInfo tool) {
            COUNTER.incrementAndGet();
            return new JsonObject()
                    .put("type", "object")
                    .put("properties", new JsonObject().put("size", new JsonObject().put("type", "integer")));
        }

    }

    @Singleton
    public static class CountingOutputSchemaGenerator implements OutputSchemaGenerator {

        static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public Object generate(Type from) {
            COUNTER.incrementAndGet();
            return new JsonObject()
                    .put("type", "object")
                    .put("properties", new JsonObject().put("val", new JsonObject().put("type", "integer")));
        }

    }

}
