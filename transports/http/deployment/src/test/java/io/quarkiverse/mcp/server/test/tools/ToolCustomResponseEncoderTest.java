package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.ToolResponseEncoder;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolCustomResponseEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyObject.class, MyObjectEncoder.class));

    @Test
    public void testEncoder() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("bravo", Map.of("price", 10), r -> {
                    assertTrue(r.isError());
                    assertEquals("MyObject[name=foo, sum=20, valid=true]", r.content().get(0).asText().text());
                })
                .toolsCall("bravos", Map.of("price", 10), r -> {
                    assertTrue(r.isError());
                    assertEquals("MyObject[name=foo, sum=20, valid=true]", r.content().get(0).asText().text());
                })
                .toolsCall("deltas", Map.of("price", 10), r -> {
                    assertFalse(r.isError());
                    assertEquals("[{\"name\":\"foo\",\"sum\":20,\"valid\":true}]", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public record MyObject(String name, int sum, boolean valid) {

    }

    public static class MyTools {

        // -> MyObjectEncoder
        @Tool
        MyObject bravo(int price) {
            return new MyObject("foo", price * 2, true);
        }

        // -> MyObjectEncoder
        @Tool
        List<MyObject> bravos(int price) {
            return List.of(new MyObject("foo", price * 2, true));
        }

        // -> JsonTextContentEncoder
        @Tool
        Collection<MyObject> deltas(int price) {
            return Set.of(new MyObject("foo", price * 2, true));
        }

    }

    @Singleton
    @Priority(1)
    public static class MyObjectEncoder implements ToolResponseEncoder<Object> {

        @Override
        public boolean supports(Class<?> runtimeType) {
            return MyObject.class.equals(runtimeType) || List.class.isAssignableFrom(runtimeType);
        }

        @Override
        public ToolResponse encode(Object value) {
            List<Content> content;
            if (value instanceof MyObject myObject) {
                content = List.of(new TextContent(myObject.toString()));
            } else if (value instanceof List list) {
                content = List.of(new TextContent(list.get(0).toString()));
            } else {
                throw new IllegalArgumentException();
            }
            return new ToolResponse(true, content);
        }

    }

}
