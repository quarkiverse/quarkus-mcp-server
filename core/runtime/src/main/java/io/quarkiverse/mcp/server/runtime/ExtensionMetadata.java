package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Set;

/**
 * Build-time metadata for an {@link io.quarkiverse.mcp.server.McpExtension}.
 *
 * @param id the extension identifier, used as the key inside {@code capabilities.extensions}
 * @param serverNames the set of servers this extension is bound to
 * @param settings the extension's settings object; keys are setting names and values are the corresponding JSON-encoded
 *        values (as produced for {@code _meta} fields), to be decoded with {@link io.vertx.core.json.Json#decodeValue(String)}
 */
public record ExtensionMetadata(String id, Set<String> serverNames, Map<String, String> settings) {
}
