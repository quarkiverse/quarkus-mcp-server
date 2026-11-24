package io.quarkiverse.mcp.server.test.tools;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class SupportedArgumentTypesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyPojo.class, MyDto.class, MyDtoCustomizer.class,
                            MyStringDto.class, MyStringDtoCustomizer.class));

    @Test
    public void testSupportedTypes() {
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
                                    toolResult.firstContent().asText().text());
                        })
                .thenAssertResults();
    }

    @Test
    public void testCustomJacksonDeserializer() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // MyDto uses custom property names "n" and "v" via a custom deserializer
                .toolsCall("dtoTool", Map.of("dto", Map.of("n", "alpha", "v", 42)), toolResult -> {
                    assertFalse(toolResult.isError());
                    assertEquals("MyDto[name=alpha, value=42]", toolResult.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    public void testCustomJacksonStringDeserializer() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // MyStringDto is serialized as a plain JSON string "alpha:42" via a custom serializer;
                // the custom deserializer parses it back
                .toolsCall("stringDtoTool", Map.of("dto", "alpha:42"), toolResult -> {
                    assertFalse(toolResult.isError());
                    assertEquals("MyStringDto[name=alpha, value=42]", toolResult.firstContent().asText().text());
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

        @Tool
        String dtoTool(MyDto dto) {
            return dto.toString();
        }

        @Tool
        String stringDtoTool(MyStringDto dto) {
            return dto.toString();
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

    public record MyDto(String name, int value) {
    }

    @Singleton
    public static class MyDtoCustomizer implements JsonMapperBuilderCustomizer {
        @Override
        public void customize(JsonMapper.Builder builder) {
            SimpleModule module = new SimpleModule();
            module.addSerializer(MyDto.class, new ValueSerializer<MyDto>() {
                @Override
                public void serialize(MyDto myDto, JsonGenerator gen, SerializationContext serializers) {
                    gen.writeStartObject();
                    gen.writeStringProperty("n", myDto.name());
                    gen.writeNumberProperty("v", myDto.value());
                    gen.writeEndObject();
                }
            });
            module.addDeserializer(MyDto.class, new ValueDeserializer<MyDto>() {
                @Override
                public MyDto deserialize(JsonParser p, DeserializationContext ctxt) {
                    String name = null;
                    int value = 0;
                    while (p.nextToken() != tools.jackson.core.JsonToken.END_OBJECT) {
                        String field = p.currentName();
                        p.nextToken();
                        if ("n".equals(field)) {
                            name = p.getText();
                        } else if ("v".equals(field)) {
                            value = p.getIntValue();
                        }
                    }
                    return new MyDto(name, value);
                }
            });
            builder.addModule(module);
        }
    }

    public record MyStringDto(String name, int value) {
    }

    @Singleton
    public static class MyStringDtoCustomizer implements JsonMapperBuilderCustomizer {

        @Override
        public void customize(JsonMapper.Builder builder) {
            SimpleModule module = new SimpleModule();
            module.addSerializer(MyStringDto.class, new ValueSerializer<MyStringDto>() {
                @Override
                public void serialize(MyStringDto dto, JsonGenerator gen, SerializationContext serializers) {
                    gen.writeString(dto.name() + ":" + dto.value());
                }
            });
            module.addDeserializer(MyStringDto.class, new ValueDeserializer<MyStringDto>() {
                @Override
                public MyStringDto deserialize(JsonParser p, DeserializationContext ctxt) {
                    String[] parts = p.getText().split(":");
                    return new MyStringDto(parts[0], Integer.parseInt(parts[1]));
                }
            });
            builder.addModule(module);
        }
    }

}
