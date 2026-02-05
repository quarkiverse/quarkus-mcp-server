package io.quarkiverse.mcp.server.runtime;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkiverse.mcp.server.McpMethod;
import io.vertx.core.json.JsonObject;

@Singleton
public class MicrometerMcpMetrics implements McpMetrics {

    private static final String MCP_SERVER_CONNECTIONS_ACTIVE = "mcp.server.connections.active";
    private static final String MCP_SERVER_REQUESTS_PREFIX = "mcp.server.requests.";
    private static final String MCP_SERVER = "mcp.server";
    private static final String TOOL_NAME = "tool.name";
    private static final String PROMPT_NAME = "prompt.name";
    private static final String RESOURCE_URI = "resource.uri";
    private static final String FAILURE = "failure";
    private static final String NONE = "none";

    private final Map<McpMethod, String> mcpMethodNames;

    @Inject
    MeterRegistry meterRegistry;

    MicrometerMcpMetrics() {
        // Note that we can't use a timer with the same name but a different set of tags
        // https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html#_limitation_on_same_name_with_different_set_of_tag_keys
        mcpMethodNames = new EnumMap<>(McpMethod.class);
        for (McpMethod m : McpMethod.values()) {
            mcpMethodNames.put(m, MCP_SERVER_REQUESTS_PREFIX + m.jsonRpcName().replace("/", "."));
        }
    }

    @Override
    public <T> void createMcpConnectionsGauge(T stateObject, ToDoubleFunction<T> valueFunction) {
        meterRegistry.gauge(MCP_SERVER_CONNECTIONS_ACTIVE, stateObject, valueFunction);
    }

    @Override
    public void mcpRequestCompleted(McpMethod method, JsonObject message, McpRequest mcpRequest, long duration,
            Throwable failure) {
        Timer.builder(mcpMethodNames.get(method))
                .tags(MCP_SERVER, mcpRequest.serverName(), FAILURE, failure(failure))
                .tags(createTags(method, message, mcpRequest))
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);
    }

    private Tags createTags(McpMethod method, JsonObject message, McpRequest mcpRequest) {
        return switch (method) {
            case TOOLS_CALL -> Tags.of(
                    TOOL_NAME, Messages.getParams(message).getString("name"));
            case RESOURCES_READ -> Tags.of(
                    RESOURCE_URI, Messages.getParams(message).getString("uri"));
            case PROMPTS_GET -> Tags.of(
                    PROMPT_NAME, Messages.getParams(message).getString("name"));
            default -> Tags.empty();
        };
    }

    static String failure(Throwable throwable) {
        if (throwable == null) {
            return NONE;
        }
        if (throwable.getCause() == null) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getCause().getClass().getSimpleName();
    }

}
