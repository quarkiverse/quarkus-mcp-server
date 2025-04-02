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
 */
public record PromptMessage(String role, Content content) {

    /**
     *
     * @param content
     * @return a new message with the {@code user} role
     */
    public static PromptMessage withUserRole(Content content) {
        return new PromptMessage("user", content);
    }

    /**
     *
     * @param content
     * @return a new message with the {@code assistant} role
     */
    public static PromptMessage withAssistantRole(Content content) {
        return new PromptMessage("assistant", content);
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
