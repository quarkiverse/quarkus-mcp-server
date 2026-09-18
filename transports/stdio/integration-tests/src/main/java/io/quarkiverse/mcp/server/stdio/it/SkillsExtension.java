package io.quarkiverse.mcp.server.stdio.it;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpExtensionSetting;
import io.quarkiverse.mcp.server.MetaField.Type;

@McpExtension(id = "io.modelcontextprotocol/skills")
@McpExtensionSetting(name = "directoryRead", type = Type.BOOLEAN, value = "true")
public class SkillsExtension {

    @McpExtensionMethod("skills/get")
    public Skill get(String uri) {
        return new Skill(uri, "Skill for " + uri);
    }

    public record Skill(String uri, String description) {
    }

}
