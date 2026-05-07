package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import io.vertx.core.json.JsonObject;

public record FeatureArgument(String name,
        String title,
        String description,
        boolean required,
        java.lang.reflect.Type type,
        String defaultValue,
        Provider provider,
        OptionalKind optionalKind) {

    // this constructor is used for McpMetadata
    public FeatureArgument(String name,
            String title,
            String description,
            boolean required,
            java.lang.reflect.Type type,
            String defaultValue,
            Provider provider) {
        this(name, title, description, required, type, defaultValue, provider, OptionalKind.NONE);
    }

    public FeatureArgument {
        if (Types.isOptional(type)) {
            optionalKind = OptionalKind.of(type);
            type = Types.getOptionalValueType(type);
        }
    }

    public boolean isOptional() {
        return optionalKind != OptionalKind.NONE;
    }

    public JsonObject asJson() {
        JsonObject ret = new JsonObject().put("name", name)
                .put("description", description)
                .put("required", required);
        if (title != null) {
            ret.put("title", title);
        }
        return ret;
    }

    public boolean isParam() {
        return provider == Provider.PARAMS;
    }

    public enum Provider {
        PARAMS,
        REQUEST_ID,
        REQUEST_URI,
        MCP_CONNECTION,
        MCP_LOG,
        PROGRESS,
        ROOTS,
        SAMPLING,
        CANCELLATION,
        RAW_MESSAGE,
        COMPLETE_CONTEXT,
        META,
        ELICITATION;

        public boolean isValidFor(Feature feature) {
            return switch (this) {
                case REQUEST_ID -> feature != Feature.NOTIFICATION;
                case REQUEST_URI -> feature == Feature.RESOURCE || feature == Feature.RESOURCE_TEMPLATE;
                case COMPLETE_CONTEXT -> feature == Feature.PROMPT_COMPLETE || feature == Feature.RESOURCE_TEMPLATE_COMPLETE;
                default -> true;
            };
        }
    }

    public enum OptionalKind {
        NONE,
        GENERIC,
        INT,
        LONG,
        DOUBLE;

        static OptionalKind of(java.lang.reflect.Type type) {
            if (type instanceof ParameterizedType pt && pt.getRawType().equals(Optional.class)) {
                return GENERIC;
            }
            if (OptionalInt.class.equals(type)) {
                return INT;
            }
            if (OptionalLong.class.equals(type)) {
                return LONG;
            }
            if (OptionalDouble.class.equals(type)) {
                return DOUBLE;
            }
            return NONE;
        }
    }
}
