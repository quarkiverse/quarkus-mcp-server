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
                ret.put("description", title);
            }
            if (defaultValue != null) {
                ret.put("default", defaultValue);
            }
            return ret;
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

        public NumberSchema(String title, String description, Number maximum, Number minimum, boolean required) {
            this(title, description, maximum, minimum, required, null);
        }

        public NumberSchema() {
            this(null, null, null, null, false);
        }

        public NumberSchema(boolean required) {
            this(null, null, null, null, required);
        }

        @Override
        public JsonObject asJson() {
            JsonObject ret = new JsonObject()
                    .put("type", "number");
            if (title != null) {
                ret.put("title", title);
            }
            if (description != null) {
                ret.put("description", title);
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

        enum Format {
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
                ret.put("description", title);
            }
            if (maxLength != null) {
                ret.put("maxLength", maxLength);
            }
            if (minLength != null) {
                ret.put("minLength", maxLength);
            }
            if (format != null) {
                ret.put("format", format.asJsonString());
            }
            if (defaultValue != null) {
                ret.put("default", defaultValue);
            }
            return ret;
        }

    }
}
