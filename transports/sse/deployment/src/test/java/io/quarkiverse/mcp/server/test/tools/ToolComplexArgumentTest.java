package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.tools.ToolComplexArgumentTest.MyTools.MyArg;
import io.quarkus.test.QuarkusUnitTest;

public class ToolComplexArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testComplexArguments() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("alpha", Map.of("myArg", new MyArg(10, List.of("foo", "bar"))), r -> {
                    assertEquals("MyArg[price=10, names=[foo, bar]]", r.content().get(0).asText().text());
                })
                .toolsCall("alphas", Map.of("myArgs", List.of(new MyArg(10, List.of("foo", "bar")))), r -> {
                    assertEquals("[MyArg[price=10, names=[foo, bar]]]", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String alpha(MyArg myArg) {
            return myArg.toString();
        }

        @Tool
        String alphas(List<MyArg> myArgs) {
            return myArgs.toString();
        }

        public record MyArg(int price, List<String> names) {

        }

    }

}
