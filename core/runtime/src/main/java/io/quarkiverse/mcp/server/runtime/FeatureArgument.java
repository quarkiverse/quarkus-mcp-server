package io.quarkiverse.mcp.server.runtime;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
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
        JsonType expectedJsonType,
        OptionalKind optionalKind) {

    // this constructor is used for McpMetadata
    public FeatureArgument(String name,
            String title,
            String description,
            boolean required,
            java.lang.reflect.Type type,
            String defaultValue,
            Provider provider) {
        this(name, title, description, required, type, defaultValue, provider, null, OptionalKind.NONE);
    }

    public FeatureArgument {
        if (Types.isOptional(type)) {
            optionalKind = OptionalKind.of(type);
            type = Types.getOptionalValueType(type);
        }
        if (expectedJsonType == null) {
            expectedJsonType = expectedJsonTypeFrom(type);
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

    public boolean isValid(Object value) {
        JsonType jsonType = expectedJsonType();
        if (jsonType == null) {
            jsonType = expectedJsonTypeFrom(type);
        }
        return switch (jsonType) {
            case BOOLEAN -> value instanceof Boolean;
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case ARRAY -> value instanceof List;
            case OBJECT -> value instanceof Map;
            default -> throw new IllegalArgumentException("Unexpected jsonType: " + jsonType);
        };
    }

    public static JsonType expectedJsonTypeFrom(java.lang.reflect.Type type) {
        if (Boolean.class.equals(type)
                || boolean.class.equals(type)) {
            return JsonType.BOOLEAN;
        } else if (String.class.equals(type)
                || (type instanceof Class<?> clazz && clazz.isEnum())) {
            return JsonType.STRING;
        } else if (int.class.equals(type)
                || long.class.equals(type)
                || short.class.equals(type)
                || byte.class.equals(type)
                || float.class.equals(type)
                || double.class.equals(type)
                || Integer.class.equals(type)
                || Long.class.equals(type)
                || Short.class.equals(type)
                || Byte.class.equals(type)
                || Float.class.equals(type)
                || Double.class.equals(type)
                || Number.class.equals(type)) {
            return JsonType.NUMBER;
        } else if (type instanceof ParameterizedType pt && pt.getRawType().equals(List.class)
                || type instanceof Class<?> clazz && (clazz.isArray())) {
            return JsonType.ARRAY;
        } else {
            return JsonType.OBJECT;
        }
    }

    public enum JsonType {
        BOOLEAN,
        STRING,
        NUMBER,
        OBJECT,
        ARRAY
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