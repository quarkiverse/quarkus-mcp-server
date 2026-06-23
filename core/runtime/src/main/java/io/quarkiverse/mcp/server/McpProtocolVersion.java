package io.quarkiverse.mcp.server;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents an MCP protocol version.
 * <p>
 * Protocol versions are ISO 8601 dates (YYYY-MM-DD). Versions {@link #FIRST_STATELESS} and later use the stateless
 * (per-request metadata) model. Earlier versions use the stateful (session-based) model.
 * <p>
 * Instances for known/supported versions are available as constants. The {@link #from(String)} factory method
 * returns these constants when possible, otherwise creates a new instance representing an unknown version.
 */
public final class McpProtocolVersion {

    public static final McpProtocolVersion V_2024_11_05 = new McpProtocolVersion("2024-11-05");
    public static final McpProtocolVersion V_2025_03_26 = new McpProtocolVersion("2025-03-26");
    public static final McpProtocolVersion V_2025_06_18 = new McpProtocolVersion("2025-06-18");
    public static final McpProtocolVersion V_2025_11_25 = new McpProtocolVersion("2025-11-25");
    public static final McpProtocolVersion V_2026_07_28 = new McpProtocolVersion("2026-07-28");

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

    private static final Map<String, McpProtocolVersion> BY_VERSION = Stream.of(
            V_2024_11_05, V_2025_03_26, V_2025_06_18, V_2025_11_25, V_2026_07_28)
            .collect(Collectors.toUnmodifiableMap(McpProtocolVersion::version, v -> v));

    private final String version;

    private McpProtocolVersion(String version) {
        this.version = Objects.requireNonNull(version, "version must not be null");
    }

    /**
     * @return the version string in ISO 8601 date format (YYYY-MM-DD)
     */
    public String version() {
        return version;
    }

    /**
     * Uses lexicographic comparison of the version strings, so this method may return {@code true} even for
     * {@linkplain #isKnown() unknown} versions whose date is equal to or later than {@link #FIRST_STATELESS}.
     *
     * @return {@code true} if this version uses the stateless (per-request metadata) model
     */
    public boolean isStateless() {
        return version.compareTo(FIRST_STATELESS.version) >= 0;
    }

    /**
     * @return {@code true} if this instance represents a known/supported protocol version
     */
    public boolean isKnown() {
        return BY_VERSION.containsKey(version);
    }

    /**
     * Uses lexicographic comparison of the version strings, so this method may return {@code true} even for
     * unknown versions whose date is equal to or later than {@link #FIRST_STATELESS}.
     *
     * @return {@code true} if the given version string uses the stateless model
     */
    public static boolean isStateless(String version) {
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        return version.compareTo(FIRST_STATELESS.version) >= 0;
    }

    /**
     * Returns the {@code McpProtocolVersion} for the given version string.
     * <p>
     * If the string matches a known version, the corresponding constant is returned.
     * If the string is non-null but unrecognized, a new instance is created.
     *
     * @param version the version string
     * @return the matching instance, or {@code null} if the input is {@code null}
     */
    public static McpProtocolVersion from(String version) {
        if (version == null) {
            return null;
        }
        McpProtocolVersion known = BY_VERSION.get(version);
        return known != null ? known : new McpProtocolVersion(version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof McpProtocolVersion other)) {
            return false;
        }
        return version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    @Override
    public String toString() {
        return version;
    }

}
