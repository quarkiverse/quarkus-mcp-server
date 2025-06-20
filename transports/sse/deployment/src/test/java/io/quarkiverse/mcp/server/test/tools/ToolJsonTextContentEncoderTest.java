package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class ToolJsonTextContentEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyObject.class));

    @Test
    public void testEncoder() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("bravo", Map.of("price", 1), r -> {
                    assertFalse(r.isError());
                    assertEquals("{\"name\":\"foo\",\"sum\":2,\"valid\":true}", r.content().get(0).asText().text());
                })
                .toolsCall("uni_bravo", Map.of("price", 1), r -> {
                    assertFalse(r.isError());
                    assertEquals("{\"name\":\"foo\",\"sum\":3,\"valid\":true}", r.content().get(0).asText().text());
                })
                .toolsCall("list_bravo", Map.of("price", 1), r -> {
                    assertFalse(r.isError());
                    assertEquals("{\"name\":\"foo\",\"sum\":4,\"valid\":true}", r.content().get(0).asText().text());
                })
                .toolsCall("uni_list_bravo", Map.of("price", 1), r -> {
                    assertFalse(r.isError());
                    assertEquals("{\"name\":\"foo\",\"sum\":5,\"valid\":true}", r.content().get(0).asText().text());
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

        @Tool
        Uni<MyObject> uni_bravo(int price) {
            return Uni.createFrom().item(new MyObject("foo", price * 3, true));
        }

        @Tool
        List<MyObject> list_bravo(int price) {
            return List.of(new MyObject("foo", price * 4, true));
        }

        @Tool
        Uni<List<MyObject>> uni_list_bravo(int price) {
            return Uni.createFrom().item(List.of(new MyObject("foo", price * 5, true)));
        }

    }

}
