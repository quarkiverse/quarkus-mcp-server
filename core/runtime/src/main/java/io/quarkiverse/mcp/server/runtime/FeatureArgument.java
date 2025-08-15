package io.quarkiverse.mcp.server.runtime;

import io.vertx.core.json.JsonObject;

public record FeatureArgument(String name,
        String title,
        String description,
        boolean required,
        java.lang.reflect.Type type,
        String defaultValue,
        Provider provider) {

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
        META;

        public boolean isValidFor(Feature feature) {
            return switch (this) {
                case REQUEST_ID -> feature != Feature.NOTIFICATION;
                case REQUEST_URI -> feature == Feature.RESOURCE || feature == Feature.RESOURCE_TEMPLATE;
                case COMPLETE_CONTEXT -> feature == Feature.PROMPT_COMPLETE || feature == Feature.RESOURCE_TEMPLATE_COMPLETE;
                default -> true;
            };
        }
    }
}