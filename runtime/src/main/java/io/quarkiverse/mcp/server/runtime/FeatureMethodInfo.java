package io.quarkiverse.mcp.server.runtime;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FeatureMethodInfo(String name, String description, @JsonIgnore List<FeatureArgument> arguments) {

    @JsonProperty("arguments")
    public List<FeatureArgument> serializedArguments() {
        if (arguments == null) {
            return List.of();
        }
        return arguments.stream().filter(FeatureArgument::isParam).toList();
    }

}