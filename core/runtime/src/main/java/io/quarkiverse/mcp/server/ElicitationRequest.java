package io.quarkiverse.mcp.server;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Request from the server to obtain additional information from the client.
 */
public interface ElicitationRequest {

    /**
     * @return the message for the user
     */
    String message();

    /**
     * @return the requested schema
     * @see StringSchema
     * @see BooleanSchema
     * @see IntegerSchema
     * @see NumberSchema
     * @see SingleSelectEnumSchema
     * @see MultiSelectEnumSchema
     */
    Map<String, PrimitiveSchema> requestedSchema();

    /**
     * Send a message to the client.
     * <p>
     * If the client does not respond before the timeout expires then the returned {@code Uni} fails with
     * {@link TimeoutException}. The default timeout is configured with the
     * {@code quarkus.mcp.server.elicitation.default-timeout}
     * config property.
     *
     * @return a new {@link Uni} that completes with a {@code ElicitationResponse}
     */
    @CheckReturnValue
    Uni<ElicitationResponse> send();

    /**
     * Send a message to the client and wait for the result.
     * <p>
     * Note that this method will block until the client sends the response.
     * <p>
     * If the client does not respond before the timeout expires then a {@link TimeoutException} is thrown. The default timeout
     * is configured with the {@code quarkus.mcp.server.elicitation.default-timeout} config property.
     *
     * @return the response
     */
    default ElicitationResponse sendAndAwait() {
        return send().await().indefinitely();
    }

    /**
     * @see Elicitation#requestBuilder()
     */
    interface Builder {

        Builder setMessage(String message);

        /**
         * Add property to the requested schema.
         *
         * @param key
         * @param schema
         * @return self
         */
        Builder addSchemaProperty(String key, PrimitiveSchema schema);

        /**
         * If no timeout is set then the default value configured with the
         * {@code quarkus.mcp.server.elicitation.default-timeout} is used.
         *
         * @param timeout
         * @return self
         */
        Builder setTimeout(Duration timeout);

        /**
         *
         * @return a new elicitation request
         */
        ElicitationRequest build();

    }

    interface PrimitiveSchema {

        boolean required();

        JsonObject asJson();

    }

