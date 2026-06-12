package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * Represents the supported MCP protocol versions.
 * <p>
 * Protocol versions are ISO 8601 dates (YYYY-MM-DD). Versions {@link #FIRST_STATELESS} and later use the stateless
 * (per-request metadata) model. Earlier versions use the stateful (session-based) model.
 */
public enum McpProtocolVersion {

    V_2024_11_05("2024-11-05"),
    V_2025_03_26("2025-03-26"),
    V_2025_06_18("2025-06-18"),
    V_2025_11_25("2025-11-25"),
    V_2026_07_28("2026-07-28"),
    ;

    private final String version;

    private McpProtocolVersion(String version) {
        this.version = version;
    }

    /**
     * @return the version string in ISO 8601 date format (YYYY-MM-DD)
     */
    public String version() {
        return version;
    }

    /**
     * @return {@code true} if this version uses the stateless (per-request metadata) model
     */
    public boolean isStateless() {
        return version.compareTo(FIRST_STATELESS.version) >= 0;
    }

    /**
     * The first protocol version that uses the stateless model.
     */
    public static final McpProtocolVersion FIRST_STATELESS = V_2026_07_28;

    /**
     * The latest protocol version that uses the stateful (session-based) model.
     */
    public static final McpProtocolVersion LATEST_STATEFUL = V_2025_11_25;

    /**
     * The protocol version to assume when no {@code MCP-Protocol-Version} header is present.
     *
     * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#backwards-compatibility">MCP
     *      spec</a>
     */
    public static final McpProtocolVersion DEFAULT_ASSUMED = V_2025_03_26;

    /**
     * All supported protocol versions.
     */
    public static final List<String> SUPPORTED_VERSIONS = List.of(
            V_2026_07_28.version,
            V_2025_11_25.version,
            V_2025_06_18.version,
            V_2025_03_26.version,
            V_2024_11_05.version);

    /**
     * @return {@code true} if the given version string uses the stateless model
     */
    public static boolean isStateless(String version) {
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        return version.compareTo(FIRST_STATELESS.version) >= 0;
    }

    /**
     * @param version the version string
     * @return the matching enum constant, or {@code null} if no match
     */
    public static McpProtocolVersion from(String version) {
        if (version != null) {
            for (McpProtocolVersion v : values()) {
                if (v.version.equals(version)) {
                    return v;
                }
            }
        }
        return null;
    }

}
