package io.quarkiverse.mcp.server;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @see TextContent
 * @see ImageContent
 * @see ToolUseContent
 * @see ToolResultContent
 * @see SamplingRequest
 */
public final class SamplingMessage {

    private final Role role;
    private final List<Content> contents;

    public SamplingMessage(Role role, Content content) {
        this(role, List.of(content));
    }

    public SamplingMessage(Role role, List<? extends Content> content) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        this.role = role;
        this.contents = List.copyOf(content);
        for (Content item : contents) {
            validateContent(role, item);
        }
    }

    public static SamplingMessage withUserRole(String textContent) {
        return withUserRole(new TextContent(textContent));
    }

    public static SamplingMessage withUserRole(Content content) {
        return new SamplingMessage(Role.USER, content);
    }

    public static SamplingMessage withUserRole(List<? extends Content> content) {
        return new SamplingMessage(Role.USER, content);
    }

    public static SamplingMessage withAssistantRole(String textContent) {
        return withAssistantRole(new TextContent(textContent));
    }

    public static SamplingMessage withAssistantRole(Content content) {
        return new SamplingMessage(Role.ASSISTANT, content);
    }

    public static SamplingMessage withAssistantRole(List<? extends Content> content) {
        return new SamplingMessage(Role.ASSISTANT, content);
    }

    @JsonProperty
    public Role role() {
        return role;
    }

    /**
     * Returns the first content block.
     *
     * @throws IllegalStateException if this message contains multiple content blocks
     */
    public Content content() {
        if (contents.size() != 1) {
            throw new IllegalStateException("This message contains multiple content blocks; use contents() instead");
        }
        return contents.get(0);
    }

    public List<Content> contents() {
        return contents;
    }

    @JsonProperty("content")
    public Object jsonContent() {
        return contents.size() == 1 ? contents.get(0) : contents;
    }

    private static void validateContent(Role role, Content content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not contain null");
        }
        if (content instanceof EmbeddedResource || content instanceof ResourceLink) {
            throw new IllegalArgumentException("content must not be resource or resource link");
        }
        if (content instanceof ToolUseContent && role != Role.ASSISTANT) {
            throw new IllegalArgumentException("tool use content requires the assistant role");
        }
        if (content instanceof ToolResultContent && role != Role.USER) {
            throw new IllegalArgumentException("tool result content requires the user role");
        }
    }
}
