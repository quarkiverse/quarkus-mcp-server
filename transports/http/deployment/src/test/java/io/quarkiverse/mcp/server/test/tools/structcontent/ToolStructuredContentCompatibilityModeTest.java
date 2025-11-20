package io.quarkiverse.mcp.server.test.tools.structcontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolStructuredContentCompatibilityModeTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(5000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyPojo.class))
            .overrideConfigKey("quarkus.mcp.server.tools.structured-content.compatibility-mode", "true");

    @Test
    public void testContents() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList(page -> {
                    assertEquals(1, page.tools().size());
                    JsonObject bravoSchema = page.findByName("bravo").outputSchema();
                    assertNotNull(bravoSchema);
                    assertEquals("integer", bravoSchema.getJsonObject("properties").getJsonObject("val").getString("type"));
                })
                .toolsCall("bravo", toolResponse -> {
                    assertEquals(1, toolResponse.content().size());
                    assertEquals("{\"val\":10}", toolResponse.content().get(0).asText().text());
                    assertNotNull(toolResponse.structuredContent());
                    if (toolResponse.structuredContent() instanceof JsonObject json) {
                        assertEquals(10, json.getInteger("val"));
                    } else {
                        fail("Not a JsonObject");
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

        @Tool(description = "Use bravo to be brave", structuredContent = true)
        MyPojo bravo() {
            MyPojo pojo = new MyPojo();
            pojo.setVal(10);
            return pojo;
        }

    }

}
