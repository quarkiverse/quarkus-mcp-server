package io.quarkiverse.mcp.server.runtime;

import java.util.List;

public record FeatureMethodInfo(String name, String description, List<FeatureArgument> arguments) {
}