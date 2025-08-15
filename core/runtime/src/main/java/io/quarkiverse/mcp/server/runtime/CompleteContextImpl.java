package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.quarkiverse.mcp.server.CompleteContext;
import io.vertx.core.json.JsonObject;

class CompleteContextImpl implements CompleteContext {

    static CompleteContextImpl from(ArgumentProviders argumentProviders) {
        JsonObject message = argumentProviders.rawMessage();
        Map<String, String> arguments = Map.of();
        JsonObject params = message.getJsonObject("params");
        if (params != null) {
            JsonObject context = params.getJsonObject("context");
            if (context != null) {
                arguments = context.getJsonObject("arguments")
                        .getMap()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toString()));
            }
        }
        return new CompleteContextImpl(arguments);
    }

    private final Map<String, String> arguments;

    private CompleteContextImpl(Map<String, String> arguments) {
        this.arguments = arguments;
    }

    @Override
    public Map<String, String> arguments() {
        return arguments;
    }

}
