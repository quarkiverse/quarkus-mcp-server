package io.quarkiverse.mcp.server.deployment;

import java.util.Map;
import java.util.Set;

import org.jboss.jandex.ClassInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A discovered {@link io.quarkiverse.mcp.server.McpExtension}.
 */
public final class ExtensionBuildItem extends MultiBuildItem {

    private final String id;
    private final Set<String> servers;
    // setting name -> JSON-encoded value (as produced for _meta fields)
    private final Map<String, String> settings;
    private final ClassInfo declaringClass;

    public ExtensionBuildItem(String id, Set<String> servers, Map<String, String> settings, ClassInfo declaringClass) {
        this.id = id;
        this.servers = servers;
        this.settings = settings;
        this.declaringClass = declaringClass;
    }

    public String getId() {
        return id;
    }

    public Set<String> getServers() {
        return servers;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    public ClassInfo getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public String toString() {
        return "Extension [id=" + id + ", class=" + declaringClass.name() + ", servers=" + servers + "]";
    }

}
