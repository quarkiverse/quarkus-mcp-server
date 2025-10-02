package io.quarkiverse.mcp.server.test.tools.inputschema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.GlobalInputSchemaGenerator;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolsPage;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class CachingGlobalInputSchemaGeneratorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(5000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyPojo.class, CachingGlobalSchemaGenerator.class));

    @Test
    public void testContents() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        assertEquals(0, CachingGlobalSchemaGenerator.CACHE.size());
        assertEquals(0, CachingGlobalSchemaGenerator.COUNTER.get());
        client.when()
                .toolsList(this::assertToolList)
                .toolsList(this::assertToolList)
                .toolsList(this::assertToolList)
                .thenAssertResults();
        assertEquals(3, CachingGlobalSchemaGenerator.COUNTER.get());
    }

    private void assertToolList(ToolsPage page) {
        assertEquals(1, page.tools().size());
        JsonObject alphaSchema = page.findByName("alpha").inputSchema();
        assertNotNull(alphaSchema);
        assertEquals("integer", alphaSchema.getJsonObject("properties").getJsonObject("size").getString("type"));
        assertEquals(1, CachingGlobalSchemaGenerator.CACHE.size());
        // "type" : "object",
        // "properties" : {
        //    "size" : {
        //      "type" : "integer",
        //    }
        //  }
        assertEquals("{\"type\":\"object\",\"properties\":{\"size\":{\"type\":\"integer\"}},\"required\":[\"size\"]}",
                CachingGlobalSchemaGenerator.CACHE.get("alpha").asJson().strip());
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

        @Inject
        ToolManager toolManager;

        @Tool
        MyPojo alpha(int size) {
            MyPojo pojo = new MyPojo();
            pojo.setVal(size);
            return pojo;
        }

    }

    @Priority(1)
    @Decorator
    public static class CachingGlobalSchemaGenerator implements GlobalInputSchemaGenerator {

        static final ConcurrentMap<String, InputSchema> CACHE = new ConcurrentHashMap<>();
        static final AtomicInteger COUNTER = new AtomicInteger();

        @Inject
        @Delegate
        GlobalInputSchemaGenerator delegate;

        @Override
        public InputSchema generate(ToolInfo tool) {
            COUNTER.incrementAndGet();
            return CACHE.computeIfAbsent(tool.name(), k -> {
                Log.infof("Compute input schema for tool: %s", tool.name());
                return delegate.generate(tool);
            });
        }

    }

}
