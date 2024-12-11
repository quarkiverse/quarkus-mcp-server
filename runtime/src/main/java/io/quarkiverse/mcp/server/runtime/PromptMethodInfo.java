package io.quarkiverse.mcp.server.runtime;

import java.util.List;

public record PromptMethodInfo(String name, String description, List<PromptArgument> arguments) {
}