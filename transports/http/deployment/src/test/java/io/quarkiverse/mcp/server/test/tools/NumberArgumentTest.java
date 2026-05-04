package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class NumberArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testNumberArguments() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("get_list_of_strings", new JsonObject()
                        .put("id", 10)
                        .put("alpha", 1L)
                        .put("bravo", 10.1)
                        .put("delta", 20.1)
                        .put("charlie", 11)
                        .put("echo", 42.1)
                        .put("foxtrot", 7)
                        .put("golf", 100L)
                        .put("hotel", 3.14).getMap(), r -> {
                            assertFalse(r.isError());
                            assertEquals("10:1:10.1:20.1:11:42.1:7:100:3.14", r.firstContent().asText().text());
                        })
                .thenAssertResults();
    }

    @Test
    public void testEmptyOptionals() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("get_list_of_strings", new JsonObject()
                        .put("id", 10)
                        .put("alpha", 1L)
                        .put("bravo", 10.1)
                        .put("delta", 20.1)
                        .put("charlie", 11).getMap(), r -> {
                            assertFalse(r.isError());
                            assertEquals("10:1:10.1:20.1:11:1.0:-1:-1:-1.0", r.firstContent().asText().text());
                        })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool(name = "get_list_of_strings")
        public String getListOfStrings(Long id, short alpha, Float bravo, Double delta, Byte charlie,
                Optional<Double> echo, OptionalInt foxtrot, OptionalLong golf, OptionalDouble hotel) {
            return id + ":" + alpha + ":" + bravo + ":" + delta + ":" + charlie + ":" + echo.orElse(1.0)
                    + ":" + foxtrot.orElse(-1) + ":" + golf.orElse(-1) + ":" + hotel.orElse(-1.0);
        }
    }
}
