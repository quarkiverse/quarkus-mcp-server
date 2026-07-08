package io.quarkiverse.mcp.server.http.runtime;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.GlobalInputSchemaGenerator;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.http.McpParamHeader;
import io.quarkiverse.mcp.server.runtime.DefaultSchemaGenerator;
import io.quarkiverse.mcp.server.runtime.FeatureKey;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizer;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;
import io.vertx.core.json.JsonObject;

/**
 * HTTP-transport-specific input schema generator that extends the default schema generation to add
 * {@value #X_MCP_HEADER} extension properties for tool parameters annotated with {@link McpParamHeader @McpParamHeader}.
 * <p>
 * This bean has {@link Priority} 100 so it takes precedence over the core {@link DefaultSchemaGenerator}, but is itself
 * a {@link DefaultBean} so that users can still provide a custom implementation.
 * <p>
 * The {@value #X_MCP_HEADER} values are resolved from {@link McpParamHeaderMetadata} which is populated at build time by
 * scanning
 * tool method parameters for {@link McpParamHeader @McpParamHeader} annotations.
 */
@DefaultBean
@Priority(100)
@Singleton
public class HttpInputSchemaGenerator extends DefaultSchemaGenerator {

    static final String X_MCP_HEADER = "x-mcp-header";

    private final McpParamHeaderMetadata headerMetadata;

    public HttpInputSchemaGenerator(@All List<SchemaGeneratorConfigCustomizer> schemaGeneratorConfigCustomizers,
            ObjectMapper objectMapper,
            McpMetadata metadata,
            McpParamHeaderMetadata headerMetadata) {
        super(schemaGeneratorConfigCustomizers, objectMapper, metadata);
        this.headerMetadata = headerMetadata;
    }

    @Override
    public GlobalInputSchemaGenerator.InputSchema generate(ToolInfo tool) {
        InputSchema schema = super.generate(tool);

        if (headerMetadata.isEmpty()) {
            return schema;
        }

        // The @McpParamHeader mapping is per-method, identical across all servers the tool is bound to
        Map<String, String> headers = null;
        for (String serverName : tool.serverNames()) {
            headers = headerMetadata.getHeaders(new FeatureKey(tool.name(), serverName));
            if (headers != null) {
                break;
            }
        }

        if (headers == null || headers.isEmpty()) {
            return schema;
        }

        JsonObject schemaJson = new JsonObject(schema.asJson());
        JsonObject properties = schemaJson.getJsonObject("properties");
        if (properties == null) {
            return schema;
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            JsonObject property = properties.getJsonObject(entry.getKey());
            if (property != null) {
                property.put(X_MCP_HEADER, entry.getValue());
            }
        }

        return new InputSchemaImpl(schemaJson);
    }

}
