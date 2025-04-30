package io.quarkiverse.mcp.server.test.tools.defaultvalues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.tools.defaultvalues.ToolDefaultValuesTest.MyTools.DurationConverter;
import io.quarkiverse.mcp.server.test.tools.defaultvalues.ToolDefaultValuesTest.MyTools.LongConverter;
import io.quarkiverse.mcp.server.test.tools.defaultvalues.ToolDefaultValuesTest.MyTools.MyArgConverter;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ToolDefaultValuesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyArg.class, LongConverter.class, MyArgConverter.class,
                            DurationConverter.class));

    @Test
    public void testDefaultValues() {
        initClient();

        JsonObject toolListMessage = newMessage("tools/list");
        send(toolListMessage);

        JsonObject toolListResponse = waitForLastResponse();

        JsonObject toolListResult = assertResponseMessage(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(2, tools.size());

        assertTool(tools, "alpha", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(10, properties.size());

            JsonObject boolVal = properties.getJsonObject("boolVal");
            assertNotNull(boolVal);
            assertEquals("boolean", boolVal.getString("type"));
            assertEquals(true, boolVal.getBoolean("default"));

            JsonObject timeUnit = properties.getJsonObject("timeUnit");
            assertNotNull(timeUnit);
            assertEquals("string", timeUnit.getString("type"));
            assertEquals("HOURS", timeUnit.getString("default"));

            assertTrue(schema.getJsonArray("required").isEmpty());
        });

        assertTool(tools, "bravo", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(1, properties.size());

            JsonObject boolVal = properties.getJsonObject("boolVal");
            assertNotNull(boolVal);
            assertEquals("boolean", boolVal.getString("type"));
            assertEquals(true, boolVal.getBoolean("default"));

            assertTrue(schema.getJsonArray("required").isEmpty());
        });

        assertResult("alpha",
                new JsonObject(),
                "true::2::2::2::2.1::2.1::fooo::HOURS::PT5S::MyArg[price=10, names=[foo, bar, baz]]");
        assertResult("bravo",
                new JsonObject(), "true");
    }

    private void assertTool(JsonArray tools, String name, String description, Consumer<JsonObject> inputSchemaAsserter) {
        JsonObject tool = null;
        for (int i = 0; i < tools.size(); i++) {
            JsonObject t = tools.getJsonObject(i);
            if (name.equals(t.getString("name"))) {
                tool = t;
            }
        }
        if (description != null) {
            assertEquals(description, tool.getString("description"));
        }
        if (inputSchemaAsserter != null) {
            inputSchemaAsserter.accept(tool.getJsonObject("inputSchema"));
        }
    }

    private void assertResult(String toolName, JsonObject arguments, String expectedErrorText) {
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", toolName)
                        .put("arguments", arguments));
        send(message);
        JsonObject toolCallResponse = waitForLastResponse();
        JsonObject toolCallResult = assertResponseMessage(message, toolCallResponse);
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedErrorText, textContent.getString("text"));
    }

    public record MyArg(int price, List<String> names) {
    }

    public static class MyTools {

        @Inject
        ToolManager manager;

        @Startup
        void start() {
            manager.newTool("bravo")
                    .setDescription("Bravo tool")
                    .addArgument("boolVal", null, false, boolean.class, "true")
                    .setHandler(args -> ToolResponse.success(args.args().get("boolVal").toString()))
                    .register();
        }

        @Tool
        String alpha(
                @ToolArg(defaultValue = "true") boolean boolVal,
                @ToolArg(defaultValue = "2") short shortVal,
                @ToolArg(defaultValue = "2") int intVal,
                @ToolArg(defaultValue = "two") Long longVal,
                @ToolArg(defaultValue = "2.1") float floatVal,
                @ToolArg(defaultValue = "2.1") Double doubleVal,
                @ToolArg(defaultValue = "fooo") String strVal,
                @ToolArg(defaultValue = "hours") TimeUnit timeUnit,
                @ToolArg(defaultValue = "5s") Duration duration,
                @ToolArg(defaultValue = "10::foo,bar,baz") MyArg myArg) {
            return "" + boolVal + "::" + shortVal + "::" + intVal + "::" + longVal + "::" + floatVal + "::" + doubleVal + "::"
                    + strVal + "::" + timeUnit + "::" + duration + "::" + myArg;
        }

        @Priority(1) // higher priority than the built-in converter for Long
        public static class LongConverter implements DefaultValueConverter<Long> {

            @Override
            public Long convert(String defaultValue) {
                return defaultValue.equals("two") ? 2l : 0l;
            }

        }

        public static class MyArgConverter implements DefaultValueConverter<MyArg> {

            @Override
            public MyArg convert(String defaultValue) {
                String[] parts = defaultValue.split("::");
                String priceVal = parts[0];
                String namesVal = parts[1];
                return new MyArg(Integer.parseInt(priceVal), Arrays.stream(namesVal.split(",")).toList());
            }

        }

        public static class DurationConverter extends BaseConverter<Duration> {

            @Override
            protected Duration doConvert(String defaultValue) {
                return io.quarkus.runtime.configuration.DurationConverter.parseDuration(defaultValue);
            }

        }

        static abstract class BaseConverter<T> implements DefaultValueConverter<T> {

            @Override
            public T convert(String defaultValue) {
                return doConvert(defaultValue);
            }

            protected abstract T doConvert(String defaultValue);

        }

    }

}
