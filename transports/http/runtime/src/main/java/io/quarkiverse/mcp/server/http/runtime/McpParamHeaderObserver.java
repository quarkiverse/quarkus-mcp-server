package io.quarkiverse.mcp.server.http.runtime;

import java.util.HashMap;
import java.util.Map;

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
        for (String argName : properties.fieldNames()) {
            JsonObject prop = properties.getJsonObject(argName);
            if (prop != null && prop.containsKey(HttpInputSchemaGenerator.X_MCP_HEADER)) {
                if (headers == null) {
                    headers = new HashMap<>();
                }
                headers.put(argName, prop.getString(HttpInputSchemaGenerator.X_MCP_HEADER));
            }
        }
        if (headers != null) {
            for (String serverName : tool.serverNames()) {
                headerMetadata.register(new FeatureKey(tool.name(), serverName), headers);
            }
        }
    }

    void onToolRemoved(@Observes ToolRemoved event) {
        ToolInfo tool = event.tool();
        for (String serverName : tool.serverNames()) {
            headerMetadata.remove(new FeatureKey(tool.name(), serverName));
        }
    }

}
