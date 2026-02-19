package io.quarkiverse.mcp.server.test.tools.defaultvalues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.tools.defaultvalues.ToolDefaultValuesTest.MyTools.DurationConverter;
import io.quarkiverse.mcp.server.test.tools.defaultvalues.ToolDefaultValuesTest.MyTools.LongConverter;
import io.quarkiverse.mcp.server.test.tools.defaultvalues.ToolDefaultValuesTest.MyTools.MyArgConverter;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolDefaultValuesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyArg.class, LongConverter.class, MyArgConverter.class,
                            DurationConverter.class));

    @Test
    public void testDefaultValues() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsList(page -> {
                    assertEquals(3, page.tools().size());

                    ToolInfo alpha = page.findByName("alpha");
                    JsonObject alphaSchema = alpha.inputSchema();
                    JsonObject alphaProperties = alphaSchema.getJsonObject("properties");
                    assertEquals(10, alphaProperties.size());

                    JsonObject alphaBoolVal = alphaProperties.getJsonObject("boolVal");
                    assertNotNull(alphaBoolVal);
                    assertEquals("boolean", alphaBoolVal.getString("type"));
                    assertEquals(true, alphaBoolVal.getBoolean("default"));

                    JsonObject timeUnit = alphaProperties.getJsonObject("timeUnit");
                    assertNotNull(timeUnit);
                    assertEquals("string", timeUnit.getString("type"));
                    assertEquals("HOURS", timeUnit.getString("default"));

                    assertTrue(alphaSchema.getJsonArray("required").isEmpty());

                    ToolInfo bravo = page.findByName("bravo");
                    JsonObject bravoSchema = bravo.inputSchema();
                    JsonObject bravoProperties = bravoSchema.getJsonObject("properties");
                    assertEquals(1, bravoProperties.size());

                    JsonObject bravoBoolVal = bravoProperties.getJsonObject("boolVal");
                    assertNotNull(bravoBoolVal);
                    assertEquals("boolean", bravoBoolVal.getString("type"));
                    assertEquals(true, bravoBoolVal.getBoolean("default"));

                    assertTrue(bravoSchema.getJsonArray("required").isEmpty());

                    ToolInfo charlie = page.findByName("charlie");
                    JsonObject charlieSchema = charlie.inputSchema();
                    JsonObject charlieProperties = charlieSchema.getJsonObject("properties");
                    assertEquals(1, charlieProperties.size());
                    JsonObject charlieDoubleVal = charlieProperties.getJsonObject("doubleVal");
                    assertNotNull(charlieDoubleVal);
                    assertEquals("number", charlieDoubleVal.getString("type"));
                    assertEquals(2.1, charlieDoubleVal.getDouble("default"));
                    assertTrue(alphaSchema.getJsonArray("required").isEmpty());
                })
                .toolsCall("alpha", toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("true::2::2::2::2.1::2.1::fooo::HOURS::PT5S::MyArg[price=10, names=[foo, bar, baz]]",
                            toolResponse.content().get(0).asText().text());
                })
                .toolsCall("bravo", toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("true", toolResponse.content().get(0).asText().text());
                })
                .toolsCall("charlie", toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("2.1", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
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

        @Tool
        String charlie(@ToolArg(defaultValue = "2.1") @Positive Double doubleVal) {
            return "" + doubleVal;
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
