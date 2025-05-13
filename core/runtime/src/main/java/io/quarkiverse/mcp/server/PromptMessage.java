package io.quarkiverse.mcp.server;

/**
 * A message returned as a part of a prompt.
 *
 * @param role the sender or recipient of messages and data in a conversation (must not be {@code null})
 * @param content the message content (must not be {@code null})
 *
 * @see TextContent
 * @see ImageContent
 * @see EmbeddedResource
 * @see AudioContent
 */
public record PromptMessage(Role role, Content content) {

    /**
     *
     * @param textContent the text content (must not be {@code null})
     * @return a new message with the {@code user} role
     */
    public static PromptMessage withUserRole(String textContent) {
        return withUserRole(new TextContent(textContent));
    }

    /**
     *
     * @param content (must not be {@code null})
     * @return a new message with the {@code user} role
     */
    public static PromptMessage withUserRole(Content content) {
        return new PromptMessage(Role.USER, content);
    }

    /**
     *
     * @param textContent the text content (must not be {@code null})
     * @return a new message with the {@code assistant} role
     */
    public static PromptMessage withAssistantRole(String textContent) {
        return withAssistantRole(new TextContent(textContent));
    }

    /**
     *
     * @param content (must not be {@code null})
     * @return a new message with the {@code assistant} role
     */
    public static PromptMessage withAssistantRole(Content content) {
        return new PromptMessage(Role.ASSISTANT, content);
    }

    public PromptMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }
}
