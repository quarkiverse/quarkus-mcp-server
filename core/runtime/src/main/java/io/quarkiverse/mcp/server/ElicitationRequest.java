package io.quarkiverse.mcp.server;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
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

    record BooleanSchema(String title, String description, Boolean defaultValue, boolean required)
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

    record NumberSchema(String title, String description, Number maximum, Number minimum, boolean required)
            implements
                PrimitiveSchema {

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
            return ret;
        }

    }

    record EnumSchema(String title, String description, List<String> enumValues, List<String> enumNames, boolean required)
            implements
                PrimitiveSchema {

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
                ret.put("description", title);
            }
            if (enumNames != null) {
                ret.put("enumNames", enumNames);
            }
            return ret;
        }

    }

    record StringSchema(String title, String description, Integer maxLength, Integer minLength, Format format, boolean required)
            implements
                PrimitiveSchema {

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
            return ret;
        }

    }
}
