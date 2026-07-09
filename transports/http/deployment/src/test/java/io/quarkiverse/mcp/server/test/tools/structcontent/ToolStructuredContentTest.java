package io.quarkiverse.mcp.server.test.tools.structcontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.OutputSchema;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.arc.impl.ParameterizedTypeImpl;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolStructuredContentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(5000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyPojo.class, MySchemaGenerator.class));

    @Test
    public void testContents() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList(page -> {
                    assertEquals(7, page.tools().size());
                    JsonObject alphaSchema = page.findByName("alpha").outputSchema();
                    assertNotNull(alphaSchema);
                    assertEquals("integer", alphaSchema.getJsonObject("properties").getJsonObject("val").getString("type"));
                    JsonObject bravoSchema = page.findByName("bravo").outputSchema();
                    assertNotNull(bravoSchema);
                    assertEquals("integer", bravoSchema.getJsonObject("properties").getJsonObject("val").getString("type"));
                    JsonObject charlieSchema = page.findByName("charlie").outputSchema();
                    assertNotNull(charlieSchema);
                    assertEquals("integer", charlieSchema.getJsonObject("properties").getJsonObject("val").getString("type"));
                    JsonObject deltaSchema = page.findByName("delta").outputSchema();
                    assertNotNull(deltaSchema);
                    assertEquals("integer", deltaSchema.getJsonObject("properties").getJsonObject("val").getString("type"));
                    assertEquals("baz", deltaSchema.getJsonObject("properties").getJsonObject("val").getString("foo"));
                    JsonObject echoSchema = page.findByName("echo").outputSchema();
                    assertNotNull(echoSchema);
                    assertEquals("integer", echoSchema.getJsonObject("properties").getJsonObject("val").getString("type"));
                    JsonObject foxtrotSchema = page.findByName("foxtrot").outputSchema();
                    assertNotNull(foxtrotSchema);
                    assertEquals("array", foxtrotSchema.getString("type"));
                    assertEquals("integer",
                            foxtrotSchema.getJsonObject("items").getJsonObject("properties").getJsonObject("val")
                                    .getString("type"));
                    JsonObject golfSchema = page.findByName("golf").outputSchema();
                    assertNotNull(golfSchema);
                    assertEquals("array", golfSchema.getString("type"));
                    assertEquals("integer",
                            golfSchema.getJsonObject("items").getJsonObject("properties").getJsonObject("val")
                                    .getString("type"));
                })
                .toolsCall("alpha", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonObject json) {
                        assertEquals(10, json.getInteger("val"));
                    } else {
                        fail("Not a JsonObject");
                    }
                })
                .toolsCall("bravo", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonObject json) {
                        assertEquals(10, json.getInteger("val"));
                    } else {
                        fail("Not a JsonObject");
                    }
                })
                .toolsCall("charlie", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonObject json) {
                        assertEquals(10, json.getInteger("val"));
                    } else {
                        fail("Not a JsonObject");
                    }
                })
                .toolsCall("delta", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonObject json) {
                        assertEquals(10, json.getInteger("val"));
                    } else {
                        fail("Not a JsonObject");
                    }
                })
                .toolsCall("echo", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonObject json) {
                        assertEquals(7, json.getInteger("val"));
                    } else {
                        fail("Not a JsonObject");
                    }
                })
                .toolsCall("foxtrot", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonArray jsonArray) {
                        assertEquals(2, jsonArray.size());
                        assertEquals(1, jsonArray.getJsonObject(0).getInteger("val"));
                        assertEquals(2, jsonArray.getJsonObject(1).getInteger("val"));
                    } else {
                        fail("Not a JsonArray");
                    }
                })
                .toolsCall("golf", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonArray jsonArray) {
                        assertEquals(2, jsonArray.size());
                        assertEquals(3, jsonArray.getJsonObject(0).getInteger("val"));
                        assertEquals(4, jsonArray.getJsonObject(1).getInteger("val"));
                    } else {
                        fail("Not a JsonArray");
                    }
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

        @Tool(outputSchema = @OutputSchema(from = MyPojo.class))
        ToolResponse alpha() {
            MyPojo pojo = new MyPojo();
            pojo.setVal(10);
            return ToolResponse.structuredSuccess(pojo);
        }

        @Tool(description = "Use bravo to be brave", structuredContent = true)
        MyPojo bravo() {
            MyPojo pojo = new MyPojo();
            pojo.setVal(10);
            return pojo;
        }

        @Tool(description = "With custom schema generator", structuredContent = true, outputSchema = @OutputSchema(generator = MySchemaGenerator.class))
        MyPojo delta() {
            MyPojo pojo = new MyPojo();
            pojo.setVal(10);
            return pojo;
        }

        @Startup
        void onStart() {
            toolManager.newTool("charlie")
                    .setDescription("Use charlie if you feel tired")
                    .generateOutputSchema(MyPojo.class)
                    .setHandler(toolArgs -> {
                        MyPojo pojo = new MyPojo();
                        pojo.setVal(10);
                        return ToolResponse.structuredSuccess(pojo);
                    })
                    .register();
            toolManager.newTool("golf")
                    .setDescription("Use golf for a list")
                    .generateOutputSchema(new ParameterizedTypeImpl(List.class, MyPojo.class))
                    .setHandler(toolArgs -> {
                        MyPojo pojo1 = new MyPojo();
                        pojo1.setVal(3);
                        MyPojo pojo2 = new MyPojo();
                        pojo2.setVal(4);
                        return ToolResponse.structuredSuccess(List.of(pojo1, pojo2));
                    })
                    .register();
        }

        @Tool(description = "Use echo!", structuredContent = true)
        Uni<MyPojo> echo() {
            MyPojo pojo = new MyPojo();
            pojo.setVal(7);
            return Uni.createFrom().item(pojo);
        }

        @Tool(description = "Use foxtrot!", structuredContent = true)
        List<MyPojo> foxtrot() {
            MyPojo pojo1 = new MyPojo();
            pojo1.setVal(1);
            MyPojo pojo2 = new MyPojo();
            pojo2.setVal(2);
            return List.of(pojo1, pojo2);
        }

    }

    @Singleton
    public static class MySchemaGenerator implements OutputSchemaGenerator {

        @Override
        public Object generate(Class<?> from) {
            Objects.requireNonNull(from);
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
