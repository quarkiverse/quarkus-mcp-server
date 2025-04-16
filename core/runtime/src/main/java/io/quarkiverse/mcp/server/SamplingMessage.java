package io.quarkiverse.mcp.server;

/**
 * @param role the sender or recipient of messages and data in a conversation (must not be {@code null})
 * @param content the message content (must not be {@code null})
 *
 * @see TextContent
 * @see ImageContent
 * @see SamplingRequest
 */
public record SamplingMessage(Role role, Content content) {

    /**
     *
     * @param textContent the text content (must not be {@code null})
     * @return a new message with the {@code user} role
     */
    public static SamplingMessage withUserRole(String textContent) {
        return withUserRole(new TextContent(textContent));
    }

    /**
     *
     * @param content (must not be {@code null})
     * @return a new message with the {@code user} role
     */
    public static SamplingMessage withUserRole(Content content) {
        return new SamplingMessage(Role.USER, content);
    }

    /**
     *
     * @param textContent the text content (must not be {@code null})
     * @return a new message with the {@code assistant} role
     */
    public static SamplingMessage withAssistantRole(String textContent) {
        return withAssistantRole(new TextContent(textContent));
    }

    /**
     *
     * @param content (must not be {@code null})
     * @return a new message with the {@code assistant} role
     */
    public static SamplingMessage withAssistantRole(Content content) {
        return new SamplingMessage(Role.ASSISTANT, content);
    }

    public SamplingMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (content instanceof EmbeddedResource) {
            throw new IllegalArgumentException("content must not be resource");
        }
    }
}
