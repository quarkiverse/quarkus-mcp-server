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

    public Future<List<PromptMessage>> get(String name, Map<String, Object> args) {
        PromptMetadata prompt = prompts.get(name);
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt not found: " + name);
        }
        Invoker<Object, Object> invoker = prompt.invoker();
        Object[] arguments = prepareArguments(prompt.info(), args);
        return doExecute(prompt.executionModel(), new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                return invoker.invoke(null, arguments);
            }
        }).map(this::mapResult);
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
    private Object[] prepareArguments(PromptMethodInfo info, Map<String, Object> args) {
        Object[] ret = new Object[info.arguments().size()];
        int idx = 0;
        for (PromptArgument arg : info.arguments()) {
            Object val = args.get(arg.name());
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
            idx++;
        }
        return ret;
    }
}
