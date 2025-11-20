package io.quarkiverse.mcp.server.test.tools;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class SupportedArgumentTypesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("tool", Map.ofEntries(
                        entry("bool2", true),
                        entry("bool1", false),
                        entry("num1", 1),
                        entry("num3", 2.0),
                        entry("num2", 8),
                        entry("array1", new int[] { 1, 2 }),
                        entry("array2", List.of("foo", "bar")),
                        entry("array3", List.of(new MyPojo("baz"))),
                        entry("array4", List.of(Integer.parseInt("42"))),
                        entry("enum1", TimeUnit.HOURS),
                        entry("str1", "foo"),
                        entry("obj1", new MyPojo("bar"))),
                        toolResult -> {
                            assertEquals("falsetrue182.0[1, 2][foo, bar][MyPojo[name=baz]][42]HOURSfooMyPojo[name=bar]",
                                    toolResult.content().get(0).asText().text());
                        })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String tool(boolean bool1, Boolean bool2,
                int num1, Integer num2, Number num3,
                int[] array1, String[] array2, MyPojo[] array3, List<Integer> array4,
                TimeUnit enum1,
                String str1,
                MyPojo obj1) {
            return "" + bool1 + bool2 + num1 + num2 + num3 + Arrays.toString(array1) + Arrays.toString(array2)
                    + Arrays.toString(array3) + array4 + enum1 + str1 + obj1;
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
