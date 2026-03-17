package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.BooleanSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.EnumSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.IntegerSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.MultiSelectEnumSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.NumberSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.SingleSelectEnumSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema.Format;

public class ElicitationRequestTest {

    @Test
    public void testSchemas() {
        ElicitationRequest.StringSchema strSchema = new StringSchema("title", "desc", 10, 5, Format.EMAIL, true,
                "mail@acme.org");
        assertTrue(strSchema.required());
        assertEquals("mail@acme.org", strSchema.defaultValue());
        assertEquals(
                "{\"type\":\"string\",\"title\":\"title\",\"description\":\"desc\",\"maxLength\":10,\"minLength\":5,\"format\":\"email\",\"default\":\"mail@acme.org\"}",
                strSchema.asJson().encode());

        ElicitationRequest.BooleanSchema boolSchema = new BooleanSchema("title", "desc", true, true);
        assertTrue(boolSchema.required());
        assertTrue(boolSchema.defaultValue());
        assertEquals(
                "{\"type\":\"boolean\",\"title\":\"title\",\"description\":\"desc\",\"default\":true}",
                boolSchema.asJson().encode());

        ElicitationRequest.NumberSchema numSchema = new NumberSchema("title", "desc", 100, 1, true, 10);
        assertTrue(numSchema.required());
        assertEquals(10, numSchema.defaultValue());
        assertEquals(1, numSchema.minimum());
        assertEquals(100, numSchema.maximum());
        assertEquals(
                "{\"type\":\"number\",\"title\":\"title\",\"description\":\"desc\",\"maximum\":100,\"minimum\":1,\"default\":10}",
                numSchema.asJson().encode());

        ElicitationRequest.IntegerSchema intSchema = new IntegerSchema("title", "desc", 100, 1, true, 10);
        assertTrue(intSchema.required());
        assertEquals(10, intSchema.defaultValue());
        assertEquals(1, intSchema.minimum());
        assertEquals(100, intSchema.maximum());
        assertEquals(
                "{\"type\":\"integer\",\"title\":\"title\",\"description\":\"desc\",\"maximum\":100,\"minimum\":1,\"default\":10}",
                intSchema.asJson().encode());

        ElicitationRequest.EnumSchema enumSchema = new EnumSchema("title", "desc", List.of("foo", "bar"), List.of("n1", "n2"),
                true, "foo");
        assertTrue(enumSchema.required());
        assertEquals(List.of("foo", "bar"), enumSchema.enumValues());
        assertEquals(
                "{\"type\":\"string\",\"enum\":[\"foo\",\"bar\"],\"title\":\"title\",\"description\":\"desc\",\"enumNames\":[\"n1\",\"n2\"],\"default\":\"foo\"}",
                enumSchema.asJson().encode());

        ElicitationRequest.SingleSelectEnumSchema singleEnumSchema = new SingleSelectEnumSchema("tile", "desc",
                List.of("foo", "bar"), List.of("n1", "n2"), true, "foo");
        assertTrue(singleEnumSchema.required());
        assertEquals(List.of("foo", "bar"), singleEnumSchema.enumValues());
        assertEquals(
                "{\"type\":\"string\",\"title\":\"tile\",\"description\":\"desc\",\"oneOf\":[{\"const\":\"foo\",\"title\":\"n1\"},{\"const\":\"bar\",\"title\":\"n2\"}],\"default\":\"foo\"}",
                singleEnumSchema.asJson().encode());

        ElicitationRequest.MultiSelectEnumSchema multiEnumSchema = new MultiSelectEnumSchema("tile", "desc",
                List.of("foo", "bar"), List.of("n1", "n2"), 1, 2, true, List.of("foo"));
        assertTrue(multiEnumSchema.required());
        assertEquals(List.of("foo", "bar"), multiEnumSchema.enumValues());
        assertEquals(
                "{\"type\":\"array\",\"title\":\"tile\",\"description\":\"desc\",\"minItems\":1,\"maxItems\":2,\"items\":{\"anyOf\":[{\"const\":\"foo\",\"title\":\"n1\"},{\"const\":\"bar\",\"title\":\"n2\"}]},\"default\":[\"foo\"]}",
                multiEnumSchema.asJson().encode());
    }

    @Test
    public void testStringSchemaBuilder() {
        StringSchema schema = StringSchema.builder()
                .setTitle("title")
                .setDescription("desc")
                .setMaxLength(10)
                .setMinLength(5)
                .setFormat(Format.EMAIL)
                .setRequired(true)
                .setDefaultValue("mail@acme.org")
                .build();
        assertTrue(schema.required());
        assertEquals("mail@acme.org", schema.defaultValue());
        assertEquals(
                "{\"type\":\"string\",\"title\":\"title\",\"description\":\"desc\",\"maxLength\":10,\"minLength\":5,\"format\":\"email\",\"default\":\"mail@acme.org\"}",
                schema.asJson().encode());
    }

