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

    public record Result<R>(R value, String serverName) {
    }

    public static class PromptOfMessage implements Function<Result<PromptMessage>, Uni<PromptResponse>> {

        public static final PromptOfMessage INSTANCE = new PromptOfMessage();

        @Override
        public Uni<PromptResponse> apply(Result<PromptMessage> r) {
            return Uni.createFrom()
                    .item(new PromptResponse(null, List.of(r.value())));
        }

    }

    public static class PromptListOfMessage implements Function<Result<List<PromptMessage>>, Uni<PromptResponse>> {

        public static final PromptListOfMessage INSTANCE = new PromptListOfMessage();

        @Override
        public Uni<PromptResponse> apply(Result<List<PromptMessage>> r) {
            return Uni.createFrom().item(PromptResponse.withMessages(r.value()));
        }

    }

    public static class PromptUniOfMessage implements Function<Result<Uni<PromptMessage>>, Uni<PromptResponse>> {

        public static final PromptUniOfMessage INSTANCE = new PromptUniOfMessage();

        @Override
        public Uni<PromptResponse> apply(Result<Uni<PromptMessage>> r) {
            return r.value().map(m -> new PromptResponse(null, List.of(m)));
        }

    }

    public static class PromptUniListOfMessage implements Function<Result<Uni<List<PromptMessage>>>, Uni<PromptResponse>> {

        public static final PromptUniListOfMessage INSTANCE = new PromptUniListOfMessage();

        @Override
        public Uni<PromptResponse> apply(Result<Uni<List<PromptMessage>>> r) {
            return r.value().map(PromptResponse::withMessages);
        }

    }

    public static class Identity implements Function<Result<Object>, Uni<Object>> {

        public static final Identity INSTANCE = new Identity();

        @SuppressWarnings("unchecked")
        @Override
        public Uni<Object> apply(Result<Object> r) {
            return (Uni<Object>) r.value();
        }

    }

    public static class ToUni implements Function<Result<Object>, Uni<Object>> {

        public static final ToUni INSTANCE = new ToUni();

        @Override
        public Uni<Object> apply(Result<Object> r) {
            return Uni.createFrom().item(r.value());
        }

    }

    public static class ToolContent implements Function<Result<Content>, Uni<ToolResponse>> {

        public static final ToolContent INSTANCE = new ToolContent();

        @Override
        public Uni<ToolResponse> apply(Result<Content> r) {
            return Uni.createFrom().item(ToolResponse.success(r.value()));
        }

    }

    public static class ToolString implements Function<Result<String>, Uni<ToolResponse>> {

        public static final ToolString INSTANCE = new ToolString();

        @Override
        public Uni<ToolResponse> apply(Result<String> r) {
            return Uni.createFrom().item(ToolResponse.success(new TextContent(r.value())));
        }

    }

    public static class ToolListContent implements Function<Result<List<Content>>, Uni<ToolResponse>> {

        public static final ToolListContent INSTANCE = new ToolListContent();

        @Override
        public Uni<ToolResponse> apply(Result<List<Content>> r) {
            return Uni.createFrom().item(ToolResponse.success(r.value()));
        }

    }

    public static class ToolListString implements Function<Result<List<String>>, Uni<ToolResponse>> {

        public static final ToolListString INSTANCE = new ToolListString();

        @Override
        public Uni<ToolResponse> apply(Result<List<String>> r) {
            return Uni.createFrom().item(ToolResponse.success(r.value().stream().map(TextContent::new).toList()));
        }

    }

    public static class ToolUniContent implements Function<Result<Uni<Content>>, Uni<ToolResponse>> {

        public static final ToolUniContent INSTANCE = new ToolUniContent();

        @Override
        public Uni<ToolResponse> apply(Result<Uni<Content>> r) {
            return r.value().map(ToolResponse::success);
        }

    }

    public static class ToolUniString implements Function<Result<Uni<String>>, Uni<ToolResponse>> {

        public static final ToolUniString INSTANCE = new ToolUniString();

        @Override
        public Uni<ToolResponse> apply(Result<Uni<String>> r) {
            return r.value().map(str -> ToolResponse.success(new TextContent(str)));
        }

    }

    public static class ToolUniListContent implements Function<Result<Uni<List<Content>>>, Uni<ToolResponse>> {

        public static final ToolUniListContent INSTANCE = new ToolUniListContent();

        @Override
        public Uni<ToolResponse> apply(Result<Uni<List<Content>>> r) {
            return r.value().map(ToolResponse::success);
        }

    }

    public static class ToolUniListString implements Function<Result<Uni<List<String>>>, Uni<ToolResponse>> {

        public static final ToolUniListString INSTANCE = new ToolUniListString();

        @Override
        public Uni<ToolResponse> apply(Result<Uni<List<String>>> r) {
            return r.value().map(l -> ToolResponse.success(l.stream().map(TextContent::new).toList()));
        }

    }

    public static class ResourceContent implements Function<Result<ResourceContents>, Uni<ResourceResponse>> {

        public static final ResourceContent INSTANCE = new ResourceContent();

        @Override
        public Uni<ResourceResponse> apply(Result<ResourceContents> r) {
            return Uni.createFrom().item(new ResourceResponse(List.of(r.value())));
        }

    }

    public static class ResourceListContent implements Function<Result<List<ResourceContents>>, Uni<ResourceResponse>> {

        public static final ResourceListContent INSTANCE = new ResourceListContent();

        @Override
        public Uni<ResourceResponse> apply(Result<List<ResourceContents>> r) {
            return Uni.createFrom().item(new ResourceResponse(r.value()));
        }

    }

    public static class ResourceUniContent implements Function<Result<Uni<ResourceContents>>, Uni<ResourceResponse>> {

        public static final ResourceUniContent INSTANCE = new ResourceUniContent();

        @Override
        public Uni<ResourceResponse> apply(Result<Uni<ResourceContents>> r) {
            return r.value().map(c -> new ResourceResponse(List.of(c)));
        }

    }

    public static class ResourceUniListContent implements Function<Result<Uni<List<ResourceContents>>>, Uni<ResourceResponse>> {

        public static final ResourceUniListContent INSTANCE = new ResourceUniListContent();

        @Override
        public Uni<ResourceResponse> apply(Result<Uni<List<ResourceContents>>> r) {
            return r.value().map(ResourceResponse::new);
        }

    }

    public static class CompleteString implements Function<Result<String>, Uni<CompletionResponse>> {

        public static final CompleteString INSTANCE = new CompleteString();

        @Override
        public Uni<CompletionResponse> apply(Result<String> r) {
            return Uni.createFrom().item(new CompletionResponse(List.of(r.value()), null, null));
        }

    }

    public static class CompleteListString implements Function<Result<List<String>>, Uni<CompletionResponse>> {

        public static final CompleteListString INSTANCE = new CompleteListString();

        @Override
        public Uni<CompletionResponse> apply(Result<List<String>> r) {
            return Uni.createFrom().item(new CompletionResponse(r.value(), null, null));
        }

    }

    public static class CompleteUniString implements Function<Result<Uni<String>>, Uni<CompletionResponse>> {

        public static final CompleteUniString INSTANCE = new CompleteUniString();

        @Override
        public Uni<CompletionResponse> apply(Result<Uni<String>> r) {
            return r.value().map(str -> new CompletionResponse(List.of(str), null, null));
        }

    }

    public static class CompleteUniListString implements Function<Result<Uni<List<String>>>, Uni<CompletionResponse>> {

        public static final CompleteUniListString INSTANCE = new CompleteUniListString();

        @Override
        public Uni<CompletionResponse> apply(Result<Uni<List<String>>> r) {
            return r.value().map(list -> new CompletionResponse(list, null, null));
        }

    }

}
