package io.quarkiverse.mcp.server.http.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.runtime.FeatureKey;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl.ToolAdded;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl.ToolRemoved;
import io.vertx.core.json.JsonObject;

/**
 * Observes {@link ToolAdded} and {@link ToolRemoved} CDI events to extract {@code x-mcp-header}
 * extension properties from the input schema of programmatically registered tools and update
 * {@link McpParamHeaderMetadata} accordingly.
 */
@Singleton
public class McpParamHeaderObserver {

    private final McpParamHeaderMetadata headerMetadata;

    McpParamHeaderObserver(McpParamHeaderMetadata headerMetadata) {
        this.headerMetadata = headerMetadata;
    }

    void onToolAdded(@Observes ToolAdded event) {
        ToolInfo tool = event.tool();
        // Method-backed tools are handled at build time by the deployment processor
        if (tool.isMethod()) {
            return;
        }
        // Serialize and re-parse to avoid ClassCastException when the input schema
        // contains Jackson ObjectNode values from the schema generator
        JsonObject toolJson = new JsonObject(tool.asJson().encode());
        JsonObject inputSchema = toolJson.getJsonObject("inputSchema");
        if (inputSchema == null) {
            return;
        }
        JsonObject properties = inputSchema.getJsonObject("properties");
        if (properties == null) {
            return;
        }
        Map<String, String> headers = null;
        // Track header names (lower-cased) to enforce case-insensitive uniqueness within the tool
        Set<String> headerNamesLower = null;
        for (String argName : properties.fieldNames()) {
            JsonObject prop = properties.getJsonObject(argName);
            if (prop == null || !prop.containsKey(HttpInputSchemaGenerator.X_MCP_HEADER)) {
                continue;
            }
            String headerName = prop.getString(HttpInputSchemaGenerator.X_MCP_HEADER);
            // The same x-mcp-header constraints the deployment processor enforces for method-backed tools
            // (HttpMcpServerProcessor) are applied here for programmatically registered tools. A violation
            // aborts tool registration by propagating out of the ToolAdded observer.
            if (headerName == null || headerName.isEmpty()) {
                throw new IllegalStateException(
                        "x-mcp-header value must not be empty [tool: %s, property: %s]".formatted(tool.name(), argName));
            }
            if (!HttpParamHeaders.isValidHttpToken(headerName)) {
                throw new IllegalStateException(
                        "x-mcp-header value '%s' is not a valid HTTP field-name token [tool: %s, property: %s]"
                                .formatted(headerName, tool.name(), argName));
            }
            if (!isAllowedSchemaType(prop)) {
                throw new IllegalStateException(
                        "x-mcp-header is only allowed on string, integer, or boolean properties [tool: %s, property: %s]"
                                .formatted(tool.name(), argName));
            }
            if (headerNamesLower == null) {
                headerNamesLower = new HashSet<>();
            }
            if (!headerNamesLower.add(headerName.toLowerCase())) {
                throw new IllegalStateException(
                        "Duplicate x-mcp-header value '%s' (case-insensitive) [tool: %s]".formatted(headerName, tool.name()));
            }
            if (headers == null) {
                headers = new HashMap<>();
            }
            headers.put(argName, headerName);
        }
        if (headers != null) {
            for (String serverName : tool.serverNames()) {
                headerMetadata.register(new FeatureKey(tool.name(), serverName), headers);
            }
        }
    }

    private static final Set<String> ALLOWED_SCHEMA_TYPES = Set.of("string", "integer", "boolean");

    private static boolean isAllowedSchemaType(JsonObject prop) {
        Object type = prop.getValue("type");
        return type instanceof String s && ALLOWED_SCHEMA_TYPES.contains(s);
    }

    void onToolRemoved(@Observes ToolRemoved event) {
        ToolInfo tool = event.tool();
        for (String serverName : tool.serverNames()) {
            headerMetadata.remove(new FeatureKey(tool.name(), serverName));
        }
    }

}
