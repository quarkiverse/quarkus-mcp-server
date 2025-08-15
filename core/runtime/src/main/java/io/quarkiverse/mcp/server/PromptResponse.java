package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Response to a {@code prompts/get} request from the client.
 *
 * @param description an optional description for the prompt
 * @param messages the prompt messages (must not be {@code null})
 * @param _meta the optional metadata
 */
@JsonInclude(Include.NON_NULL)
public record PromptResponse(String description, List<PromptMessage> messages, Map<MetaKey, Object> _meta) {

    /**
     * @param messages
     * @return a new response
     */
    public static PromptResponse withMessages(PromptMessage... messages) {
        return new PromptResponse(null, List.of(messages));
    }

    /**
     * @param messages
     * @return a new response
     */
    public static PromptResponse withMessages(List<PromptMessage> messages) {
        return new PromptResponse(null, messages);
    }

    public PromptResponse(String description, List<PromptMessage> messages) {
        this(description, messages, null);
    }

    public PromptResponse {
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
    }

}
