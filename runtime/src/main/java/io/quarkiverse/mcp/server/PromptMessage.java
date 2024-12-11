package io.quarkiverse.mcp.server;

/**
 *
 * @param role The sender or recipient of messages and data in a conversation.
 * @param content
 *
 * @see TextContent
 * @see ImageContent
 * @see ResourceContent
 */
public record PromptMessage(String role, Content content) {

    public static PromptMessage user(Content content) {
        return new PromptMessage("user", content);
    }

}