    @Test
    public void testBooleanSchemaBuilder() {
        BooleanSchema schema = BooleanSchema.builder()
                .setTitle("title")
                .setDescription("desc")
                .setDefaultValue(true)
                .setRequired(true)
                .build();
        assertTrue(schema.required());
        assertTrue(schema.defaultValue());
        assertEquals(
                "{\"type\":\"boolean\",\"title\":\"title\",\"description\":\"desc\",\"default\":true}",
                schema.asJson().encode());
    }

    @Test
    public void testNumberSchemaBuilder() {
        NumberSchema schema = NumberSchema.builder()
                .setTitle("title")
                .setDescription("desc")
                .setMaximum(100)
                .setMinimum(1)
                .setRequired(true)
                .setDefaultValue(10)
                .build();
        assertTrue(schema.required());
        assertEquals(10, schema.defaultValue());
        assertEquals(1, schema.minimum());
        assertEquals(100, schema.maximum());
        assertEquals(
                "{\"type\":\"number\",\"title\":\"title\",\"description\":\"desc\",\"maximum\":100,\"minimum\":1,\"default\":10}",
                schema.asJson().encode());
    }

    @Test
    public void testIntegerSchemaBuilder() {
        IntegerSchema schema = IntegerSchema.builder()
                .setTitle("title")
                .setDescription("desc")
                .setMaximum(100)
                .setMinimum(1)
                .setRequired(true)
                .setDefaultValue(10)
                .build();
        assertTrue(schema.required());
        assertEquals(10, schema.defaultValue());
        assertEquals(1, schema.minimum());
        assertEquals(100, schema.maximum());
        assertEquals(
                "{\"type\":\"integer\",\"title\":\"title\",\"description\":\"desc\",\"maximum\":100,\"minimum\":1,\"default\":10}",
                schema.asJson().encode());
    }

    @Test
    public void testEnumSchemaBuilder() {
        EnumSchema schema = EnumSchema.builder(List.of("foo", "bar"))
                .setTitle("title")
                .setDescription("desc")
                .setEnumNames(List.of("n1", "n2"))
                .setRequired(true)
                .setDefaultValue("foo")
                .build();
        assertTrue(schema.required());
        assertEquals(List.of("foo", "bar"), schema.enumValues());
        assertEquals(
                "{\"type\":\"string\",\"enum\":[\"foo\",\"bar\"],\"title\":\"title\",\"description\":\"desc\",\"enumNames\":[\"n1\",\"n2\"],\"default\":\"foo\"}",
                schema.asJson().encode());
    }

    @Test
    public void testSingleSelectEnumSchemaBuilder() {
        SingleSelectEnumSchema schema = SingleSelectEnumSchema.builder(List.of("foo", "bar"))
                .setTitle("tile")
                .setDescription("desc")
                .setEnumTitles(List.of("n1", "n2"))
                .setRequired(true)
                .setDefaultValue("foo")
                .build();
        assertTrue(schema.required());
        assertEquals(List.of("foo", "bar"), schema.enumValues());
        assertEquals(
                "{\"type\":\"string\",\"title\":\"tile\",\"description\":\"desc\",\"oneOf\":[{\"const\":\"foo\",\"title\":\"n1\"},{\"const\":\"bar\",\"title\":\"n2\"}],\"default\":\"foo\"}",
                schema.asJson().encode());
    }

    @Test
    public void testMultiSelectEnumSchemaBuilder() {
        MultiSelectEnumSchema schema = MultiSelectEnumSchema.builder(List.of("foo", "bar"))
                .setTitle("tile")
                .setDescription("desc")
                .setEnumTitles(List.of("n1", "n2"))
                .setMinItems(1)
                .setMaxItems(2)
                .setRequired(true)
                .setDefaultValues(List.of("foo"))
                .build();
        assertTrue(schema.required());
        assertEquals(List.of("foo", "bar"), schema.enumValues());
        assertEquals(
                "{\"type\":\"array\",\"title\":\"tile\",\"description\":\"desc\",\"minItems\":1,\"maxItems\":2,\"items\":{\"anyOf\":[{\"const\":\"foo\",\"title\":\"n1\"},{\"const\":\"bar\",\"title\":\"n2\"}]},\"default\":[\"foo\"]}",
                schema.asJson().encode());
    }

}
