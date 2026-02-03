package io.quarkiverse.mcp.server;

public enum McpMethod {

    INITIALIZE("initialize"),

    PROMPTS_LIST("prompts/list"),
    PROMPTS_GET("prompts/get"),
    TOOLS_LIST("tools/list"),
    TOOLS_CALL("tools/call"),
    RESOURCES_LIST("resources/list"),
    RESOURCES_READ("resources/read"),
    RESOURCES_SUBSCRIBE("resources/subscribe"),
    RESOURCES_UNSUBSCRIBE("resources/unsubscribe"),
    RESOURCE_TEMPLATES_LIST("resources/templates/list"),

    PING("ping"),
    COMPLETION_COMPLETE("completion/complete"),
    LOGGING_SET_LEVEL("logging/setLevel"),

    NOTIFICATIONS_ROOTS_LIST_CHANGED("notifications/roots/list_changed"),
    NOTIFICATIONS_CANCELLED("notifications/cancelled"),
    NOTIFICATIONS_INITIALIZED("notifications/initialized"),
    NOTIFICATIONS_MESSAGE("notifications/message"),
    NOTIFICATIONS_PROGRESS("notifications/progress"),
    NOTIFICATIONS_TOOLS_LIST_CHANGED("notifications/tools/list_changed"),
    NOTIFICATIONS_RESOURCES_LIST_CHANGED("notifications/resources/list_changed"),
    NOTIFICATIONS_PROMPTS_LIST_CHANGED("notifications/prompts/list_changed"),

    ROOTS_LIST("roots/list"),
    SAMPLING_CREATE_MESSAGE("sampling/createMessage"),
    ELICITATION_CREATE("elicitation/create"),

    // non-standard methods
    Q_CLOSE("q/close"),
    ;

    private final String name;

    McpMethod(String value) {
        this.name = value;
    }

    public String jsonRpcName() {
        return name;
    }

    public static McpMethod from(String method) {
        if (method != null && !method.isEmpty()) {
            for (McpMethod m : values()) {
                if (m.name.equals(method)) {
                    return m;
                }
            }
        }
        return null;
    }
}
