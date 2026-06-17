package io.quarkiverse.mcp.server;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A key for additional metadata defined in the {@code _meta} part of the message.
 * <p>
 * {@code _meta} keys have two segments: an optional prefix, and a name.
 */
public record MetaKey(String prefix, String name, String key) {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]*[a-zA-Z0-9]");
    private static final Pattern PREFIX_PATTERN = Pattern
            .compile("([a-zA-Z][a-zA-Z0-9-]*[a-zA-Z0-9])+(\\.[a-zA-Z][a-zA-Z0-9-]*[a-zA-Z0-9])*/");

    /**
     * The {@code io.modelcontextprotocol/clientInfo} key.
     */
    public static final MetaKey CLIENT_INFO = new MetaKey("io.modelcontextprotocol/", "clientInfo");

    /**
     * The {@code io.modelcontextprotocol/clientCapabilities} key.
     */
    public static final MetaKey CLIENT_CAPABILITIES = new MetaKey("io.modelcontextprotocol/", "clientCapabilities");

    /**
     * The {@code io.modelcontextprotocol/protocolVersion} key.
     */
    public static final MetaKey PROTOCOL_VERSION = new MetaKey("io.modelcontextprotocol/", "protocolVersion");

    /**
     * The {@code io.modelcontextprotocol/logLevel} key.
     */
    public static final MetaKey LOG_LEVEL = new MetaKey("io.modelcontextprotocol/", "logLevel");

    /**
     * The {@code io.modelcontextprotocol/subscriptionId} key.
     */
    public static final MetaKey SUBSCRIPTION_ID = new MetaKey("io.modelcontextprotocol/", "subscriptionId");

    /**
     * Create a new key with the specified prefix and name.
     *
     * @param prefix the optional prefix (e.g. {@code "io.modelcontextprotocol/"})
     * @param name the key name
     */
    public MetaKey(String prefix, String name) {
        this(prefix, name, prefix == null ? name : prefix + name);
    }

    /**
     * Create a new key from the specified string value, i.e. from {@code foo.bar/myKey}.
     *
     * @param value
     * @return the key
     */
    public static MetaKey from(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("value must not be null or empty");
        }
        int slashIdx = value.indexOf('/');
        if (slashIdx < 0) {
            return new MetaKey(null, value, value);
        }
        return new MetaKey(value.substring(0, slashIdx + 1), value.substring(slashIdx + 1), value);
    }

    /**
     * Create a new key with the specified name but without a prefix.
     *
     * @param name
     * @return the key
     */
    public static MetaKey of(String name) {
        return new MetaKey(null, name);
    }

    /**
     * Create a new key with the specified name and the prefix is built from the supplied labels.
     * <p>
     * Note that {@code modelcontextprotocol} and {@code mcp} labels are reserved for MCP spec.
     *
     * @param name
     * @return the key
     */
    public static MetaKey of(String name, String... prefixLabels) {
        return new MetaKey(Arrays.stream(prefixLabels).collect(Collectors.joining(".")) + "/", name);
    }

    public MetaKey {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (!isValidName(name)) {
            throw new IllegalArgumentException("name %s is not valid".formatted(name));
        }
        if (prefix != null && !isValidPrefix(prefix)) {
            throw new IllegalArgumentException("prefix %s is not valid".formatted(prefix));
        }
    }

    @JsonValue
    @Override
    public String toString() {
        return key;
    }

    public static boolean isValidName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return NAME_PATTERN.matcher(value).matches();
    }

    public static boolean isValidPrefix(String value) {
        if (value == null
                || value.isBlank()
                || !value.endsWith("/")) {
            return false;
        }
        return PREFIX_PATTERN.matcher(value).matches();
    }
}
