package io.quarkiverse.mcp.server.test.tools.inputschema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InputSchemaGenerator;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.InputSchema;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class CustomInputSchemaGeneratorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(5000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyPojo.class, MySchemaGenerator.class));

    @Test
    public void testContents() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList(page -> {
                    assertEquals(2, page.tools().size());
                    JsonObject alphaSchema = page.findByName("alpha").inputSchema();
                    assertNotNull(alphaSchema);
                    assertEquals("integer", alphaSchema.getJsonObject("properties").getJsonObject("val").getString("type"));
                    assertEquals("baz", alphaSchema.getJsonObject("properties").getJsonObject("val").getString("foo"));
                    JsonObject bravoSchema = page.findByName("bravo").inputSchema();
                    assertNotNull(bravoSchema);
                    assertEquals("integer", bravoSchema.getJsonObject("properties").getJsonObject("val").getString("type"));
                    assertEquals("bazzz", bravoSchema.getJsonObject("properties").getJsonObject("val").getString("foo"));
                })
                .toolsCall("alpha", toolResponse -> {
                    assertEquals(1, toolResponse.content().size());
                    assertEquals("{\"val\":10}", toolResponse.content().get(0).asText().text());
                })
                .toolsCall("bravo", toolResponse -> {
                    assertEquals(1, toolResponse.content().size());
                    assertEquals("{\"val\":10}", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
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

        @Tool(inputSchema = @InputSchema(generator = MySchemaGenerator.class))
        MyPojo alpha() {
            MyPojo pojo = new MyPojo();
            pojo.setVal(10);
            return pojo;
        }

        @Startup
        void onStart() {
            toolManager.newTool("bravo")
                    .setDescription("Use bravo if you feel tired")
                    .setInputSchema(
                            // "type" : "object",
                            // "properties" : {
                            //    "val" : {
                            //      "type" : "integer",
                            //      "foo"  : "bazzz",
                            //    }
                            //  }
                            new JsonObject()
                                    .put("type", "object")
                                    .put("properties", new JsonObject()
                                            .put("val", new JsonObject()
                                                    .put("type", "integer")
                                                    .put("foo", "bazzz"))))
                    .setHandler(toolArgs -> {
                        MyPojo pojo = new MyPojo();
                        pojo.setVal(10);
                        return ToolResponse.success(Json.encode(pojo));
                    })
                    .register();
        }

    }

    @Singleton
    public static class MySchemaGenerator implements InputSchemaGenerator<Object> {

        @Override
        public Object generate(ToolManager.ToolInfo tool) {
            // "type" : "object",
            // "properties" : {
            //    "val" : {
            //      "type" : "integer",
            //      "foo"  : "baz",
            //    }
            //  }
            return new JsonObject()
                    .put("type", "object")
                    .put("properties", new JsonObject()
                            .put("val", new JsonObject()
                                    .put("type", "integer")
                                    .put("foo", "baz")));
        }

    }

}
