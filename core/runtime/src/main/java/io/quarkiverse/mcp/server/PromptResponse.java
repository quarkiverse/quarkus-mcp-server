package io.quarkiverse.mcp.server;

import java.util.List;

public record PromptResponse(String description, List<PromptMessage> messages) {

    public static PromptResponse withMessages(List<PromptMessage> messages) {
        return new PromptResponse(null, messages);
    }

}
