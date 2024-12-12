package io.quarkiverse.mcp.server.runtime;

import java.util.List;
import java.util.function.Function;

import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.smallrye.mutiny.Uni;

public class ResultMappers {

    public static final Function<PromptMessage, Uni<PromptResponse>> PROMPT_SINGLE_MESSAGE = message -> Uni.createFrom()
            .item(new PromptResponse(null, List.of(message)));

    public static final Function<List<PromptMessage>, Uni<PromptResponse>> PROMPT_LIST_MESSAGE = messages -> Uni.createFrom()
            .item(PromptResponse.withMessages(messages));

    public static final Function<Uni<PromptMessage>, Uni<PromptResponse>> PROMPT_UNI_SINGLE_MESSAGE = uni -> {
        return uni.map(m -> new PromptResponse(null, List.of(m)));
    };

    public static final Function<Uni<List<PromptMessage>>, Uni<PromptResponse>> PROMPT_UNI_LIST_MESSAGE = uni -> uni
            .map(messages -> PromptResponse.withMessages(messages));

    @SuppressWarnings("unchecked")
    public static final Function<Object, Uni<Object>> IDENTITY = o -> (Uni<Object>) o;

    public static final Function<Object, Uni<Object>> TO_UNI = o -> Uni.createFrom().item(o);

}
