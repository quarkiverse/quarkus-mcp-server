package io.quarkiverse.mcp.server.test.tools.structcontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.OutputSchema;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class FailingOutputSchemaGeneratorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(5000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyPojo.class, FailingSchemaGenerator.class));

    @Test
    public void testContents() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList()
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.code());
                })
                .send()
                .toolsCall("delta", toolResponse -> {
                    assertEquals(0, toolResponse.content().size());
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

        @Tool(description = "With custom schema generator", structuredContent = true, outputSchema = @OutputSchema(generator = FailingSchemaGenerator.class))
        MyPojo delta() {
            MyPojo pojo = new MyPojo();
            pojo.setVal(10);
            return pojo;
        }
    }

    @Singleton
    public static class FailingSchemaGenerator implements OutputSchemaGenerator {

        @Override
        public Object generate(Class<?> from) {
            throw new IllegalStateException("Bum!");
        }

    }

}
