package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * A request from the assistant to call a tool.
 *
 * @param id a unique identifier for this tool use (must not be {@code null})
 * @param name the name of the tool to call (must not be {@code null})
 * @param input the arguments to pass to the tool (must not be {@code null})
 * @param _meta the optional metadata (may be {@code null})
 */
public record ToolUseContent(String id, String name, Map<String, Object> input, Map<MetaKey, Object> _meta) implements Content {

    public ToolUseContent {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
    }

    @Override
    public Type type() {
        return Type.TOOL_USE;
    }

    @Override
    public Annotations annotations() {
        return null;
    }

    @Override
    public ToolUseContent asToolUse() {
        return this;
    }

}
