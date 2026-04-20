package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;

/**
 * The result of a tool use, provided back to the assistant.
 *
 * @param toolUseId the id of the corresponding tool use (must not be {@code null})
 * @param content the unstructured result content (must not be {@code null})
 * @param structuredContent the optional structured result object (may be {@code null})
 * @param isError whether the tool use resulted in an error (may be {@code null})
 * @param _meta the optional metadata (may be {@code null})
 */
public record ToolResultContent(String toolUseId, List<Content> content, Map<String, Object> structuredContent, Boolean isError,
        Map<MetaKey, Object> _meta) implements Content {

    public ToolResultContent(String toolUseId, List<Content> content) {
        this(toolUseId, content, null, null, null);
    }

    public ToolResultContent {
        if (toolUseId == null) {
            throw new IllegalArgumentException("toolUseId must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        content = List.copyOf(content);
        for (Content item : content) {
            if (item == null) {
                throw new IllegalArgumentException("content must not contain null");
            }
            if (item instanceof ToolUseContent || item instanceof ToolResultContent) {
                throw new IllegalArgumentException("tool result content must contain only plain content blocks");
            }
        }
    }

    @Override
    public Type type() {
        return Type.TOOL_RESULT;
    }

    @Override
    public Annotations annotations() {
        return null;
    }

    @Override
    public ToolResultContent asToolResult() {
        return this;
    }

}
