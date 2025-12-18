package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class InvalidArgumentTypeTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("bravo", Map.of("price", true, "timeUnit", TimeUnit.DAYS, "isActive", true), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    String text = toolResponse.content().get(0).asText().text();
                    assertEquals("Invalid argument [price] - value does not match int", text);
                })
                .toolsCall("bravo", Map.of("price", 1, "timeUnit", TimeUnit.DAYS, "isActive", "hello"), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    String text = toolResponse.content().get(0).asText().text();
                    assertEquals("Invalid argument [isActive] - value does not match java.lang.Boolean", text);
                })
                .toolsCall("bravo", Map.of("price", 1, "timeUnit", "AGES", "isActive", true), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    String text = toolResponse.content().get(0).asText().text();
                    assertEquals("Invalid argument [timeUnit] - AGES is not an enum constant of java.util.concurrent.TimeUnit",
                            text);
                })
                .toolsCall("bravo", Map.of("price", 1, "timeUnit", TimeUnit.DAYS, "isActive", true, "pojo", true),
                        toolResponse -> {
                            assertTrue(toolResponse.isError());
                            String text = toolResponse.content().get(0).asText().text();
                            assertEquals(
                                    "Invalid argument [pojo] - value does not match io.quarkiverse.mcp.server.test.tools.InvalidArgumentTypeTest$MyPojo",
                                    text);
                        })
                .toolsCall("bravo", Map.of("price", 1, "timeUnit", TimeUnit.DAYS, "isActive", true, "pojo", new MyPojo("foo")),
                        toolResult -> {
                            assertEquals("1DAYStrueMyPojo[name=foo]", toolResult.content().get(0).asText().text());
                        })
                .toolsCall("charlie", Map.of("pojos", List.of(1, 2)), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    String text = toolResponse.content().get(0).asText().text();
                    assertEquals(
                            "Invalid argument [pojos] - unable to convert JSON array to java.util.List<io.quarkiverse.mcp.server.test.tools.InvalidArgumentTypeTest$MyPojo>",
                            text);
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String bravo(int price, TimeUnit timeUnit, Boolean isActive, Optional<MyPojo> pojo) {
            return "" + price + timeUnit + isActive + pojo.get().toString();
        }

        @Tool
        String charlie(List<MyPojo> pojos) {
            return pojos.toString();
        }
    }

    public static class MyPojo {

        private String name;

        public MyPojo() {
        }

        public MyPojo(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "MyPojo[name=" + name + "]";
        }

    }

}
