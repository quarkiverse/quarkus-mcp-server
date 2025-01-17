package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.function.Function;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;

public class ResultMappers {

    public static final Function<PromptMessage, Uni<PromptResponse>> PROMPT_MESSAGE = message -> Uni.createFrom()
            .item(new PromptResponse(null, List.of(message)));

    public static final Function<List<PromptMessage>, Uni<PromptResponse>> PROMPT_LIST_MESSAGE = messages -> Uni.createFrom()
            .item(PromptResponse.withMessages(messages));

    public static final Function<Uni<PromptMessage>, Uni<PromptResponse>> PROMPT_UNI_MESSAGE = uni -> {
        return uni.map(m -> new PromptResponse(null, List.of(m)));
    };

    public static final Function<Uni<List<PromptMessage>>, Uni<PromptResponse>> PROMPT_UNI_LIST_MESSAGE = uni -> uni
            .map(messages -> PromptResponse.withMessages(messages));

    @SuppressWarnings("unchecked")
    public static final Function<Object, Uni<Object>> IDENTITY = o -> (Uni<Object>) o;

    public static final Function<Object, Uni<Object>> TO_UNI = o -> Uni.createFrom().item(o);

    public static final Function<Content, Uni<Object>> TOOL_CONTENT = content -> Uni.createFrom()
            .item(ToolResponse.success(content));

    public static final Function<String, Uni<ToolResponse>> TOOL_STRING = str -> Uni.createFrom()
            .item(ToolResponse.success(new TextContent(str)));

    public static final Function<List<Content>, Uni<ToolResponse>> TOOL_LIST_CONTENT = list -> Uni.createFrom()
            .item(ToolResponse.success(list));

    public static final Function<List<String>, Uni<ToolResponse>> TOOL_LIST_STRING = list -> Uni.createFrom()
            .item(ToolResponse.success(list.stream().map(TextContent::new).toList()));

    public static final Function<Uni<Content>, Uni<ToolResponse>> TOOL_UNI_CONTENT = uni -> uni
            .map(c -> ToolResponse.success(c));

    public static final Function<Uni<String>, Uni<ToolResponse>> TOOL_UNI_STRING = uni -> uni
            .map(str -> ToolResponse.success(new TextContent(str)));

    public static final Function<Uni<List<Content>>, Uni<ToolResponse>> TOOL_UNI_LIST_CONTENT = uni -> uni
            .map(l -> ToolResponse.success(l));

    public static final Function<Uni<List<String>>, Uni<ToolResponse>> TOOL_UNI_LIST_STRING = uni -> uni
            .map(l -> ToolResponse.success(l.stream().map(TextContent::new).toList()));

    public static final Function<ResourceContents, Uni<ResourceResponse>> RESOURCE_CONTENT = content -> Uni.createFrom()
            .item(new ResourceResponse(List.of(content)));

    public static final Function<List<ResourceContents>, Uni<ResourceResponse>> RESOURCE_LIST_CONTENT = list -> Uni.createFrom()
            .item(new ResourceResponse(list));

    public static final Function<Uni<ResourceContents>, Uni<ResourceResponse>> RESOURCE_UNI_CONTENT = uni -> uni
            .map(c -> new ResourceResponse(List.of(c)));

    public static final Function<Uni<List<ResourceContents>>, Uni<ResourceResponse>> RESOURCE_UNI_LIST_CONTENT = uni -> uni
            .map(l -> new ResourceResponse(l));

    public static final Function<String, Uni<CompletionResponse>> COMPLETE_STRING = str -> Uni.createFrom()
            .item(new CompletionResponse(List.of(str), null, null));

    public static final Function<List<String>, Uni<CompletionResponse>> COMPLETE_LIST_STRING = list -> Uni.createFrom()
            .item(new CompletionResponse(list, null, null));

    public static final Function<Uni<String>, Uni<CompletionResponse>> COMPLETE_UNI_STRING = uni -> uni
            .map(str -> new CompletionResponse(List.of(str), null, null));

    public static final Function<Uni<List<String>>, Uni<CompletionResponse>> COMPLETE_UNI_LIST_STRING = uni -> uni
            .map(list -> new CompletionResponse(list, null, null));

}
