package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.invoke.Invoker;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.runtime.PromptArgument.Provider;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Singleton
public class PromptManager extends ComponentManager {

    final ObjectMapper mapper;

    final Map<String, PromptMetadata> prompts;

    PromptManager(McpMetadata metadata, ObjectMapper mapper) {
        this.prompts = metadata.prompts().stream().collect(Collectors.toMap(m -> m.info().name(), Function.identity()));
        this.mapper = mapper;
    }

    public List<PromptMethodInfo> list() {
        return prompts.values().stream().map(PromptMetadata::info).toList();
    }

    public Future<List<PromptMessage>> get(String name, ArgumentProviders argProviders) {
        PromptMetadata prompt = prompts.get(name);
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt not found: " + name);
        }
        Invoker<Object, Object> invoker = prompt.invoker();
        Object[] arguments = prepareArguments(prompt.info(), argProviders);
        return execute(prompt.executionModel(), new Callable<Uni<List<PromptMessage>>>() {
            @Override
            public Uni<List<PromptMessage>> call() throws Exception {
                return prompt.resultMapper().apply(invoker.invoke(null, arguments));
            }
        });
    }

    @SuppressWarnings("unchecked")
    List<PromptMessage> mapResult(Object result) {
        if (result instanceof PromptMessage msg) {
            return List.of(msg);
        } else if (result instanceof List list) {
            return list;
        }
        throw new IllegalStateException("Unsupported return type: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    private Object[] prepareArguments(PromptMethodInfo info, ArgumentProviders argProviders) {
        Object[] ret = new Object[info.arguments().size()];
        int idx = 0;
        for (PromptArgument arg : info.arguments()) {
            if (arg.provider() == Provider.MCP_CONNECTION) {
                ret[idx] = argProviders.connection();
            } else if (arg.provider() == Provider.REQUEST_ID) {
                ret[idx] = new RequestId(argProviders.requestId());
            } else {
                Object val = argProviders.args().get(arg.name());
                if (val == null && arg.required()) {
                    throw new IllegalStateException("Missing required argument: " + arg.name());
                }
                if (val instanceof Map map) {
                    // json object
                    JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                    try {
                        ret[idx] = mapper.readValue(new JsonObject(map).encode(), javaType);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (val instanceof List list) {
                    // json array
                    JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                    try {
                        ret[idx] = mapper.readValue(new JsonArray(list).encode(), javaType);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                } else {
                    ret[idx] = val;
                }
            }
            idx++;
        }
        return ret;
    }
}
