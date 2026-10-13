package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;

/**
 * Shared policy for admission, discovery and legacy transport availability.
 */
public record SupportedProtocolVersions(List<McpProtocolVersion> versions) {

    public SupportedProtocolVersions {
        versions = List.copyOf(versions);
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("Supported protocol versions must not be empty");
        }
    }

    public static SupportedProtocolVersions from(McpServerRuntimeConfig config) {
        List<String> configured = config.supportedProtocolVersions().orElse(McpProtocolVersion.SUPPORTED_VERSIONS);
        return new SupportedProtocolVersions(configured.stream().map(McpProtocolVersion::from).toList());
    }

    public boolean supports(String version) {
        return supports(McpProtocolVersion.from(version));
    }

    public boolean supports(McpProtocolVersion version) {
        return version != null && versions.contains(version);
    }

    public boolean supportsStateful() {
        for (McpProtocolVersion version : versions) {
            if (!version.isStateless()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the version strings, in configured order, for inclusion in wire responses
     */
    public List<String> versionStrings() {
        List<String> ret = new ArrayList<>(versions.size());
        for (McpProtocolVersion version : versions) {
            ret.add(version.version());
        }
        return ret;
    }
}