    record BooleanSchema(String title,
            String description,
            Boolean defaultValue,
            boolean required)
            implements
                PrimitiveSchema {

        public static BooleanSchemaBuilder builder() {
            return new BooleanSchemaBuilder();
        }

        public BooleanSchema() {
            this(null, null, null, false);
        }

        public BooleanSchema(boolean required) {
            this(null, null, null, required);
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject()
                    .put("type", "boolean");
            if (title != null) {
                ret.put("title", title);
            }
            if (description != null) {
                ret.put("description", description);
            }
            if (defaultValue != null) {
                ret.put("default", defaultValue);
            }
            return ret;
        }

        public static class BooleanSchemaBuilder {

            private String title;
            private String description;
            private Boolean defaultValue;
            private boolean required;

            public BooleanSchemaBuilder setTitle(String title) {
                this.title = title;
                return this;
            }

            public BooleanSchemaBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public BooleanSchemaBuilder setDefaultValue(Boolean defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            public BooleanSchemaBuilder setRequired(boolean required) {
                this.required = required;
                return this;
            }

            public BooleanSchema build() {
                return new BooleanSchema(title, description, defaultValue, required);
            }

        }

    }

    record NumberSchema(String title,
            String description,
            Number maximum,
            Number minimum,
            boolean required,
            Number defaultValue)
            implements
                PrimitiveSchema {

        public static NumberSchemaBuilder builder() {
            return new NumberSchemaBuilder();
        }

        public NumberSchema(String title, String description, Number maximum, Number minimum, boolean required) {
            this(title, description, maximum, minimum, required, null);
        }

        public NumberSchema() {
            this(null, null, null, null, false);
        }

        public NumberSchema(boolean required) {
            this(null, null, null, null, required);
        }

        public NumberSchema(Number defaultValue) {
            this(null, null, null, null, false, defaultValue);
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject()
                    .put("type", "number");
            if (title != null) {
                ret.put("title", title);
            }
            if (description != null) {
                ret.put("description", description);
            }
            if (maximum != null) {
                ret.put("maximum", maximum);
            }
            if (minimum != null) {
                ret.put("minimum", minimum);
            }
            if (defaultValue != null) {
                ret.put("default", defaultValue);
            }
            return ret;
        }

        public static class NumberSchemaBuilder {

            private String title;
            private String description;
            private Number maximum;
            private Number minimum;
            private boolean required;
            private Number defaultValue;

            public NumberSchemaBuilder setTitle(String title) {
                this.title = title;
                return this;
            }

            public NumberSchemaBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public NumberSchemaBuilder setMaximum(Number maximum) {
                this.maximum = maximum;
                return this;
            }

            public NumberSchemaBuilder setMinimum(Number minimum) {
                this.minimum = minimum;
                return this;
            }

            public NumberSchemaBuilder setRequired(boolean required) {
                this.required = required;
                return this;
            }

            public NumberSchemaBuilder setDefaultValue(Number defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            public NumberSchema build() {
                return new NumberSchema(title, description, maximum, minimum, required, defaultValue);
            }

        }

    }

    record IntegerSchema(String title,
            String description,
            Integer maximum,
            Integer minimum,
            boolean required,
            Integer defaultValue)
            implements
                PrimitiveSchema {

        public static IntegerSchemaBuilder builder() {
            return new IntegerSchemaBuilder();
        }

        public IntegerSchema(String title, String description, Integer maximum, Integer minimum, boolean required) {
            this(title, description, maximum, minimum, required, null);
        }

        public IntegerSchema() {
            this(null, null, null, null, false);
        }

        public IntegerSchema(boolean required) {
            this(null, null, null, null, required);
        }

        public IntegerSchema(int defaultValue) {
            this(null, null, null, null, false, defaultValue);
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject()
                    .put("type", "integer");
            if (title != null) {
                ret.put("title", title);
            }
            if (description != null) {
                ret.put("description", description);
            }
            if (maximum != null) {
                ret.put("maximum", maximum);
            }
            if (minimum != null) {
                ret.put("minimum", minimum);
            }
            if (defaultValue != null) {
                ret.put("default", defaultValue);
            }
            return ret;
        }

        public static class IntegerSchemaBuilder {

            private String title;
            private String description;
            private Integer maximum;
            private Integer minimum;
            private boolean required;
            private Integer defaultValue;

            public IntegerSchemaBuilder setTitle(String title) {
                this.title = title;
                return this;
            }

            public IntegerSchemaBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public IntegerSchemaBuilder setMaximum(Integer maximum) {
                this.maximum = maximum;
                return this;
            }

            public IntegerSchemaBuilder setMinimum(Integer minimum) {
                this.minimum = minimum;
                return this;
            }

            public IntegerSchemaBuilder setRequired(boolean required) {
                this.required = required;
                return this;
            }

            public IntegerSchemaBuilder setDefaultValue(Integer defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            public IntegerSchema build() {
                return new IntegerSchema(title, description, maximum, minimum, required, defaultValue);
            }

        }

    }

    /**
     * {@code LegacyTitledEnumSchema}
     */
    record EnumSchema(String title,
            String description,
            List<String> enumValues,
            List<String> enumNames,
            boolean required,
            String defaultValue)
            implements
                PrimitiveSchema {

        public static EnumSchemaBuilder builder(List<String> enumValues) {
            return new EnumSchemaBuilder(enumValues);
        }

        public EnumSchema(String title, String description, List<String> enumValues, List<String> enumNames, boolean required) {
            this(title, description, enumValues, enumNames, required, null);
        }

        public EnumSchema(List<String> enumValues) {
            this(null, null, enumValues, null, false);
        }

        public EnumSchema(List<String> enumValues, boolean required) {
            this(null, null, enumValues, null, required);
        }

        public EnumSchema {
            if (enumValues == null) {
                throw new IllegalArgumentException("enumValues must not be null");
            }
            if (enumNames != null && enumNames.size() != enumValues.size()) {
                throw new IllegalArgumentException("enumNames and enumValues must contain the same number of elements");
            }
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject()
                    .put("type", "string")
                    .put("enum", enumValues);
            if (title != null) {
                ret.put("title", title);
            }
            if (description != null) {
                ret.put("description", description);
            }
            if (enumNames != null) {
                ret.put("enumNames", enumNames);
            }
            if (defaultValue != null) {
                ret.put("default", defaultValue);
            }
            return ret;
        }

        public static class EnumSchemaBuilder {

            private final List<String> enumValues;
            private String title;
            private String description;
            private List<String> enumNames;
            private boolean required;
            private String defaultValue;

            EnumSchemaBuilder(List<String> enumValues) {
                this.enumValues = enumValues;
            }

            public EnumSchemaBuilder setTitle(String title) {
                this.title = title;
                return this;
            }

            public EnumSchemaBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public EnumSchemaBuilder setEnumNames(List<String> enumNames) {
                this.enumNames = enumNames;
                return this;
            }

            public EnumSchemaBuilder setRequired(boolean required) {
                this.required = required;
                return this;
            }

            public EnumSchemaBuilder setDefaultValue(String defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            public EnumSchema build() {
                return new EnumSchema(title, description, enumValues, enumNames, required, defaultValue);
            }

        }

    }

    /**
     * {@code SingleSelectEnumSchema}
     */
    record SingleSelectEnumSchema(String title,
            String description,
            List<String> enumValues,
            List<String> enumTitles,
            boolean required,
            String defaultValue)
            implements
                PrimitiveSchema {

        public static SingleSelectEnumSchemaBuilder builder(List<String> enumValues) {
            return new SingleSelectEnumSchemaBuilder(enumValues);
        }

        public SingleSelectEnumSchema(List<String> enumValues) {
            this(null, null, enumValues, null, false, null);
        }

        public SingleSelectEnumSchema(List<String> enumValues, List<String> enumTitles) {
            this(null, null, enumValues, enumTitles, false, null);
        }

        public SingleSelectEnumSchema(List<String> enumValues, String defaultValue) {
            this(null, null, enumValues, null, false, defaultValue);
        }

        public SingleSelectEnumSchema {
            if (enumValues == null) {
                throw new IllegalArgumentException("enumValues must not be null");
            }
            if (enumTitles != null && enumTitles.size() != enumValues.size()) {
                throw new IllegalArgumentException("enumValues and enumTitles must contain the same number of elements");
            }
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject()
                    .put("type", "string");
            if (title != null) {
                ret.put("title", title);
            }
            if (description != null) {
                ret.put("description", description);
            }
            if (enumTitles == null) {
                // UntitledSingleSelectEnumSchema
                ret.put("enum", enumValues);
            } else {
                // TitledSingleSelectEnumSchema
                JsonArray oneOf = new JsonArray();
                for (int i = 0; i < enumValues.size(); i++) {
                    oneOf.add(new JsonObject()
                            .put("const", enumValues.get(i))
                            .put("title", enumTitles.get(i)));
                }
                ret.put("oneOf", oneOf);
            }
            if (defaultValue != null) {
                ret.put("default", defaultValue);
            }
            return ret;
        }

        public static class SingleSelectEnumSchemaBuilder {

            private final List<String> enumValues;
            private String title;
            private String description;
            private List<String> enumTitles;
            private boolean required;
            private String defaultValue;

            SingleSelectEnumSchemaBuilder(List<String> enumValues) {
                this.enumValues = enumValues;
            }

            public SingleSelectEnumSchemaBuilder setTitle(String title) {
                this.title = title;
                return this;
            }

            public SingleSelectEnumSchemaBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public SingleSelectEnumSchemaBuilder setEnumTitles(List<String> enumTitles) {
                this.enumTitles = enumTitles;
                return this;
            }

            public SingleSelectEnumSchemaBuilder setRequired(boolean required) {
                this.required = required;
                return this;
            }

            public SingleSelectEnumSchemaBuilder setDefaultValue(String defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            public SingleSelectEnumSchema build() {
                return new SingleSelectEnumSchema(title, description, enumValues, enumTitles, required, defaultValue);
            }

        }

    }

    /**
     * {@code MultiSelectEnumSchema}
     */
    record MultiSelectEnumSchema(String title,
            String description,
            List<String> enumValues,
            List<String> enumTitles,
            Integer minItems,
            Integer maxItems,
            boolean required,
            List<String> defaultValues)
            implements
                PrimitiveSchema {

        public static MultiSelectEnumSchemaBuilder builder(List<String> enumValues) {
            return new MultiSelectEnumSchemaBuilder(enumValues);
        }

        public MultiSelectEnumSchema(List<String> enumValues) {
            this(null, null, enumValues, null, null, null, false, null);
        }

        public MultiSelectEnumSchema {
            if (enumValues == null) {
                throw new IllegalArgumentException("enumValues must not be null");
            }
            if (enumTitles != null && enumTitles.size() != enumValues.size()) {
                throw new IllegalArgumentException("enumValues and enumTitles must contain the same number of elements");
            }
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject()
                    .put("type", "array");
            if (title != null) {
                ret.put("title", title);
            }
            if (description != null) {
                ret.put("description", description);
            }
            if (minItems != null) {
                ret.put("minItems", minItems);
            }
            if (maxItems != null) {
                ret.put("maxItems", maxItems);
            }
            if (enumTitles == null) {
                // UntitledMultiSelectEnumSchema
                ret.put("items", new JsonObject()
                        .put("type", "string")
                        .put("enum", enumValues));
            } else {
                // TitledMultiSelectEnumSchema
                JsonObject items = new JsonObject();
                JsonArray anyOf = new JsonArray();
                for (int i = 0; i < enumValues.size(); i++) {
                    anyOf.add(new JsonObject()
                            .put("const", enumValues.get(i))
                            .put("title", enumTitles.get(i)));
                }
                items.put("anyOf", anyOf);
                ret.put("items", items);
            }
            if (defaultValues != null) {
                ret.put("default", defaultValues);
            }
            return ret;
        }

        public static class MultiSelectEnumSchemaBuilder {

            private final List<String> enumValues;
            private String title;
            private String description;
            private List<String> enumTitles;
            private Integer minItems;
            private Integer maxItems;
            private boolean required;
            private List<String> defaultValues;

            MultiSelectEnumSchemaBuilder(List<String> enumValues) {
                this.enumValues = enumValues;
            }

            public MultiSelectEnumSchemaBuilder setTitle(String title) {
                this.title = title;
                return this;
            }

            public MultiSelectEnumSchemaBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public MultiSelectEnumSchemaBuilder setEnumTitles(List<String> enumTitles) {
                this.enumTitles = enumTitles;
                return this;
            }

            public MultiSelectEnumSchemaBuilder setMinItems(Integer minItems) {
                this.minItems = minItems;
                return this;
            }

            public MultiSelectEnumSchemaBuilder setMaxItems(Integer maxItems) {
                this.maxItems = maxItems;
                return this;
            }

            public MultiSelectEnumSchemaBuilder setRequired(boolean required) {
                this.required = required;
                return this;
            }

            public MultiSelectEnumSchemaBuilder setDefaultValues(List<String> defaultValues) {
                this.defaultValues = defaultValues;
                return this;
            }

            public MultiSelectEnumSchema build() {
                return new MultiSelectEnumSchema(title, description, enumValues, enumTitles, minItems, maxItems, required,
                        defaultValues);
            }

        }

    }

    record StringSchema(String title,
            String description,
            Integer maxLength,
            Integer minLength,
            Format format,
            boolean required,
            String defaultValue)
            implements
                PrimitiveSchema {

        public static StringSchemaBuilder builder() {
            return new StringSchemaBuilder();
        }

        public StringSchema(String title, String description, Integer maxLength, Integer minLength, Format format,
                boolean required) {
            this(title, description, maxLength, minLength, format, required, null);
        }

        public StringSchema() {
            this(null, null, null, null, null, false);
        }

        public StringSchema(boolean required) {
            this(null, null, null, null, null, required);
        }

        public StringSchema(String defaultValue) {
            this(null, null, null, null, null, false, defaultValue);
        }

        public enum Format {
            URI,
            EMAIL,
            DATE,
            DATE_TIME;

            String asJsonString() {
                return switch (this) {
                    case URI, EMAIL, DATE -> toString().toLowerCase();
                    case DATE_TIME -> "date-time";
                };
            }
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject()
                    .put("type", "string");
            if (title != null) {
                ret.put("title", title);
            }
            if (description != null) {
                ret.put("description", description);
            }
            if (maxLength != null) {
                ret.put("maxLength", maxLength);
            }
            if (minLength != null) {
                ret.put("minLength", minLength);
            }
            if (format != null) {
                ret.put("format", format.asJsonString());
            }
            if (defaultValue != null) {
                ret.put("default", defaultValue);
            }
            return ret;
        }

        public static class StringSchemaBuilder {

            private String title;
            private String description;
            private Integer maxLength;
            private Integer minLength;
            private Format format;
            private boolean required;
            private String defaultValue;

            public StringSchemaBuilder setTitle(String title) {
                this.title = title;
                return this;
            }

            public StringSchemaBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public StringSchemaBuilder setMaxLength(Integer maxLength) {
                this.maxLength = maxLength;
                return this;
            }

            public StringSchemaBuilder setMinLength(Integer minLength) {
                this.minLength = minLength;
                return this;
            }

            public StringSchemaBuilder setFormat(Format format) {
                this.format = format;
                return this;
            }

            public StringSchemaBuilder setRequired(boolean required) {
                this.required = required;
                return this;
            }

            public StringSchemaBuilder setDefaultValue(String defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }

            public StringSchema build() {
                return new StringSchema(title, description, maxLength, minLength, format, required, defaultValue);
            }

        }

    }
}
