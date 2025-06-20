package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ContentEncoder;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolCustomContentEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyObject.class, MyObjectEncoder.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("bravo", Map.of("price", 10), r -> {
                    assertFalse(r.isError());
                    assertEquals("MyObject[name=foo, sum=20, valid=true]", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public record MyObject(String name, int sum, boolean valid) {

    }

    public static class MyTools {

        @Tool
        MyObject bravo(int price) {
            return new MyObject("foo", price * 2, true);
        }

    }

    @Singleton
    @Priority(1)
    public static class MyObjectEncoder implements ContentEncoder<MyObject> {

        @Override
        public boolean supports(Class<?> runtimeType) {
            return MyObject.class.equals(runtimeType);
        }

        @Override
        public Content encode(MyObject value) {
            return new TextContent(value.toString());
        }

    }

}
