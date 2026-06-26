package io.quarkiverse.mcp.server.test.mcpjava;

import java.util.Map;
import java.util.stream.Collectors;

import org.mcpjava.server.Icon;
import org.mcpjava.server.ImplementationInfo;
import org.mcpjava.server.McpRequest;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

public class McpJavaMcpRequestFeatures {

    @Tool(description = "Returns request info")
    String requestInfo(McpRequest request) {
        String sessionId = request.sessionId().orElse("none");
        String protocolVersion = request.protocolVersion();
        ImplementationInfo clientInfo = request.clientInfo();
        return "session:" + sessionId
                + ",protocol:" + protocolVersion
                + ",client:" + clientInfo.name() + "/" + clientInfo.version()
                + ",title:" + clientInfo.title();
    }

    @Tool(description = "Returns request id")
    String requestId(McpRequest request) {
        return "id:" + request.id();
    }

    @Tool(description = "Returns request metadata")
    String requestMetadata(McpRequest request, @ToolArg(description = "The key") String key) {
        Map<String, Object> metadata = request.metadata();
        Object value = metadata.get(key);
        return "meta:" + (value != null ? value.toString() : "null");
    }

    @Tool(description = "Returns client capabilities")
    String clientCapabilities(McpRequest request) {
        Map<String, Object> caps = request.rawClientCapabilities();
        return caps.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.joining(",", "caps:[", "]"));
    }

    @Tool(description = "Returns client info details")
    String clientInfoDetails(McpRequest request) {
        ImplementationInfo info = request.clientInfo();
        StringBuilder sb = new StringBuilder();
        sb.append("name:").append(info.name());
        sb.append(",version:").append(info.version());
        sb.append(",title:").append(info.title());
        sb.append(",description:").append(info.description().orElse("none"));
        sb.append(",websiteUrl:").append(info.websiteUrl().orElse("none"));
        sb.append(",icons:").append(info.icons().size());
        if (!info.icons().isEmpty()) {
            Icon icon = info.icons().get(0);
            sb.append(",icon0.src:").append(icon.src());
            sb.append(",icon0.mimeType:").append(icon.mimeType().orElse("none"));
            sb.append(",icon0.sizes:").append(icon.sizes());
            sb.append(",icon0.theme:").append(icon.theme().map(Enum::name).orElse("none"));
        }
        return sb.toString();
    }
}
