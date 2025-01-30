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

    public static class PromptOfMessage implements Function<PromptMessage, Uni<PromptResponse>> {

        public static final PromptOfMessage INSTANCE = new PromptOfMessage();

        @Override
        public Uni<PromptResponse> apply(PromptMessage message) {
            return Uni.createFrom()
                    .item(new PromptResponse(null, List.of(message)));
        }

    }

    public static class PromptListOfMessage implements Function<List<PromptMessage>, Uni<PromptResponse>> {

        public static final PromptListOfMessage INSTANCE = new PromptListOfMessage();

        @Override
        public Uni<PromptResponse> apply(List<PromptMessage> list) {
            return Uni.createFrom().item(PromptResponse.withMessages(list));
        }

    }

    public static class PromptUniOfMessage implements Function<Uni<PromptMessage>, Uni<PromptResponse>> {

        public static final PromptUniOfMessage INSTANCE = new PromptUniOfMessage();

        @Override
        public Uni<PromptResponse> apply(Uni<PromptMessage> uni) {
            return uni.map(m -> new PromptResponse(null, List.of(m)));
        }

    }

    public static class PromptUniListOfMessage implements Function<Uni<List<PromptMessage>>, Uni<PromptResponse>> {

        public static final PromptUniListOfMessage INSTANCE = new PromptUniListOfMessage();

        @Override
        public Uni<PromptResponse> apply(Uni<List<PromptMessage>> uni) {
            return uni.map(PromptResponse::withMessages);
        }

    }

    public static class Identity implements Function<Object, Uni<Object>> {

        public static final Identity INSTANCE = new Identity();

        @SuppressWarnings("unchecked")
        @Override
        public Uni<Object> apply(Object o) {
            return (Uni<Object>) o;
        }

    }

    public static class ToUni implements Function<Object, Uni<Object>> {

        public static final ToUni INSTANCE = new ToUni();

        @Override
        public Uni<Object> apply(Object o) {
            return Uni.createFrom().item(o);
        }

    }

    public static class ToolContent implements Function<Content, Uni<ToolResponse>> {

        public static final ToolContent INSTANCE = new ToolContent();

        @Override
        public Uni<ToolResponse> apply(Content content) {
            return Uni.createFrom().item(ToolResponse.success(content));
        }

    }

    public static class ToolString implements Function<String, Uni<ToolResponse>> {

        public static final ToolString INSTANCE = new ToolString();

        @Override
        public Uni<ToolResponse> apply(String str) {
            return Uni.createFrom().item(ToolResponse.success(new TextContent(str)));
        }

    }

    public static class ToolListContent implements Function<List<Content>, Uni<ToolResponse>> {

        public static final ToolListContent INSTANCE = new ToolListContent();

        @Override
        public Uni<ToolResponse> apply(List<Content> list) {
            return Uni.createFrom().item(ToolResponse.success(list));
        }

    }

    public static class ToolListString implements Function<List<String>, Uni<ToolResponse>> {

        public static final ToolListString INSTANCE = new ToolListString();

        @Override
        public Uni<ToolResponse> apply(List<String> list) {
            return Uni.createFrom().item(ToolResponse.success(list.stream().map(TextContent::new).toList()));
        }

    }

    public static class ToolUniContent implements Function<Uni<Content>, Uni<ToolResponse>> {

        public static final ToolUniContent INSTANCE = new ToolUniContent();

        @Override
        public Uni<ToolResponse> apply(Uni<Content> uni) {
            return uni.map(ToolResponse::success);
        }

    }

    public static class ToolUniString implements Function<Uni<String>, Uni<ToolResponse>> {

        public static final ToolUniString INSTANCE = new ToolUniString();

        @Override
        public Uni<ToolResponse> apply(Uni<String> uni) {
            return uni.map(str -> ToolResponse.success(new TextContent(str)));
        }

    }

    public static class ToolUniListContent implements Function<Uni<List<Content>>, Uni<ToolResponse>> {

        public static final ToolUniListContent INSTANCE = new ToolUniListContent();

        @Override
        public Uni<ToolResponse> apply(Uni<List<Content>> uni) {
            return uni.map(ToolResponse::success);
        }

    }

    public static class ToolUniListString implements Function<Uni<List<String>>, Uni<ToolResponse>> {

        public static final ToolUniListString INSTANCE = new ToolUniListString();

        @Override
        public Uni<ToolResponse> apply(Uni<List<String>> uni) {
            return uni.map(l -> ToolResponse.success(l.stream().map(TextContent::new).toList()));
        }

    }

    public static class ResourceContent implements Function<ResourceContents, Uni<ResourceResponse>> {

        public static final ResourceContent INSTANCE = new ResourceContent();

        @Override
        public Uni<ResourceResponse> apply(ResourceContents contents) {
            return Uni.createFrom().item(new ResourceResponse(List.of(contents)));
        }

    }

    public static class ResourceListContent implements Function<List<ResourceContents>, Uni<ResourceResponse>> {

        public static final ResourceListContent INSTANCE = new ResourceListContent();

        @Override
        public Uni<ResourceResponse> apply(List<ResourceContents> list) {
            return Uni.createFrom().item(new ResourceResponse(list));
        }

    }

    public static class ResourceUniContent implements Function<Uni<ResourceContents>, Uni<ResourceResponse>> {

        public static final ResourceUniContent INSTANCE = new ResourceUniContent();

        @Override
        public Uni<ResourceResponse> apply(Uni<ResourceContents> uni) {
            return uni.map(c -> new ResourceResponse(List.of(c)));
        }

    }

    public static class ResourceUniListContent implements Function<Uni<List<ResourceContents>>, Uni<ResourceResponse>> {

        public static final ResourceUniListContent INSTANCE = new ResourceUniListContent();

        @Override
        public Uni<ResourceResponse> apply(Uni<List<ResourceContents>> uni) {
            return uni.map(ResourceResponse::new);
        }

    }

    public static class CompleteString implements Function<String, Uni<CompletionResponse>> {

        public static final CompleteString INSTANCE = new CompleteString();

        @Override
        public Uni<CompletionResponse> apply(String str) {
            return Uni.createFrom().item(new CompletionResponse(List.of(str), null, null));
        }

    }

    public static class CompleteListString implements Function<List<String>, Uni<CompletionResponse>> {

        public static final CompleteListString INSTANCE = new CompleteListString();

        @Override
        public Uni<CompletionResponse> apply(List<String> list) {
            return Uni.createFrom().item(new CompletionResponse(list, null, null));
        }

    }

    public static class CompleteUniString implements Function<Uni<String>, Uni<CompletionResponse>> {

        public static final CompleteUniString INSTANCE = new CompleteUniString();

        @Override
        public Uni<CompletionResponse> apply(Uni<String> uni) {
            return uni.map(str -> new CompletionResponse(List.of(str), null, null));
        }

    }

    public static class CompleteUniListString implements Function<Uni<List<String>>, Uni<CompletionResponse>> {

        public static final CompleteUniListString INSTANCE = new CompleteUniListString();

        @Override
        public Uni<CompletionResponse> apply(Uni<List<String>> uni) {
            return uni.map(list -> new CompletionResponse(list, null, null));
        }

    }

}
